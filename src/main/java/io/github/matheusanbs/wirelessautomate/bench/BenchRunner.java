package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Roda as tarefas do benchmark, uma de cada vez, e mede cada tick do servidor: do começo do
 * {@code ServerTickEvent} (fase {@code START}) (prioridade mais alta) ao fim da fase {@code END} (mais baixa). O laço do
 * mod roda na fase {@code END}, depois que o vanilla já fechou a conta do MSPT dele, então o MSPT do
 * vanilla (e o do {@code /forge tps}) não inclui o mod; esta medição inclui.
 *
 * <p>Cada tarefa diz quem transporta ({@link BenchTransport}); ao começar uma tarefa, o runner ajusta o
 * orçamento do Wireless Automate ({@code wa-full}) ou o modo assíncrono do Logistics Network
 * ({@code ln-async}) e devolve os valores de antes no fim dela.
 *
 * <p>Modo automático: com a variável {@code WA_BENCH} (ex.: {@code many:500:3:vanilla;idle:500:3:vanilla:ln}),
 * as tarefas começam quando o servidor sobe; o relatório vai para {@code WA_BENCH_OUT} (padrão
 * {@code bench-report.md} na pasta do servidor) e o servidor para no fim. Também lê
 * {@code WA_BENCH_BASELINE}, {@code WA_BENCH_WARMUP} e {@code WA_BENCH_MEASURE} (ticks), e
 * {@code WA_BENCH_SPRINT} (roda com {@code /tick sprint}, para perfis).
 */
