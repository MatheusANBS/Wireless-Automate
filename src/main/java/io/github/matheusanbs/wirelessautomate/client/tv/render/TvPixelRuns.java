package io.github.matheusanbs.wirelessautomate.client.tv.render;

/**
 * As faixas horizontais de uma pixel art (pura, com JUnit): cada sequência de pixels vizinhos na mesma linha com o
 * mesmo índice vira uma faixa {@code (y, x0, x1, índice)}, com {@code x1} exclusivo, e o desenho pinta um retângulo
 * por faixa, nunca um por pixel. O índice 0 (transparente) não gera faixa. Cada faixa é um {@code int}: o índice nos
 * bits 0 a 3, {@code x1} nos 4 a 10, {@code x0} nos 11 a 16 e {@code y} nos 17 a 22 (lados até 64).
 */
public final class TvPixelRuns {
    private static final int INDEX_MASK = 0x0F;
    private static final int X1_SHIFT = 4;
    private static final int X0_SHIFT = 11;
    private static final int Y_SHIFT = 17;
    private static final int X1_MASK = 0x7F;
    private static final int SIDE_MASK = 0x3F;

    private TvPixelRuns() {
    }

    /** As faixas da arte {@code width} × {@code height}, em ordem de linha e de coluna. */
    public static int[] of(int width, int height, byte[] pixels) {
        int count = 0;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int index = pixels[row + x];
                if (index != 0 && (x == 0 || pixels[row + x - 1] != index)) {
                    count++;
                }
            }
        }
        int[] runs = new int[count];
        int at = 0;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int x = 0;
            while (x < width) {
                int index = pixels[row + x];
                int start = x;
                while (x < width && pixels[row + x] == index) {
                    x++;
                }
                if (index != 0) {
                    runs[at++] = y << Y_SHIFT | start << X0_SHIFT | x << X1_SHIFT | index;
                }
            }
        }
        return runs;
    }

    public static int y(int run) {
        return run >>> Y_SHIFT & SIDE_MASK;
    }

    public static int x0(int run) {
        return run >>> X0_SHIFT & SIDE_MASK;
    }

    /** O fim da faixa, exclusivo. */
    public static int x1(int run) {
        return run >>> X1_SHIFT & X1_MASK;
    }

    public static int index(int run) {
        return run & INDEX_MASK;
    }
}
