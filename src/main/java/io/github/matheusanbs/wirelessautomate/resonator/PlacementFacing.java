package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;

/**
 * A frente de uma peça posta numa parede da caixa 5×5×5 de um Núcleo: a direção para fora dessa parede. Numa
 * aresta ou num canto vale a parede que aponta mais para o jogador. Lógica pura.
 */
public final class PlacementFacing {
    private static final int LAST = ResonatorLayout.SIZE - 1;

    private PlacementFacing() {
    }

    /**
     * A face para fora da parede da caixa de canto mínimo {@code origin} em que (x, y, z) fica, ou {@code null} no
     * miolo ou fora da caixa. Com mais de uma parede, a de maior produto escalar com o vetor do centro do bloco até
     * o olho do jogador; o empate fica com a primeira na ordem do {@code Direction}.
     */
    public static Face wallFacing(int[] origin, int x, int y, int z, double toPlayerX, double toPlayerY, double toPlayerZ) {
        int lx = x - origin[0];
        int ly = y - origin[1];
        int lz = z - origin[2];
        if (outside(lx) || outside(ly) || outside(lz)) {
            return null;
        }
        Face best = null;
        double bestDot = 0;
        for (Face face : Face.values()) {
            if (!onWall(face, lx, ly, lz)) {
                continue;
            }
            double dot = face.dx() * toPlayerX + face.dy() * toPlayerY + face.dz() * toPlayerZ;
            if (best == null || dot > bestDot) {
                best = face;
                bestDot = dot;
            }
        }
        return best;
    }

    private static boolean outside(int c) {
        return c < 0 || c > LAST;
    }

    private static boolean onWall(Face face, int lx, int ly, int lz) {
        int c = face.dx() != 0 ? lx : face.dy() != 0 ? ly : lz;
        int sign = face.dx() + face.dy() + face.dz();
        return c == (sign > 0 ? LAST : 0);
    }
}
