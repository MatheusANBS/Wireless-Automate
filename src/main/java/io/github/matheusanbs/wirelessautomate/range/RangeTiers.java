package io.github.matheusanbs.wirelessautomate.range;

/**
 * Valores padrão do Upgrade de Alcance por tier: raio em blocos e máximo de máquinas vinculadas.
 * Os arrays seguem a posição do {@code RouterTier} (Básico a Ultimate); o Básico não aceita o upgrade.
 *
 * <p>Lógica pura, sem classes do Minecraft, para ser testada com JUnit.
 */
public final class RangeTiers {
    /** Maior raio que a config aceita. */
    public static final int MAX_RADIUS = 64;
    /** Maior limite de máquinas que a config aceita. */
    public static final int MAX_MACHINES = 512;

    private static final int[] RADIUS = {0, 6, 12, 16, 24, 32, 48, 64};
    private static final int[] MACHINES = {0, 8, 16, 32, 48, 64, 96, 128};

    private RangeTiers() {
    }

    public static int defaultRadius(int tierOrdinal) {
        return RADIUS[tierOrdinal];
    }

    public static int defaultMaxMachines(int tierOrdinal) {
        return MACHINES[tierOrdinal];
    }
}
