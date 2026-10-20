package io.github.matheusanbs.wirelessautomate.client.tablet;

/**
 * O ponto mais perto do mouse entre os pontos à vista do Mapa do Tablet, dentro de uma distância máxima. Num empate
 * (pontos sobrepostos), ganha o preferido (a máquina-alvo do painel) e, sem ele no empate, o primeiro da lista: o
 * clique e a dica pegam sempre o mesmo. Lógica pura, com JUnit.
 */
final class MapPick {
    private MapPick() {
    }

    /**
     * O índice do ponto mais perto de ({@code mouseX}, {@code mouseY}) entre os {@code count} primeiros, com a
     * distância ao quadrado até {@code maxDistanceSq}, ou -1. {@code preferred} (ou -1) ganha os empates.
     */
    static int nearest(int[] xs, int[] ys, int count, double mouseX, double mouseY, double maxDistanceSq,
            int preferred) {
        int best = -1;
        double bestDistance = maxDistanceSq;
        for (int k = 0; k < count; k++) {
            double dx = xs[k] - mouseX;
            double dy = ys[k] - mouseY;
            double d = dx * dx + dy * dy;
            if (d < bestDistance || (d <= maxDistanceSq && d == bestDistance && (best < 0 || k == preferred))) {
                best = k;
                bestDistance = d;
            }
        }
        return best;
    }
}
