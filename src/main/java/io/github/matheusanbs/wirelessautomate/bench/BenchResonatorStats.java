package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorProfile;
import java.util.List;
import java.util.Locale;

/**
 * As medições do Ressonador numa repetição dos cenários {@code resonator-*}: o tempo do gerenciador (varredura do
 * balde e lotes) e o das revalidações por tick, lidos do {@link ResonatorProfile} no fim de cada tick, a
 * montagem dos Núcleos no aquecimento e, no cenário do evento, o pico dos ticks logo depois de quebrar e de
 * devolver os cantos das cascas.
 */
final class BenchResonatorStats {
    /** Ticks depois da quebra (ou da volta) em que o pico é procurado: a revalidação é agendada para 1 tick. */
    static final int EVENT_WINDOW = 5;
    /** Ticks entre quebrar os cantos e devolvê-los. */
    static final int RESTORE_AFTER = 40;

    /** Uma repetição; tempos em ns. */
    record Rep(int rep, double batchMean, long batchP99, long batchMax, double revalidationMean,
            long formationRevalidationNanos, long formationMsptMax, int formed, int total, double bufferFill,
            long breakMsptMax, long breakRevalidationMax, long restoreMsptMax, long restoreRevalidationMax,
            int formedAfterBreak) {
    }

    private final Samples batch = new Samples();
    private final Samples revalidation = new Samples();
    private long formationRevalidation;
    private long formationMspt;
    private int breakTick = -1;
    private long breakMspt;
    private long breakRevalidation;
    private long restoreMspt;
    private long restoreRevalidation;
    private int formedAfterBreak = -1;

    void clear() {
        batch.clear();
        revalidation.clear();
        formationRevalidation = 0;
        formationMspt = 0;
        breakTick = -1;
        breakMspt = 0;
        breakRevalidation = 0;
        restoreMspt = 0;
        restoreRevalidation = 0;
        formedAfterBreak = -1;
    }

    /** Um tick do aquecimento (e o logo depois de pôr os Núcleos): a montagem cai aqui. */
    void warmup(long tickNanos) {
        formationRevalidation += ResonatorProfile.snapshot().lastRevalidationNanos();
        formationMspt = Math.max(formationMspt, tickNanos);
    }

    /** Um tick medido; {@code phaseTick} conta da primeira medição (1). */
    void measure(int phaseTick, long tickNanos) {
        ResonatorProfile.Snapshot snapshot = ResonatorProfile.snapshot();
        batch.add(snapshot.lastBatchNanos());
        revalidation.add(snapshot.lastRevalidationNanos());
        if (breakTick < 0) {
            return;
        }
        int after = phaseTick - breakTick;
        if (after > 0 && after <= EVENT_WINDOW) {
            breakMspt = Math.max(breakMspt, tickNanos);
            breakRevalidation = Math.max(breakRevalidation, snapshot.lastRevalidationNanos());
        }
        if (after == RESTORE_AFTER - 1) {
            formedAfterBreak = snapshot.formed();
        }
        int afterRestore = after - RESTORE_AFTER;
        if (afterRestore > 0 && afterRestore <= EVENT_WINDOW) {
            restoreMspt = Math.max(restoreMspt, tickNanos);
            restoreRevalidation = Math.max(restoreRevalidation, snapshot.lastRevalidationNanos());
        }
    }

    /** Os cantos foram quebrados no fim do tick medido {@code phaseTick}. */
    void broke(int phaseTick) {
        breakTick = phaseTick;
    }

    Rep finish(int rep, BenchResonators resonators) {
        return new Rep(rep, batch.mean(), batch.percentile(99), batch.max(), revalidation.mean(),
                formationRevalidation, formationMspt, resonators.formed(), resonators.list.size(),
                resonators.bufferFill(), breakMspt, breakRevalidation, restoreMspt, restoreRevalidation,
                formedAfterBreak);
    }

