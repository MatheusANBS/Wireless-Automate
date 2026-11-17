package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import org.jetbrains.annotations.Nullable;

/**
 * A frente de uma peça posta na casa de uma Porta ou do Sintonizador da Armilar de um Núcleo: a direção para fora
 * dessa casa. Só as quatro casas do meio da camada do chão têm frente; a Laje e o resto não. Lógica pura.
 */
public final class PlacementFacing {
    private static final int LAST = ResonatorLayout.SIZE - 1;
    private static final int MID = ResonatorLayout.SIZE / 2;

    private PlacementFacing() {
    }

    /**
     * A face para fora da casa (x, y, z) da caixa de canto mínimo {@code origin}: {@code NORTH} em (2, 0, 0),
     * {@code SOUTH} em (2, 0, 4), {@code WEST} em (0, 0, 2) e {@code EAST} em (4, 0, 2), com os índices contados do
     * canto mínimo no mundo; qualquer outra casa dá {@code null}.
     */
    public static @Nullable Face wallFacing(int[] origin, int x, int y, int z) {
        if (y != origin[1]) {
            return null;
        }
        int lx = x - origin[0];
        int lz = z - origin[2];
        if (lx == MID) {
            return lz == 0 ? Face.NORTH : lz == LAST ? Face.SOUTH : null;
        }
        if (lz == MID) {
            return lx == 0 ? Face.WEST : lx == LAST ? Face.EAST : null;
        }
        return null;
    }
}
