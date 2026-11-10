package io.github.matheusanbs.wirelessautomate.dock;

import java.util.function.IntUnaryOperator;

/**
 * Alcance da Doca de Padrões: ela usa a linha do tier um degrau acima do dela, e o Ultimate, que não
 * tem degrau acima, lê valores próprios. Quem chama passa o ordinal do tier seguinte (ordinal + 1,
 * não o {@code next()}, que pula os tiers sem o mod) ou -1 para o Ultimate, de modo que a conta fica
 * sem {@code RouterTier} e sem {@code Config}.
 *
 * <p>Lógica pura, sem classes do Minecraft, para ser testada com JUnit.
 */
public final class DockReach {
    public static final int ULTIMATE_RADIUS = 96;
    /** Maior raio que a config do Ultimate aceita. */
    public static final int MAX_ULTIMATE_RADIUS = 128;
    public static final int ULTIMATE_MACHINES = 256;

    private DockReach() {
    }

    /** Raio em blocos; {@code nextOrdinal} é o do tier seguinte, ou -1 para o Ultimate. */
    public static int radius(int nextOrdinal, IntUnaryOperator routerRadius, int ultimateRadius) {
        if (nextOrdinal < 0) {
            return Math.min(ultimateRadius, MAX_ULTIMATE_RADIUS);
        }
        return routerRadius.applyAsInt(nextOrdinal);
    }

    /** Máximo de máquinas; {@code nextOrdinal} é o do tier seguinte, ou -1 para o Ultimate. */
    public static int max(int nextOrdinal, IntUnaryOperator routerMax, int ultimateMax) {
        if (nextOrdinal < 0) {
            return ultimateMax;
        }
        return routerMax.applyAsInt(nextOrdinal);
    }
}