    static String repLine(Rep r) {
        String line = String.format(Locale.ROOT,
                "    Ressonador, rep %d: gerenciador %.4f ms/tick (p99 %.4f, máx %.4f) | revalidações %.4f ms/tick"
                        + " | montados %d/%d, buffer médio %.1f%% | montagem: %.3f ms de revalidação, tick máx %.3f ms",
                r.rep(), ms(r.batchMean()), ms(r.batchP99()), ms(r.batchMax()), ms(r.revalidationMean()), r.formed(),
                r.total(), 100 * r.bufferFill(), ms(r.formationRevalidationNanos()), ms(r.formationMsptMax()));
        if (r.formedAfterBreak() < 0) {
            return line;
        }
        return line + String.format(Locale.ROOT,
                " | evento: quebra tick máx %.3f ms (revalidação %.3f ms, montados depois %d/%d),"
                        + " volta tick máx %.3f ms (revalidação %.3f ms)",
                ms(r.breakMsptMax()), ms(r.breakRevalidationMax()), r.formedAfterBreak(), r.total(),
                ms(r.restoreMsptMax()), ms(r.restoreRevalidationMax()));
    }

    static final String SUMMARY_HEADER = "| cenário | n | reps | MSPT base (ms) | MSPT medido (ms) | Δ MSPT (ms)"
            + " | MSPT p99 | MSPT máx | mod (ms/tick) | Ressonador (ms/tick) | Ressonador p99 | Ressonador máx"
            + " | buffer médio (%) | montagem (ms) | quebra: tick máx (ms) | quebra: revalidação (ms)"
            + " | volta: tick máx (ms) | volta: revalidação (ms) |"
            + "\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|";

    /** Linha do resumo do Ressonador: média ± desvio entre as repetições. */
    static String summaryLine(BenchRun.Job job, List<BenchRun.Rep> reps, List<Rep> resonator) {
        boolean event = resonator.stream().anyMatch(r -> r.formedAfterBreak() >= 0);
        return String.format(Locale.ROOT, "| %s | %d | %d | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s"
                        + " | %s | %s | %s | %s |",
                job.scenario().id, job.nodes(), reps.size(),
                pm(reps.stream().mapToDouble(r -> ms(r.baseMsptMean())).toArray(), 3),
                pm(reps.stream().mapToDouble(r -> ms(r.msptMean())).toArray(), 3),
                pm(reps.stream().mapToDouble(r -> ms(r.costNanos())).toArray(), 3),
                pm(reps.stream().mapToDouble(r -> ms(r.msptP99())).toArray(), 3),
                pm(reps.stream().mapToDouble(r -> ms(r.msptMax())).toArray(), 3),
                pm(reps.stream().mapToDouble(r -> ms(r.modMean())).toArray(), 4),
                pm(resonator.stream().mapToDouble(r -> ms(r.batchMean())).toArray(), 4),
                pm(resonator.stream().mapToDouble(r -> ms(r.batchP99())).toArray(), 4),
                pm(resonator.stream().mapToDouble(r -> ms(r.batchMax())).toArray(), 3),
                pm(resonator.stream().mapToDouble(r -> 100 * r.bufferFill()).toArray(), 1),
                pm(resonator.stream().mapToDouble(r -> ms(r.formationRevalidationNanos())).toArray(), 2),
                event ? pm(resonator.stream().mapToDouble(r -> ms(r.breakMsptMax())).toArray(), 2) : "—",
                event ? pm(resonator.stream().mapToDouble(r -> ms(r.breakRevalidationMax())).toArray(), 2) : "—",
                event ? pm(resonator.stream().mapToDouble(r -> ms(r.restoreMsptMax())).toArray(), 2) : "—",
                event ? pm(resonator.stream().mapToDouble(r -> ms(r.restoreRevalidationMax())).toArray(), 2) : "—");
    }

    private static String pm(double[] values, int decimals) {
        double[] md = Samples.meanAndDeviation(values);
        String format = "%." + decimals + "f";
        return String.format(Locale.ROOT, format + " ± " + format, md[0], md[1]);
    }

    private static double ms(double nanos) {
        return nanos / 1_000_000.0;
    }
}
