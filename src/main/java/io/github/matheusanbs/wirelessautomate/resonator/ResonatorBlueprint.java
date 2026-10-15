package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;

/**
 * O desenho que a projeção sugere em volta de um Núcleo solto: o Núcleo no centro da face para onde ele está
 * virado, a Porta de Energia na face oposta, a Porta de Cristais e o vidro nas laterais e o Sintonizador em cima.
 * A montagem real aceita mais arranjos; este é só o mais comum. Lógica pura, índices locais {@code [x][y][z]}.
 */
public final class ResonatorBlueprint {
    private static final int SIZE = ResonatorLayout.SIZE;
    private static final int LAST = SIZE - 1;
    private static final int MID = SIZE / 2;

    /** Onde vai o vidro: a janela 3×3 numa face ({@code VITRINE}) ou a linha de cima das quatro laterais ({@code FAIXA}). */
    public enum Layout {
        VITRINE, FAIXA;

        public Layout other() {
            return this == VITRINE ? FAIXA : VITRINE;
        }
    }

    private ResonatorBlueprint() {
    }

    public static Part[][][] expected(Face coreFace, Layout layout) {
        Part[][][] parts = new Part[SIZE][SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    parts[x][y][z] = onShell(x) || onShell(y) || onShell(z) ? Part.CASING : Part.AIR;
                }
            }
        }
        Face glassFace = coreFace.dy() != 0 ? Face.SOUTH : clockwise(coreFace);
        put(parts, coreFace, Part.CORE);
        put(parts, opposite(coreFace), Part.ENERGY_PORT);
        put(parts, coreFace.dy() != 0 ? Face.NORTH : counterClockwise(coreFace), Part.CRYSTAL_PORT);
        put(parts, coreFace.dy() != 0 ? Face.EAST : Face.UP, Part.TUNER);
        if (layout == Layout.VITRINE) {
            window(parts, glassFace);
        } else {
            band(parts);
        }
        return parts;
    }

    /** O canto mínimo da caixa no mundo, com o Núcleo em (cx, cy, cz) no centro da face {@code coreFace}. */
    public static int[] origin(int cx, int cy, int cz, Face coreFace) {
        int[] center = faceCenter(coreFace);
        return new int[] {cx - center[0], cy - center[1], cz - center[2]};
    }

    private static boolean onShell(int c) {
        return c == 0 || c == LAST;
    }

    private static int[] faceCenter(Face face) {
        return new int[] {MID + MID * face.dx(), MID + MID * face.dy(), MID + MID * face.dz()};
    }

    private static void put(Part[][][] parts, Face face, Part part) {
        int[] c = faceCenter(face);
        parts[c[0]][c[1]][c[2]] = part;
    }

    /** As nove posições do miolo da face viram vidro. */
    private static void window(Part[][][] parts, Face face) {
        int[] c = faceCenter(face);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                int x = face.dx() != 0 ? c[0] : c[0] + a;
                int y = face.dy() != 0 ? c[1] : face.dx() != 0 ? c[1] + a : c[1] + b;
                int z = face.dz() != 0 ? c[2] : c[2] + b;
                parts[x][y][z] = Part.GLASS;
            }
        }
    }

    /** A linha de cima do miolo das quatro faces laterais, só onde não há peça. */
    private static void band(Part[][][] parts) {
        int y = LAST - 1;
        for (int c = 1; c < LAST; c++) {
            glassIfCasing(parts, c, y, 0);
            glassIfCasing(parts, c, y, LAST);
            glassIfCasing(parts, 0, y, c);
            glassIfCasing(parts, LAST, y, c);
        }
    }

    private static void glassIfCasing(Part[][][] parts, int x, int y, int z) {
        if (parts[x][y][z] == Part.CASING) {
            parts[x][y][z] = Part.GLASS;
        }
    }

    private static Face opposite(Face face) {
        return switch (face) {
            case DOWN -> Face.UP;
            case UP -> Face.DOWN;
            case NORTH -> Face.SOUTH;
            case SOUTH -> Face.NORTH;
            case WEST -> Face.EAST;
            case EAST -> Face.WEST;
        };
    }

    /** Sentido horário visto de cima, só para as faces laterais. */
    private static Face clockwise(Face face) {
        return switch (face) {
            case NORTH -> Face.EAST;
            case EAST -> Face.SOUTH;
            case SOUTH -> Face.WEST;
            case WEST -> Face.NORTH;
            case UP, DOWN -> face;
        };
    }

    private static Face counterClockwise(Face face) {
        return opposite(clockwise(face));
    }
}
