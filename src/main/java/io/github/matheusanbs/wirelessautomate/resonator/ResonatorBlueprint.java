package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.Arrays;

/**
 * O desenho que a projeção sugere em volta de um Núcleo solto: a Armilar inteira, com a frente dele. É o mesmo
 * desenho da regra ({@link ResonatorLayout#expected}), nos índices da caixa do mundo. Lógica pura.
 */
public final class ResonatorBlueprint {
    private static final int SIZE = ResonatorLayout.SIZE;
    private static final int LAST = SIZE - 1;

    private ResonatorBlueprint() {
    }

    /**
     * As peças esperadas nos índices {@code [x][y][z]} da caixa do mundo, a partir do canto mínimo
     * ({@link #origin}), para a frente {@code front}.
     */
    public static Part[][][] expected(Face front) {
        Part[][][] parts = new Part[SIZE][SIZE][SIZE];
        for (Part[][] plane : parts) {
            for (Part[] row : plane) {
                Arrays.fill(row, Part.AIR);
            }
        }
        int[] origin = origin(0, 0, 0, front);
        for (int lx = 0; lx < SIZE; lx++) {
            for (int ly = 0; ly < SIZE; ly++) {
                for (int lz = 0; lz < SIZE; lz++) {
                    int[] w = ResonatorLayout.world(front, 0, 0, 0, lx, ly, lz);
                    parts[w[0] - origin[0]][w[1] - origin[1]][w[2] - origin[2]] = ResonatorLayout.expected(lx, ly, lz);
                }
            }
        }
        return parts;
    }

    /** O canto mínimo da caixa no mundo, com o Núcleo em (cx, cy, cz) e a frente {@code front}. */
    public static int[] origin(int cx, int cy, int cz, Face front) {
        int[] a = ResonatorLayout.world(front, cx, cy, cz, 0, 0, 0);
        int[] b = ResonatorLayout.world(front, cx, cy, cz, LAST, 0, LAST);
        return new int[] {Math.min(a[0], b[0]), cy, Math.min(a[2], b[2])};
    }
}
