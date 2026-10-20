package io.github.matheusanbs.wirelessautomate.client.tablet;

import java.util.Arrays;

/**
 * Os pixels das linhas tracejadas do Mapa do Tablet, do roteador até cada máquina dele: a reta de pixels (Bresenham)
 * em traços de {@link #DASH} pixels e vãos de {@link #GAP}, só os pixels dentro do retângulo dado (a reta é cortada
 * nele antes, para o zoom alto não percorrer milhares de pixels fora do mapa). Montados quando
 * os pontos mudam, nunca por quadro; o desenho só percorre {@link #size} pares por índice. Os arrays crescem e são
 * reaproveitados entre as montagens. Lógica pura, com JUnit.
 */
final class MapLines {
    static final int DASH = 2;
    static final int GAP = 3;

    private int[] xs = new int[256];
    private int[] ys = new int[256];
    private int size;

    void clear() {
        size = 0;
    }

    int size() {
        return size;
    }

    int x(int i) {
        return xs[i];
    }

    int y(int i) {
        return ys[i];
    }

    /**
     * Junta os pixels tracejados da reta de ({@code x0}, {@code y0}) a ({@code x1}, {@code y1}), o primeiro pixel
     * num traço, guardando só os que caem em [{@code minX}, {@code maxX}] × [{@code minY}, {@code maxY}].
     */
    void dashed(int x0, int y0, int x1, int y1, int minX, int minY, int maxX, int maxY) {
        // Liang-Barsky: o trecho [t0, t1] da reta que fica no retângulo
        double t0 = 0;
        double t1 = 1;
        int ddx = x1 - x0;
        int ddy = y1 - y0;
        double[] p = {-ddx, ddx, -ddy, ddy};
        double[] q = {x0 - minX, maxX - x0, y0 - minY, maxY - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) {
                    return;
                }
            } else {
                double t = q[i] / p[i];
                if (p[i] < 0) {
                    t0 = Math.max(t0, t);
                } else {
                    t1 = Math.min(t1, t);
                }
            }
        }
        if (t0 > t1) {
            return;
        }
        int ax = (int) Math.round(x0 + t0 * ddx);
        int ay = (int) Math.round(y0 + t0 * ddy);
        int bx = (int) Math.round(x0 + t1 * ddx);
        int by = (int) Math.round(y0 + t1 * ddy);
        // o tracejado conta do roteador: o passo inicial é a distância de Chebyshev já cortada
        int skipped = Math.max(Math.abs(ax - x0), Math.abs(ay - y0));
        walk(ax, ay, bx, by, skipped, minX, minY, maxX, maxY);
    }

    private void walk(int x0, int y0, int x1, int y1, int firstStep, int minX, int minY, int maxX, int maxY) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        for (int step = firstStep; ; step++) {
            if (step % (DASH + GAP) < DASH && x >= minX && x <= maxX && y >= minY && y <= maxY) {
                add(x, y);
            }
            if (x == x1 && y == y1) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private void add(int x, int y) {
        if (size == xs.length) {
            xs = Arrays.copyOf(xs, size * 2);
            ys = Arrays.copyOf(ys, size * 2);
        }
        xs[size] = x;
        ys[size] = y;
        size++;
    }
}
