package io.github.matheusanbs.wirelessautomate.client.dock.render;

/** Os ganchos do e2e que param a animação da Doca numa fase, como os do Reparador. Puro. */
public final class DockClock {
    /** Só do e2e: o segundo do laço (0 a 6) em que todas as Docas ficam paradas, ou −1. */
    public static double freezeForTests = -1;
    /** Só do e2e: com o tempo parado, o laço é o de trabalho (senão, o de repouso). */
    public static boolean workForTests;

    private DockClock() {
    }

    public static boolean frozen() {
        return freezeForTests >= 0;
    }

    /** Solta o tempo. */
    public static void release() {
        freezeForTests = -1;
        workForTests = false;
    }
}