@Mod.EventBusSubscriber(modid = WirelessAutomate.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BenchRunner {
    static final int DEFAULT_BASELINE = 100;
    static final int DEFAULT_MEASURE = 200;
    /** Ticks do servidor vazio medidos antes das tarefas, no modo automático. */
    static final int EMPTY_TICKS = 200;

    private static final Deque<BenchRun.Job> queue = new ArrayDeque<>();
    private static final List<BenchRun> finished = new ArrayList<>();
    private static @Nullable BenchRun current;
    private static @Nullable Consumer<String> feedback;
    private static @Nullable Path report;
    private static boolean auto;
    private static long tickStart;
    private static int emptyTicksLeft;
    private static final Samples emptyMspt = new Samples();
    private static final Samples emptyMod = new Samples();
    /** Valores de antes da tarefa, para devolver no fim ({@link #applyTransport}). */
    private static @Nullable Double savedBudget;
    private static @Nullable Boolean savedAdaptive;
    private static @Nullable Boolean savedLnAsync;

    static boolean busy() {
        return current != null || !queue.isEmpty() || emptyTicksLeft > 0;
    }

    static @Nullable String status() {
        BenchRun run = current;
        return run == null ? null : run.status() + (queue.isEmpty() ? "" : ", mais " + queue.size() + " na fila");
    }

    /** Enfileira tarefas; {@code out} recebe as linhas do relatório (além do log e do arquivo). */
    static void submit(List<BenchRun.Job> jobs, @Nullable Consumer<String> out, @Nullable Path reportFile) {
        queue.addAll(jobs);
        feedback = out;
        if (reportFile != null) {
            report = reportFile;
            writeHeader();
        }
    }

    static void stop() {
        queue.clear();
        emptyTicksLeft = 0;
        if (current != null) {
            current.abort();
            current = null;
        }
        restoreTransport();
    }

    /** Ajusta o orçamento do Wireless Automate ou o modo do Logistics Network para a tarefa. */
    private static void applyTransport(BenchTransport transport) {
        restoreTransport();
        switch (transport) {
            case WA -> {
            }
            case WA_FULL -> {
                savedBudget = Config.TICK_BUDGET_MS.get();
                savedAdaptive = Config.ADAPTIVE_BUDGET.get();
                Config.TICK_BUDGET_MS.set(BenchTransport.FULL_BUDGET_MS);
                Config.ADAPTIVE_BUDGET.set(false);
            }
            case LN, LN_RR, LN_ASYNC -> {
                LogisticsNetworkBench ln = LogisticsNetworkBench.get();
                savedLnAsync = ln.async();
                ln.setAsync(transport == BenchTransport.LN_ASYNC);
            }
        }
    }

    /** Devolve o que {@link #applyTransport} mudou. */
    private static void restoreTransport() {
        if (savedBudget != null) {
            Config.TICK_BUDGET_MS.set(savedBudget);
            savedBudget = null;
        }
        if (savedAdaptive != null) {
            Config.ADAPTIVE_BUDGET.set(savedAdaptive);
            savedAdaptive = null;
        }
        if (savedLnAsync != null) {
            LogisticsNetworkBench.get().setAsync(savedLnAsync);
            savedLnAsync = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        tickStart = System.nanoTime();
        BenchRun run = current;
        if (run != null) {
            run.preTick(event.getServer());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long tickNanos = System.nanoTime() - tickStart;
        if (!busy()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long modNanos = NetworkManager.get().budget().lastUsedNanos();
        if (emptyTicksLeft > 0) {
            emptyMspt.add(tickNanos);
            emptyMod.add(modNanos);
            if (--emptyTicksLeft == 0) {
                emit(String.format(Locale.ROOT, "Servidor sem cena (%d ticks): MSPT média %.3f ms, p99 %.3f ms; mod %.4f ms/tick",
                        emptyMspt.size(), emptyMspt.mean() / 1e6, emptyMspt.percentile(99) / 1e6, emptyMod.mean() / 1e6));
            }
            return;
        }
        if (current == null) {
            BenchRun.Job job = queue.poll();
            if (job == null) {
                return;
            }
            current = new BenchRun(job, intEnv("WA_BENCH_BASELINE", DEFAULT_BASELINE),
                    intEnv("WA_BENCH_WARMUP", job.scenario().warmupTicks),
                    intEnv("WA_BENCH_MEASURE", DEFAULT_MEASURE), BenchRunner::emit);
            try {
                applyTransport(job.transport());
                current.start(server);
            } catch (IllegalStateException e) {
                // Reflexão no Logistics Network falhou: aborta a tarefa em vez de medir pela metade.
                emit("ERRO na tarefa " + job.label() + ": " + e.getMessage());
                WirelessAutomate.LOGGER.error("Benchmark: tarefa {} abortada", job.label(), e);
                current.abort();
                current = null;
                restoreTransport();
                if (queue.isEmpty()) {
                    finish(server);
                }
            }
            return;
        }
        current.tick(server, tickNanos, modNanos);
        if (current.done()) {
            restoreTransport();
            if (!current.job.jitWarmup()) {
                finished.add(current);
            }
            current = null;
            if (queue.isEmpty()) {
                finish(server);
            }
        }
    }

    private static void finish(MinecraftServer server) {
        StringBuilder table = new StringBuilder("\n## Resumo\n\n").append(BenchRun.SUMMARY_HEADER).append('\n');
        for (BenchRun run : finished) {
            table.append(BenchRun.summaryLine(run.job, run.reps)).append('\n');
        }
        emit(table.toString());
        finished.clear();
        if (auto) {
            emit("Benchmark terminado; parando o servidor.");
            server.halt(false);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String spec = System.getenv("WA_BENCH");
        if (spec == null || spec.isBlank()) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<BenchRun.Job> jobs;
        try {
            jobs = parse(spec);
        } catch (IllegalArgumentException e) {
            WirelessAutomate.LOGGER.error("WA_BENCH inválida: {}", e.getMessage());
            server.halt(false);
            return;
        }
        auto = true;
        // Autosave no meio da medição vira pico de MSPT que não é do mod.
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "save-off");
        String sprint = System.getenv("WA_BENCH_SPRINT");
        if (sprint != null && !sprint.isBlank()) {
            // Só para perfis: sem a pausa entre ticks o servidor gasta todo o tempo trabalhando, e o
            // amostrador do JFR pega muito mais o código do mod. O MSPT continua por tick.
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tick sprint 1000000");
        }
        String out = System.getenv("WA_BENCH_OUT");
        // O código do motor começa interpretado: sem aquecer o JIT, a primeira tarefa sai bem mais lenta.
        List<BenchRun.Job> all = new ArrayList<>();
        all.add(new BenchRun.Job(BenchScenario.MANY, 200, 1, BenchStorage.VANILLA, BenchTransport.WA, true));
        if (BenchCapabilities.enabled()) {
            all.add(new BenchRun.Job(BenchScenario.MIXED, 60, 1, BenchStorage.VANILLA, BenchTransport.WA, true));
        }
        // O motor do Logistics Network também começa interpretado: o mesmo aquecimento para ele.
        if (jobs.stream().anyMatch(job -> job.transport().logisticsNetwork())) {
            all.add(new BenchRun.Job(BenchScenario.MANY, 200, 1, BenchStorage.VANILLA, BenchTransport.LN, true));
            if (BenchCapabilities.enabled()) {
                all.add(new BenchRun.Job(BenchScenario.MIXED, 60, 1, BenchStorage.VANILLA, BenchTransport.LN, true));
            }
        }
        all.addAll(jobs);
        emptyTicksLeft = EMPTY_TICKS;
        submit(all, null, Path.of(out == null || out.isBlank() ? "bench-report.md" : out));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        stop();
        finished.clear();
        feedback = null;
        report = null;
        auto = false;
    }

    /** {@code cenário[:n[:reps[:armazenamento[:transporte]]]]} separados por {@code ;} ou {@code ,}. */
    static List<BenchRun.Job> parse(String spec) {
        List<BenchRun.Job> jobs = new ArrayList<>();
        for (String item : spec.split("[;,]")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(":");
            BenchScenario scenario = BenchScenario.byId(parts[0]);
            if (scenario == null) {
                throw new IllegalArgumentException("cenário desconhecido: " + parts[0]);
            }
            int nodes = parts.length > 1 ? Integer.parseInt(parts[1]) : scenario.defaultNodes;
            int reps = parts.length > 2 ? Integer.parseInt(parts[2]) : 3;
            BenchStorage storage = parts.length > 3 ? BenchStorage.byId(parts[3]) : BenchStorage.VANILLA;
            if (storage == null) {
                throw new IllegalArgumentException("armazenamento desconhecido: " + parts[3]);
            }
            BenchTransport transport = parts.length > 4 ? BenchTransport.byId(parts[4]) : BenchTransport.WA;
            if (transport == null) {
                throw new IllegalArgumentException("transporte desconhecido: " + parts[4]);
            }
            String problem = problem(scenario, storage, transport);
            if (problem != null) {
                WirelessAutomate.LOGGER.warn("Benchmark: pulando {}: {}", trimmed, problem);
                continue;
            }
            jobs.add(new BenchRun.Job(scenario, Math.max(2, nodes), Math.max(1, reps), storage, transport));
        }
        return jobs;
    }

    /** Por que a tarefa não pode rodar aqui; {@code null} se pode. */
    static @Nullable String problem(BenchScenario scenario, BenchStorage storage, BenchTransport transport) {
        if (!storage.available()) {
            return "o Sophisticated Storage não está carregado";
        }
        if (transport.logisticsNetwork()) {
            if (!LogisticsNetworkBench.loaded()) {
                return "o Logistics Network não está carregado (scripts/bench.sh com uma tarefa :ln põe o jar)";
            }
            if (!scenario.comparable()) {
                return "o cenário " + scenario.id + " não tem equivalente no Logistics Network";
            }
        }
        if (scenario == BenchScenario.INFINITE) {
            if (!BenchCapabilities.enabled()) {
                return "o cenário inf precisa de -Dwirelessautomate.bench=true (run benchServer)";
            }
            if (storage != BenchStorage.VANILLA) {
                return "o cenário inf usa só máquinas de teste; rode com vanilla";
            }
        }
        if (scenario == BenchScenario.MIXED && !BenchCapabilities.enabled()) {
            return "o cenário misto precisa de -Dwirelessautomate.bench=true (run benchServer)";
        }
        if (scenario == BenchScenario.BIG_STACK) {
            if (!BenchCapabilities.enabled()) {
                return "o cenário bigstack precisa de -Dwirelessautomate.bench=true (run benchServer)";
            }
            if (storage != BenchStorage.VANILLA) {
                return "o cenário bigstack usa só máquinas de teste; rode com vanilla";
            }
        }
        return null;
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void emit(String line) {
        WirelessAutomate.LOGGER.info("[bench] {}", line);
        Consumer<String> out = feedback;
        if (out != null) {
            out.accept(line);
        }
        append(line);
    }

    private static void writeHeader() {
        Runtime runtime = Runtime.getRuntime();
        String header = "# Benchmark do Wireless Automate\n\n"
                + "- Data: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n"
                + "- Minecraft " + SharedConstants.getCurrentVersion().getName()
                + ", mod " + ModList.get().getModContainerById(WirelessAutomate.MODID)
                        .map(c -> c.getModInfo().getVersion().toString()).orElse("?") + "\n"
                + "- Java " + System.getProperty("java.version") + ", " + runtime.availableProcessors()
                + " CPUs, heap máx " + runtime.maxMemory() / (1024 * 1024) + " MB, "
                + System.getProperty("os.name") + " " + System.getProperty("os.arch") + "\n"
                + "- Orçamento: " + Config.TICK_BUDGET_MS.get() + " ms/tick, adaptativo " + Config.ADAPTIVE_BUDGET.get() + "\n"
                + "- Sophisticated Storage: " + (BenchStorage.SOPH.available() ? "sim" : "não")
                + ", Spark: " + (ModList.get().isLoaded("spark") ? "sim" : "não")
                + ", máquinas de teste: " + (BenchCapabilities.enabled() ? "sim" : "não") + "\n"
                + "- Logistics Network: " + (LogisticsNetworkBench.loaded()
                        ? LogisticsNetworkBench.version() + " (assíncrono na config: "
                                + (LogisticsNetworkBench.get().async() ? "ligado" : "desligado") + ")"
                        : "não") + "\n\n";
        try {
            Files.writeString(report, header, StandardCharsets.UTF_8);
        } catch (IOException e) {
            WirelessAutomate.LOGGER.error("Benchmark: não deu para escrever {}", report, e);
            report = null;
        }
    }

    private static void append(String line) {
        Path path = report;
        if (path == null) {
            return;
        }
        try {
            Files.writeString(path, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            WirelessAutomate.LOGGER.error("Benchmark: não deu para escrever {}", path, e);
        }
    }

    private BenchRunner() {
    }
}
