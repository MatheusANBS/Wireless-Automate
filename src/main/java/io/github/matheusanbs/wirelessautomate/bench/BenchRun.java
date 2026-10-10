package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkStats;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;

/**
 * Uma tarefa do benchmark: um cenário, um tamanho e algumas repetições. Avança um passo por tick,
 * chamada no fim do tick ({@link BenchRunner}) com o tempo que o tick levou e o que o mod gastou.
 *
 * <p>Cada repetição: monta a cena com os roteadores sem rede → espera todos se registrarem →
 * mede a linha de base (MSPT sem a rede) → põe a rede → aquece → mede → desmonta. As ações (montar,
 * reencher, desmontar) rodam depois da medição do tick, então não entram nos números.
 *
 * <p>Benchmark comparativo (docs/benchmark-logistics-network.md): a vazão e as origens que moveram vêm da
 * contagem neutra da cena ({@link BenchScene#delivered}, {@link BenchScene#extractedBySource}), igual
 * para os dois mods. O custo é o MSPT com rede menos o da linha de base, mais a CPU das threads do
 * planejamento assíncrono do Logistics Network ({@link #WORKER_PREFIX}), que não aparece no MSPT. As
 * colunas do motor (mod, visitas, orçamento, remontagens) são só do Wireless Automate.
 */
final class BenchRun {
    /** {@code jitWarmup}: só aquece o JIT antes das medições; o resultado não entra no resumo. */
    record Job(BenchScenario scenario, int nodes, int reps, BenchStorage storage, BenchTransport transport,
            boolean jitWarmup) {
        Job(BenchScenario scenario, int nodes, int reps, BenchStorage storage) {
            this(scenario, nodes, reps, storage, BenchTransport.WA, false);
        }

        Job(BenchScenario scenario, int nodes, int reps, BenchStorage storage, BenchTransport transport) {
            this(scenario, nodes, reps, storage, transport, false);
        }

        String label() {
            return (jitWarmup ? "aquecimento do JIT (descartado): " : "") + scenario.id + " n=" + nodes + " " + storage.id
                    + " " + transport.id;
        }
    }

    /** Threads do planejamento assíncrono do Logistics Network ({@code AsyncTransferRuntime}). */
    static final String WORKER_PREFIX = "LogisticsNetworks-Worker";

    /**
     * Resultado de uma repetição. Tempos em ns, vazões por segundo de jogo. Visitas, orçamento
     * esgotado e remontagens vêm dos contadores cumulativos do {@link NetworkManager}.
     */
    record Rep(int rep, double baseMsptMean, long baseMsptP99, double msptMean, long msptP50, long msptP99, long msptMax,
            double modMean, long modP50, long modP99, long modMax, long limitNanos, double exhaustedPct,
            double visitsPerTick, double nanosPerVisit, double nanosPerDelivery, double itemsPerSecond,
            double fluidPerSecond, double energyPerSecond, double opsPerSecond, int sourcesMoved, int sources,
            int sourcesSleeping, int sourcesTotal, int destinationsSleeping, int destinationsTotal,
            long attachModNanos, long rebuilds, double rebuildMeanNanos, long afterTouchMaxNanos, int slots,
            double rebuildsPerSecond, double tabletMeanNanos, long tabletMaxNanos,
            boolean engine, double costNanos, double workerNanos, double neutralItems, double neutralFluid,
            double neutralEnergy, int neutralMoved, int neutralSources) {
        /** Unidades por segundo da contagem neutra (itens, mB e FE somados). */
        double neutralUnits() {
            return neutralItems + neutralFluid + neutralEnergy;
        }

        /** Custo total por tick: MSPT com rede menos a base, mais a CPU das threads do assíncrono. */
        double totalCostNanos() {
            return costNanos + workerNanos;
        }

        /**
         * ns de custo por item movido (é o mesmo número que µs por mil itens). Só nos cenários só de
         * itens: somar itens, mB e FE não dá uma unidade comparável.
         */
        double nanosPerUnit() {
            return neutralItems <= 0 || neutralFluid > 0 || neutralEnergy > 0 ? Double.NaN
                    : totalCostNanos() * 20 / neutralItems;
        }
    }

    private enum Phase { SETTLE, BASELINE, ATTACHED, WARMUP, MEASURE, PAUSE }

    static final int SETTLE_TICKS = 20;
    static final int SETTLE_TIMEOUT = 200;
    static final int PAUSE_TICKS = 20;

