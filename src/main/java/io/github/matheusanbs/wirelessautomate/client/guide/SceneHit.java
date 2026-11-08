package io.github.matheusanbs.wirelessautomate.client.guide;

/**
 * As contas das anotações das cenas, sem Minecraft: a cor da página, a cor realçada, a distância do mouse a uma linha
 * projetada e o mouse dentro de uma caixa projetada. A caixa projetada são os 8 cantos (x, y) em sequência, o canto
 * {@code c} com o bit 0 em x, o bit 1 em y e o bit 2 em z.
 */
final class SceneHit {
    /** A cor de quem não diz a sua (coral). */
    static final int CORAL = 0xFFF0603E;
    /** As 12 arestas da caixa, pelos índices dos cantos. */
    static final int[] EDGES = {0, 1, 2, 3, 4, 5, 6, 7, 0, 2, 1, 3, 4, 6, 5, 7, 0, 4, 1, 5, 2, 6, 3, 7};
    /** As 6 faces da caixa, cada uma com os 4 cantos em volta. */
    static final int[] FACES = {0, 2, 6, 4, 1, 3, 7, 5, 0, 1, 5, 4, 2, 3, 7, 6, 0, 1, 3, 2, 4, 5, 7, 6};

    private SceneHit() {
    }

    /** {@code #rrggbb} com alfa cheio; vazio ou ilegível dá o coral. */
    static int color(String hex) {
        if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') {
            return CORAL;
        }
        try {
            return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
        } catch (NumberFormatException e) {
            return CORAL;
        }
    }

    /** A cor realçada: um terço do caminho até o branco, com alfa cheio. */
    static int lighter(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return 0xFF000000 | (r + (255 - r) / 3) << 16 | (g + (255 - g) / 3) << 8 | (b + (255 - b) / 3);
    }

    /** A distância do ponto ao segmento de a até b. */
    static double segment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.clamp(((px - ax) * dx + (py - ay) * dy) / len2, 0, 1);
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    /**
     * O ponto dentro da caixa projetada que começa em {@code pts[o]}: dentro de alguma das seis faces, cada uma um
     * quadrilátero convexo (os produtos vetoriais sem sinais opostos). Uma face vista de lado não conta.
     */
    static boolean inBox(float[] pts, int o, double px, double py) {
        for (int f = 0; f < FACES.length; f += 4) {
            int pos = 0;
            int neg = 0;
            for (int e = 0; e < 4; e++) {
                int a = o + FACES[f + e] * 2;
                int b = o + FACES[f + (e + 1) % 4] * 2;
                double cross = (pts[b] - pts[a]) * (py - pts[a + 1]) - (pts[b + 1] - pts[a + 1]) * (px - pts[a]);
                if (cross > 0) {
                    pos++;
                } else if (cross < 0) {
                    neg++;
                }
            }
            if ((pos == 0 || neg == 0) && pos + neg > 0) {
                return true;
            }
        }
        return false;
    }
}
