package io.github.matheusanbs.wirelessautomate.client.dock;

/**
 * O cubo do alcance da Doca no mundo ({@link DockReachPaint}): os limites, recortados na altura do mundo, e as 12
 * arestas, relativos ao canto do bloco da Doca. Distância de Chebyshev: o cubo tem lado {@code 2r + 1} e cobre os
 * blocos de {@code -r} a {@code +r} em cada eixo.
 *
 * <p>Lógica pura, sem classes do Minecraft, para ser testada com JUnit.
 */
public final class DockReachBox {
    private DockReachBox() {
    }

    /**
     * Os limites {@code {x0, y0, z0, x1, y1, z1}} do cubo de raio {@code radius} da Doca na altura {@code dockY},
     * relativos ao canto dela, com o y recortado entre {@code minY} (o primeiro bloco do mundo) e {@code maxY} (o
     * primeiro acima do último). Um cubo todo fora da altura fica achatado no limite (y0 = y1).
     */
    public static float[] bounds(int radius, int dockY, int minY, int maxY) {
        int r = Math.max(radius, 0);
        int y0 = Math.max(-r, minY - dockY);
        int y1 = Math.min(r + 1, maxY - dockY);
        if (y1 < y0) {
            y1 = y0;
        }
        return new float[] {-r, y0, -r, r + 1, y1, r + 1};
    }

    /**
     * As 12 arestas da caixa {@code box} ({@link #bounds}), seis números por aresta (as duas pontas): primeiro as
     * quatro em x, depois as quatro em y e as quatro em z.
     */
    public static float[] edges(float[] box) {
        float[] out = new float[72];
        int n = 0;
        for (int axis = 0; axis < 3; axis++) {
            int a = (axis + 1) % 3;
            int b = (axis + 2) % 3;
            for (int k = 0; k < 4; k++) {
                float[] p = new float[3];
                p[a] = (k & 1) == 0 ? box[a] : box[a + 3];
                p[b] = (k & 2) == 0 ? box[b] : box[b + 3];
                for (int end = 0; end < 2; end++) {
                    p[axis] = end == 0 ? box[axis] : box[axis + 3];
                    out[n++] = p[0];
                    out[n++] = p[1];
                    out[n++] = p[2];
                }
            }
        }
        return out;
    }

    /** O quadrado da distância do ponto ({@code x}, {@code y}, {@code z}) à caixa {@code box}; 0 dentro dela. */
    public static double distanceSqr(float[] box, double x, double y, double z) {
        double dx = Math.max(Math.max(box[0] - x, x - box[3]), 0);
        double dy = Math.max(Math.max(box[1] - y, y - box[4]), 0);
        double dz = Math.max(Math.max(box[2] - z, z - box[5]), 0);
        return dx * dx + dy * dy + dz * dz;
    }
}