    final Job job;
    private final int baselineTicks;
    private final int measureTicks;
    private final int warmupTicks;
    private final Consumer<String> out;
    final List<Rep> reps = new ArrayList<>();

    private BenchScene scene;
    private int rep;
    private Phase phase;
    private int phaseTick;
    private int settled;
    private final Samples baseMspt = new Samples();
    private final Samples mspt = new Samples();
    private final Samples mod = new Samples();
    /** Tempo do mod no tick logo depois de {@link BenchScene#touch} (cenário de remontagem). */
    private final Samples afterTouch = new Samples();
    /** Sincronização do Tablet aberto em cada tick medido ({@link BenchScenario#TABLET}), fora do laço do mod. */
    private final Samples tablet = new Samples();
    private long tabletNanos;
    private long limitNanos;
    private long attachModNanos;
    private long visitsStart;
    private long exhaustedStart;
    private long rebuildsStart;
    private long rebuildNanosStart;
    private long itemsStart;
    private long fluidStart;
    private long energyStart;
    private long[] movedStart;
    private long neutralItemsStart;
    private long neutralFluidStart;
    private long neutralEnergyStart;
    private long[] extractedStart;
    private long workerStart;
    private long opsSum;
    private int opsSeconds;
    private boolean done;

    BenchRun(Job job, int baselineTicks, int warmupTicks, int measureTicks, Consumer<String> out) {
        this.job = job;
        this.baselineTicks = baselineTicks;
        this.warmupTicks = warmupTicks;
        this.measureTicks = measureTicks;
        this.out = out;
    }

    boolean done() {
        return done;
    }

    String status() {
        return job.label() + ", repetição " + (rep + 1) + "/" + job.reps() + ", fase " + phase + " (" + phaseTick + ")";
    }

    /** Monta a primeira repetição. */
    void start(MinecraftServer server) {
        out.accept("Benchmark " + job.label() + ": " + job.scenario().description);
        startRep(server);
    }

    private void startRep(MinecraftServer server) {
        ServerLevel level = server.overworld();
        scene = new BenchScene(level, job.scenario(), job.storage(), job.transport(), job.nodes());
        scene.build();
        baseMspt.clear();
        mspt.clear();
        mod.clear();
        afterTouch.clear();
        tablet.clear();
        tabletNanos = 0;
        settled = 0;
        enter(Phase.SETTLE);
    }

    private void enter(Phase next) {
        phase = next;
        phaseTick = 0;
    }

    /**
     * Começo de um tick, dentro do tempo medido: o que roda no tick do jogo e não é do laço do mod.
     * Hoje, só o Tablet aberto, que o {@code ServerPlayer} sincroniza a cada tick.
     */
    void preTick(MinecraftServer server) {
        tabletNanos = !done && (phase == Phase.WARMUP || phase == Phase.MEASURE) ? scene.syncTablet() : 0;
    }

    /** Fim de um tick: registra as amostras da fase em que o tick rodou e prepara o próximo. */
    void tick(MinecraftServer server, long tickNanos, long modNanos) {
        if (done) {
            return;
        }
        phaseTick++;
        NetworkManager manager = NetworkManager.get();
        switch (phase) {
            case SETTLE -> {
                if (scene.registered()) {
                    settled++;
                }
                if (settled >= SETTLE_TICKS || phaseTick >= SETTLE_TIMEOUT) {
                    if (!scene.registered()) {
                        out.accept("  aviso: nem todos os roteadores se registraram em " + SETTLE_TIMEOUT + " ticks");
                    }
                    enter(Phase.BASELINE);
                }
            }
            case BASELINE -> {
                baseMspt.add(tickNanos);
                if (phaseTick >= baselineTicks) {
                    scene.attach();
                    enter(Phase.ATTACHED);
                }
            }
            case ATTACHED -> {
                // O tick logo depois de pôr a rede: a primeira montagem das rotas.
                attachModNanos = modNanos;
                if (job.scenario() == BenchScenario.TABLET) {
                    scene.openTablet();
                }
                enter(Phase.WARMUP);
                scene.toggleClocks();
                maybeRecycle();
            }
            case WARMUP -> {
                if (phaseTick >= warmupTicks) {
                    beginMeasure();
                    enter(Phase.MEASURE);
                }
                scene.toggleClocks();
                maybeRecycle();
            }
            case MEASURE -> {
                mspt.add(tickNanos);
                mod.add(modNanos);
                tablet.add(tabletNanos);
                limitNanos = manager.budget().limitNanos();
                if (server.getTickCount() % 20 == 0) {
                    // O gerenciador fecha a janela de operações neste mesmo tick.
                    NetworkStats stats = networkStats(server);
                    if (stats != null) {
                        opsSum += stats.opsLastSecond();
                        opsSeconds++;
                    }
                }
                if (job.scenario() == BenchScenario.REBUILD) {
                    if (phaseTick % 20 == 11) {
                        afterTouch.add(modNanos);
                    } else if (phaseTick % 20 == 10) {
                        scene.touch();
                    }
                }
                if (phaseTick >= measureTicks) {
                    finishRep(server);
                    scene.teardown();
                    enter(Phase.PAUSE);
                } else {
                    scene.toggleClocks();
                    maybeRecycle();
                }
            }
            case PAUSE -> {
                if (phaseTick >= PAUSE_TICKS) {
                    rep++;
                    if (rep >= job.reps()) {
                        summarize();
                        done = true;
                    } else {
                        startRep(server);
                    }
                }
            }
        }
    }

