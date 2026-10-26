package io.github.matheusanbs.wirelessautomate.client.repairer.render;

/** O tempo da animação de cada linha, com o gancho do e2e que o congela numa fase. Puro. */
public final class RepairClock {
    /** Só do e2e: o segundo (0 a 7) em que todas as linhas que andam ficam paradas, ou −1. */
    public static double freezeForTests = -1;

    private RepairClock() {
    }

    public static boolean frozen() {
        return freezeForTests >= 0;
    }

    public static double seconds(long end, int duration, long gameTime, float partialTick) {
        return frozen() ? freezeForTests : RepairTimeline.seconds(end, duration, gameTime + (double) partialTick);
    }
}
