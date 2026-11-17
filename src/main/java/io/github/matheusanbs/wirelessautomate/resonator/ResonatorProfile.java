package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * O tempo do Ressonador no servidor, para o {@code /wa profile}: o tick inteiro do gerenciador (a geração de cada
 * tick, a varredura do balde e os lotes) e as revalidações da estrutura, cada um com o último tick e a média móvel, como o orçamento do
 * transporte. Só a thread do servidor mexe.
 */
public final class ResonatorProfile {
    /** O mesmo peso da média do {@code TickBudget}: cerca de um segundo de memória. */
    private static final double AVERAGE_WEIGHT = 0.05;

    private static long lastManagerNanos;
    private static double averageManagerNanos;
    private static long pendingRevalidationNanos;
    private static long lastRevalidationNanos;
    private static double averageRevalidationNanos;

    /** O que o perfil mostra: Núcleos carregados, quantos montados e os tempos em nanossegundos. */
    public record Snapshot(int cores, int formed, double averageManagerNanos, long lastManagerNanos,
                           double averageRevalidationNanos, long lastRevalidationNanos) {
    }

    private ResonatorProfile() {
    }

    /** Fecha um tick do servidor: o tempo do gerenciador e o das revalidações que rodaram desde o anterior. */
    static void tickEnded(long managerNanos) {
        lastManagerNanos = managerNanos;
        averageManagerNanos += (managerNanos - averageManagerNanos) * AVERAGE_WEIGHT;
        lastRevalidationNanos = pendingRevalidationNanos;
        averageRevalidationNanos += (pendingRevalidationNanos - averageRevalidationNanos) * AVERAGE_WEIGHT;
        pendingRevalidationNanos = 0;
    }

    static void revalidated(long nanos) {
        pendingRevalidationNanos += nanos;
    }

    public static Snapshot snapshot() {
        return new Snapshot(ResonatorManager.coreCount(), ResonatorManager.formedCount(), averageManagerNanos,
                lastManagerNanos, averageRevalidationNanos, lastRevalidationNanos);
    }

    /** Parada do servidor: o próximo mundo começa sem medições. */
    public static void reset() {
        lastManagerNanos = 0;
        averageManagerNanos = 0;
        pendingRevalidationNanos = 0;
        lastRevalidationNanos = 0;
        averageRevalidationNanos = 0;
    }
}