    private void maybeRecycle() {
        int period = job.scenario().recyclePeriod;
        if (period > 0 && phaseTick % period == 0) {
            scene.recycle();
        }
    }

    private void beginMeasure() {
        NetworkManager engine = NetworkManager.get();
        visitsStart = engine.visitCount();
        exhaustedStart = engine.exhaustedTicks();
        rebuildsStart = engine.rebuildCount();
        rebuildNanosStart = engine.rebuildNanos();
        itemsStart = scene.moved(ResourceType.ITEM);
        fluidStart = scene.moved(ResourceType.FLUID);
        energyStart = scene.moved(ResourceType.ENERGY);
        movedStart = scene.movedBySource();
        neutralItemsStart = scene.delivered(ResourceType.ITEM);
        neutralFluidStart = scene.delivered(ResourceType.FLUID);
        neutralEnergyStart = scene.delivered(ResourceType.ENERGY);
        extractedStart = scene.extractedBySource();
        workerStart = workerCpuNanos();
        opsSum = 0;
        opsSeconds = 0;
    }

    private NetworkStats networkStats(MinecraftServer server) {
        UUID id = scene.network();
        for (NetworkStats stats : NetworkManager.get().stats(server)) {
            if (stats.id().equals(id)) {
                return stats;
            }
        }
        return null;
    }

