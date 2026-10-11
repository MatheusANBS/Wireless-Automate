package io.github.matheusanbs.wirelessautomate.range;

import java.util.Locale;

/**
 * Regras de alcance do Upgrade de Alcance: distância, raio e o motivo de uma recusa ao vincular.
 *
 * <p>Lógica pura, sem classes do Minecraft, para ser testada com JUnit.
 */
public final class RangeRules {
    private RangeRules() {
    }

    /** Distância de Chebyshev: a maior diferença entre os eixos. */
    public static int chebyshev(int x1, int y1, int z1, int x2, int y2, int z2) {
        return Math.max(Math.abs(x1 - x2), Math.max(Math.abs(y1 - y2), Math.abs(z1 - z2)));
    }

    public static boolean withinRadius(int x1, int y1, int z1, int x2, int y2, int z2, int radius) {
        return chebyshev(x1, y1, z1, x2, y2, z2) <= radius;
    }

    /** Por que um vínculo foi recusado. {@code NONE} quer dizer que pode vincular. */
    public enum LinkRefusal {
        NONE, NO_UPGRADE, NO_RANGE, OTHER_DIMENSION, OUT_OF_RANGE, LIMIT, NOT_A_MACHINE, SELF, ROUTER, ATTACHED, PROTECTED;

        /** Chave de tradução da recusa. */
        public String key() {
            return "range.refusal." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Decide o vínculo pela ordem: upgrade, raio do tier maior que zero, mesma dimensão, distância
     * dentro do raio e limite ({@code count} é quantas já estão vinculadas; com {@code count == max} recusa).
     */
    public static LinkRefusal check(boolean hasUpgrade, int radius, boolean sameDimension, int distance, int count, int max) {
        if (!hasUpgrade) {
            return LinkRefusal.NO_UPGRADE;
        }
        if (radius <= 0) {
            return LinkRefusal.NO_RANGE;
        }
        if (!sameDimension) {
            return LinkRefusal.OTHER_DIMENSION;
        }
        if (distance > radius) {
            return LinkRefusal.OUT_OF_RANGE;
        }
        if (count >= max) {
            return LinkRefusal.LIMIT;
        }
        return LinkRefusal.NONE;
    }
}
