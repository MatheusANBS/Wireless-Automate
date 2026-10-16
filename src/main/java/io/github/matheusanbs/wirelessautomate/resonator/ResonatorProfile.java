package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * O tempo do Ressonador no servidor, para o {@code /wa profile}: o tick do gerenciador (a varredura do balde e os
 * lotes) e as revalidações da estrutura, cada um com o último tick e a média móvel, como o orçamento do
 * transporte. Só a thread do servidor mexe.
 */
public final class ResonatorProfile {
    /** O mesmo peso da média do {@code TickBudget}: cerca de um segundo de memória. */
    private static final double AVERAGE_WEIGHT = 0.05;

    private static long lastBatchNanos;
    private static double averageBatchNanos;
    private static long pendingRevalidationNanos;
    private static long lastRevalidationNanos;
    private static double averageRevalidationNanos;

    /** O que o perfil mostra: Núcleos carregados, quantos montados e os tempos em nanossegundos. */
    public record Snapshot(int cores, int formed, double averageBatchNanos, long lastBatchNanos,
                           double averageRevalidationNanos, long lastRevalidationNanos) {
    }

    private ResonatorProfile() {
    }

    /** Fecha um tick do servidor: o tempo do gerenciador e o das revalidações que rodaram desde o anterior. */
    static void tickEnded(long batchNanos) {
        lastBatchNanos = batchNanos;
        averageBatchNanos += (batchNanos - averageBatchNanos) * AVERAGE_WEIGHT;
        lastRevalidationNanos = pendingRevalidationNanos;
        averageRevalidationNanos += (pendingRevalidationNanos - averageRevalidationNanos) * AVERAGE_WEIGHT;
        pendingRevalidationNanos = 0;
    }

    static void revalidated(long nanos) {
        pendingRevalidationNanos += nanos;
    }

    public static Snapshot snapshot() {
        return new Snapshot(ResonatorManager.coreCount(), ResonatorManager.formedCount(), averageBatchNanos,
                lastBatchNanos, averageRevalidationNanos, lastRevalidationNanos);
    }

    /** Parada do servidor: o próximo mundo começa sem medições. */
    public static void reset() {
        lastBatchNanos = 0;
        averageBatchNanos = 0;
        pendingRevalidationNanos = 0;
        lastRevalidationNanos = 0;
        averageRevalidationNanos = 0;
    }
}