    private void finishRep(MinecraftServer server) {
        int ticks = Math.max(1, mspt.size());
        double seconds = ticks / 20.0;
        NetworkManager engine = NetworkManager.get();
        long visits = engine.visitCount() - visitsStart;
        long rebuilds = engine.rebuildCount() - rebuildsStart;
        long rebuildNanos = engine.rebuildNanos() - rebuildNanosStart;
        double exhausted = 100.0 * (engine.exhaustedTicks() - exhaustedStart) / ticks;
        long[] movedEnd = scene.movedBySource();
        int sourcesMoved = 0;
        for (int i = 0; i < movedEnd.length; i++) {
            if (scene.nodes.get(i).source() && movedEnd[i] > movedStart[i]) {
                sourcesMoved++;
            }
        }
        long[] extractedEnd = scene.extractedBySource();
        int neutralMoved = 0;
        int neutralSources = 0;
        for (int i = 0; i < extractedEnd.length; i++) {
            if (extractedEnd[i] >= 0 && extractedStart[i] >= 0) {
                neutralSources++;
                if (extractedEnd[i] > extractedStart[i]) {
                    neutralMoved++;
                }
            }
        }
        boolean waEngine = !job.transport().logisticsNetwork();
        double modTotal = mod.mean() * mod.size();
        double opsPerSecond = opsSeconds == 0 ? 0 : (double) opsSum / opsSeconds;
        double deliveries = opsPerSecond * seconds;
        NetworkStats stats = networkStats(server);
        Rep result = new Rep(rep + 1, baseMspt.mean(), baseMspt.percentile(99), mspt.mean(), mspt.percentile(50),
                mspt.percentile(99), mspt.max(), mod.mean(), mod.percentile(50), mod.percentile(99), mod.max(),
                limitNanos, exhausted,
                visits < 0 ? Double.NaN : (double) visits / ticks,
                visits <= 0 ? Double.NaN : modTotal / visits,
                deliveries <= 0 ? Double.NaN : modTotal / deliveries,
                (scene.moved(ResourceType.ITEM) - itemsStart) / seconds,
                (scene.moved(ResourceType.FLUID) - fluidStart) / seconds,
                (scene.moved(ResourceType.ENERGY) - energyStart) / seconds,
                opsPerSecond,
                sourcesMoved, scene.sourceCount(),
                stats == null ? 0 : stats.sourcesSleeping(),
                stats == null ? 0 : stats.sourcesSleeping() + stats.sourcesAwake(),
                stats == null ? 0 : stats.destinationsSleeping(),
                stats == null ? 0 : stats.destinationsSleeping() + stats.destinationsAwake(),
                attachModNanos, rebuilds, rebuilds <= 0 ? Double.NaN : (double) rebuildNanos / rebuilds,
                afterTouch.max(), scene.slotsPerMachine(), rebuilds / seconds,
                job.scenario() == BenchScenario.TABLET ? tablet.mean() : Double.NaN, tablet.max(),
                waEngine, mspt.mean() - baseMspt.mean(), (double) (workerCpuNanos() - workerStart) / ticks,
                (scene.delivered(ResourceType.ITEM) - neutralItemsStart) / seconds,
                (scene.delivered(ResourceType.FLUID) - neutralFluidStart) / seconds,
                (scene.delivered(ResourceType.ENERGY) - neutralEnergyStart) / seconds,
                neutralMoved, neutralSources);
        reps.add(result);
        out.accept(repLine(job, result));
        if (ModList.get().isLoaded("spark")) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "spark tps");
        }
    }

    /** Uma linha por repetição, legível no console e no relatório. */
    static String repLine(Job job, Rep r) {
        String neutral = String.format(Locale.ROOT,
                "  rep %d [%s]: MSPT base %.3f | com rede %.3f (p99 %.3f, máx %.3f) ms | custo %.4f ms/tick"
                        + " + threads %.4f | contagem neutra %.0f itens/s, %.0f mB/s, %.0f FE/s | %s ns/item"
                        + " | origens que moveram %s",
                r.rep(), job.transport().id, ms(r.baseMsptMean()), ms(r.msptMean()), ms(r.msptP99()), ms(r.msptMax()),
                ms(r.costNanos()), ms(r.workerNanos()), r.neutralItems(), r.neutralFluid(), r.neutralEnergy(),
                num(r.nanosPerUnit(), 1),
                r.neutralSources() == 0 ? "—" : r.neutralMoved() + "/" + r.neutralSources());
        return r.engine() ? neutral + "\n" + engineLine(r) : neutral;
    }

    /** Detalhes do motor do Wireless Automate (contadores do {@link NetworkManager}). */
    private static String engineLine(Rep r) {
        return String.format(Locale.ROOT,
                "    motor do WA, rep %d: MSPT base %.3f (p99 %.3f) | com rede %.3f (p50 %.3f, p99 %.3f, máx %.3f) ms"
                        + " | mod %.4f (p50 %.4f, p99 %.4f, máx %.4f; teto %.3f) ms/tick, orçamento esgotado em %.1f%% dos ticks"
                        + " | %s visitas/tick, %s µs/visita, %s µs/entrega, %.1f entregas/s"
                        + " | %.0f itens/s, %.0f mB/s, %.0f FE/s | origens que moveram %d/%d"
                        + " | dormindo: origens %d/%d, destinos %d/%d"
                        + " | 1ª montagem %.3f ms, %s remontagens (%.1f/s, %s ms cada; tick depois da mudança: máx %.3f ms)"
                        + " | %d slots%s",
                r.rep(), ms(r.baseMsptMean()), ms(r.baseMsptP99()), ms(r.msptMean()), ms(r.msptP50()), ms(r.msptP99()),
                ms(r.msptMax()), ms(r.modMean()), ms(r.modP50()), ms(r.modP99()), ms(r.modMax()), ms(r.limitNanos()),
                r.exhaustedPct(), num(r.visitsPerTick(), 1), num(r.nanosPerVisit() / 1000.0, 2), num(r.nanosPerDelivery() / 1000.0, 2),
                r.opsPerSecond(),
                r.itemsPerSecond(), r.fluidPerSecond(), r.energyPerSecond(), r.sourcesMoved(), r.sources(),
                r.sourcesSleeping(), r.sourcesTotal(), r.destinationsSleeping(), r.destinationsTotal(),
                ms(r.attachModNanos()), r.rebuilds() < 0 ? "—" : Long.toString(r.rebuilds()), r.rebuildsPerSecond(),
                num(ms(r.rebuildMeanNanos()), 3), ms(r.afterTouchMaxNanos()), r.slots(),
                Double.isNaN(r.tabletMeanNanos()) ? "" : String.format(Locale.ROOT, " | Tablet aberto %.4f ms/tick (máx %.3f)",
                        ms(r.tabletMeanNanos()), ms(r.tabletMaxNanos())));
    }

    private void summarize() {
        out.accept(summaryLine(job, reps));
    }

    /**
     * Linha da tabela de resumo (Markdown): média ± desvio padrão entre as repetições. As colunas até
     * "origens que moveram" são neutras (valem para os dois mods); as seguintes são do motor do Wireless
     * Automate e ficam "—" no Logistics Network.
     */
    static String summaryLine(Job job, List<Rep> reps) {
        boolean engine = !job.transport().logisticsNetwork();
        return String.format(Locale.ROOT, "| %s | %s | %s | %d | %d | %s | %s | %s | %s | %s | %s | %s"
                        + " | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |",
                job.scenario().id, job.transport().id, job.storage().id, job.nodes(), reps.size(),
                pm(reps, r -> ms(r.baseMsptMean()), 3),
                pm(reps, r -> ms(r.msptMean()), 3),
                pm(reps, r -> ms(r.costNanos()), 4),
                pm(reps, r -> ms(r.workerNanos()), 4),
                pm(reps, Rep::neutralUnits, 0),
                pm(reps, Rep::nanosPerUnit, 1),
                pm(reps, r -> r.neutralSources() == 0 ? Double.NaN : 100.0 * r.neutralMoved() / r.neutralSources(), 0),
                engine ? pm(reps, r -> ms(r.modMean()), 4) : "—",
                engine ? pm(reps, r -> ms(r.modP99()), 4) : "—",
                engine ? pm(reps, r -> ms(r.modMax()), 3) : "—",
                engine ? pm(reps, Rep::exhaustedPct, 1) : "—",
                engine ? pm(reps, r -> r.nanosPerVisit() / 1000.0, 2) : "—",
                engine ? pm(reps, r -> r.nanosPerDelivery() / 1000.0, 2) : "—",
                engine ? pm(reps, r -> ms(r.attachModNanos()), 2) : "—",
                engine ? pm(reps, Rep::visitsPerTick, 1) : "—",
                engine ? pm(reps, Rep::rebuildsPerSecond, 1) : "—",
                engine ? pm(reps, r -> ms(r.tabletMeanNanos()), 4) : "—");
    }

    static final String SUMMARY_HEADER = "| cenário | transporte | armaz. | n | reps | MSPT base (ms) | MSPT com rede (ms)"
            + " | custo (ms/tick) | threads (ms/tick) | unidades/s (neutro) | ns/item | origens que moveram (%)"
            + " | WA: mod média (ms/tick) | mod p99 | mod máx | ticks c/ orçamento esgotado (%) | µs/visita | µs/entrega"
            + " | 1ª montagem (ms) | visitas/tick | remontagens/s | Tablet (ms/tick) |"
            + "\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|";

    /**
     * CPU (ns) somada das threads do planejamento assíncrono do Logistics Network que existem agora; 0
     * sem elas. O pool dele é fixo, então a diferença entre duas leituras é o trabalho entre elas.
     */
    static long workerCpuNanos() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!bean.isThreadCpuTimeSupported()) {
            return 0;
        }
        long total = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().startsWith(WORKER_PREFIX)) {
                long cpu = bean.getThreadCpuTime(thread.getId());
                if (cpu > 0) {
                    total += cpu;
                }
            }
        }
        return total;
    }

    private static String pm(List<Rep> reps, java.util.function.ToDoubleFunction<Rep> field, int decimals) {
        double[] values = reps.stream().mapToDouble(field).toArray();
        for (double value : values) {
            if (Double.isNaN(value)) {
                return "—";
            }
        }
        double[] md = Samples.meanAndDeviation(values);
        String format = "%." + decimals + "f";
        return String.format(Locale.ROOT, format + " ± " + format, md[0], md[1]);
    }

    /** Número com casas fixas, ou "—" quando não foi medido ({@code NaN}). */
    private static String num(double value, int decimals) {
        return Double.isNaN(value) ? "—" : String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static double ms(double nanos) {
        return nanos / 1_000_000.0;
    }

    /** Interrompe: desmonta a cena, se houver. */
    void abort() {
        if (scene != null && phase != Phase.PAUSE) {
            scene.teardown();
        }
        done = true;
    }
}
