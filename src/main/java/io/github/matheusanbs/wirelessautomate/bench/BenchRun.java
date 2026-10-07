package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkStats;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

/**
 * Uma tarefa do benchmark: um cenário, um tamanho e algumas repetições. Avança um passo por tick,
 * chamada no fim do tick ({@link BenchRunner}) com o tempo que o tick levou e o que o mod gastou.
 *
 * <p>Cada repetição: monta a cena com os roteadores sem rede → espera todos se registrarem →
 * mede a linha de base (MSPT sem a rede) → põe a rede → aquece → mede → desmonta. As ações (montar,
 * reencher, desmontar) rodam depois da medição do tick, então não entram nos números.
 */
final class BenchRun {
    /** {@code jitWarmup}: só aquece o JIT antes das medições; o resultado não entra no resumo. */
    record Job(BenchScenario scenario, int nodes, int reps, BenchStorage storage, boolean jitWarmup) {
        Job(BenchScenario scenario, int nodes, int reps, BenchStorage storage) {
            this(scenario, nodes, reps, storage, false);
        }

        String label() {
            return (jitWarmup ? "aquecimento do JIT (descartado): " : "") + scenario.id + " n=" + nodes + " " + storage.id;
        }
    }

    /**
     * Resultado de uma repetição. Tempos em ns, vazões por segundo de jogo. Visitas, orçamento
     * esgotado e remontagens vêm dos contadores cumulativos do {@link NetworkManager}.
     */
    record Rep(int rep, double baseMsptMean, long baseMsptP99, double msptMean, long msptP50, long msptP99, long msptMax,
            double modMean, long modP50, long modP99, long modMax, long limitNanos, double exhaustedPct,
            double visitsPerTick, double nanosPerVisit, double nanosPerDelivery, double itemsPerSecond,
            double fluidPerSecond, double energyPerSecond, double opsPerSecond, int sourcesMoved, int sources,
            int sourcesSleeping, int sourcesTotal, int destinationsSleeping, int destinationsTotal,
            long attachModNanos, long rebuilds, double rebuildMeanNanos, long afterTouchMaxNanos, int slots) {
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
        scene = new BenchScene(level, job.scenario(), job.storage(), job.nodes());
        scene.build();
        baseMspt.clear();
        mspt.clear();
        mod.clear();
        afterTouch.clear();
        settled = 0;
        enter(Phase.SETTLE);
    }

    private void enter(Phase next) {
        phase = next;
        phaseTick = 0;
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
                enter(Phase.WARMUP);
                maybeRecycle();
            }
            case WARMUP -> {
                if (phaseTick >= warmupTicks) {
                    beginMeasure();
                    enter(Phase.MEASURE);
                }
                maybeRecycle();
            }
            case MEASURE -> {
                mspt.add(tickNanos);
                mod.add(modNanos);
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
                afterTouch.max(), scene.slotsPerMachine());
        reps.add(result);
        out.accept(repLine(job, result));
        if (ModList.get().isLoaded("spark")) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "spark tps");
        }
    }

    /** Uma linha por repetição, legível no console e no relatório. */
    static String repLine(Job job, Rep r) {
        return String.format(Locale.ROOT,
                "  rep %d: MSPT base %.3f (p99 %.3f) | com rede %.3f (p50 %.3f, p99 %.3f, máx %.3f) ms"
                        + " | mod %.4f (p50 %.4f, p99 %.4f, máx %.4f; teto %.3f) ms/tick, orçamento esgotado em %.1f%% dos ticks"
                        + " | %s visitas/tick, %s µs/visita, %s µs/entrega, %.1f entregas/s"
                        + " | %.0f itens/s, %.0f mB/s, %.0f FE/s | origens que moveram %d/%d"
                        + " | dormindo: origens %d/%d, destinos %d/%d"
                        + " | 1ª montagem %.3f ms, %s remontagens (%s ms cada; tick depois da mudança: máx %.3f ms) | %d slots",
                r.rep(), ms(r.baseMsptMean()), ms(r.baseMsptP99()), ms(r.msptMean()), ms(r.msptP50()), ms(r.msptP99()),
                ms(r.msptMax()), ms(r.modMean()), ms(r.modP50()), ms(r.modP99()), ms(r.modMax()), ms(r.limitNanos()),
                r.exhaustedPct(), num(r.visitsPerTick(), 1), num(r.nanosPerVisit() / 1000.0, 2), num(r.nanosPerDelivery() / 1000.0, 2),
                r.opsPerSecond(),
                r.itemsPerSecond(), r.fluidPerSecond(), r.energyPerSecond(), r.sourcesMoved(), r.sources(),
                r.sourcesSleeping(), r.sourcesTotal(), r.destinationsSleeping(), r.destinationsTotal(),
                ms(r.attachModNanos()), r.rebuilds() < 0 ? "—" : Long.toString(r.rebuilds()), num(ms(r.rebuildMeanNanos()), 3),
                ms(r.afterTouchMaxNanos()), r.slots());
    }

    private void summarize() {
        out.accept(summaryLine(job, reps));
    }

    /** Linha da tabela de resumo (Markdown): média ± desvio padrão entre as repetições. */
    static String summaryLine(Job job, List<Rep> reps) {
        return String.format(Locale.ROOT, "| %s | %s | %d | %d | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |",
                job.scenario().id, job.storage().id, job.nodes(), reps.size(),
                pm(reps, r -> ms(r.baseMsptMean()), 3),
                pm(reps, r -> ms(r.msptMean()), 3),
                pm(reps, r -> ms(r.modMean()), 4),
                pm(reps, r -> ms(r.modP99()), 4),
                pm(reps, r -> ms(r.modMax()), 3),
                pm(reps, Rep::exhaustedPct, 1),
                pm(reps, r -> r.nanosPerVisit() / 1000.0, 2),
                pm(reps, r -> r.nanosPerDelivery() / 1000.0, 2),
                pm(reps, r -> r.itemsPerSecond() + r.fluidPerSecond() + r.energyPerSecond(), 0),
                pm(reps, r -> 100.0 * r.sourcesMoved() / Math.max(1, r.sources()), 0),
                pm(reps, r -> ms(r.attachModNanos()), 2));
    }

    static final String SUMMARY_HEADER = "| cenário | armaz. | n | reps | MSPT base (ms) | MSPT com rede (ms) | mod média (ms/tick)"
            + " | mod p99 | mod máx | ticks c/ orçamento esgotado (%) | µs/visita | µs/entrega | unidades/s"
            + " | origens que moveram (%) | 1ª montagem (ms) |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|";

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
