package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * Os grãos de redstone da Porta de Cristais ao Opaco que recarrega, em lógica pura (o desenho é do
 * {@code ArmilarScene}). Porte do relógio e da vida de {@code ChamberParticles}: o relógio sobe 0,1 por tick, só com
 * alvo; há no máximo 4 vivos e cada um voa por 33 ticks.
 */
final class ArmilarGrains {
    static final int MAX = 4;
    static final int TICKS = 33;
    private static final double SPAWN_PER_TICK = 2.0 / 20;

    private final boolean[] alive = new boolean[MAX];
    private final int[] chamber = new int[MAX];
    private final int[] age = new int[MAX];
    private double clock;

    /** Um tick: avança os grãos vivos, apaga os de câmara que sumiu e, com alvo, nasce um novo quando o relógio passa de 1. */
    void tick(int targetChamber, int chamberCount) {
        clock += targetChamber < 0 ? 0 : SPAWN_PER_TICK;
        if (clock > 1) {
            clock -= 1;
            int g = free();
            if (g >= 0 && targetChamber >= 0) {
                alive[g] = true;
                chamber[g] = targetChamber;
                age[g] = 0;
            }
        }
        for (int g = 0; g < MAX; g++) {
            if (alive[g] && chamber[g] >= chamberCount) {
                alive[g] = false;
            }
            if (alive[g]) {
                // Idade em ticks inteiros: somar 1/33 trinta e três vezes dá 0,99999... e o grão voaria 34 ticks.
                age[g]++;
                if (age[g] >= TICKS) {
                    alive[g] = false;
                }
            }
        }
    }

    private int free() {
        for (int g = 0; g < MAX; g++) {
            if (!alive[g]) {
                return g;
            }
        }
        return -1;
    }

    boolean alive(int g) {
        return alive[g];
    }

    int chamber(int g) {
        return chamber[g];
    }

    double progress(int g) {
        return (double) age[g] / TICKS;
    }
}
