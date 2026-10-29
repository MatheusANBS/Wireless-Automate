package io.github.matheusanbs.wirelessautomate.client.tv.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

class TvPixelRunsTest {
    @Test
    void faixasDaMesmaCorPorLinhaSemATransparente() {
        byte[] pixels = new byte[] {
                1, 1, 0, 2,
                0, 0, 0, 0,
                3, 3, 3, 3};
        int[] runs = TvPixelRuns.of(4, 3, pixels);
        assertEquals(3, runs.length);
        assertRun(runs[0], 0, 0, 2, 1);
        assertRun(runs[1], 0, 3, 4, 2);
        assertRun(runs[2], 2, 0, 4, 3);
    }

    @Test
    void ladoDe64CabeNaFaixa() {
        byte[] pixels = new byte[64 * 64];
        java.util.Arrays.fill(pixels, (byte) 15);
        int[] runs = TvPixelRuns.of(64, 64, pixels);
        assertEquals(64, runs.length);
        assertRun(runs[63], 63, 0, 64, 15);
    }

    @Test
    void asFaixasCobremCadaPixelPintadoUmaVez() {
        Random random = new Random(7);
        int w = 37;
        int h = 23;
        byte[] pixels = new byte[w * h];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) (random.nextInt(3) == 0 ? 0 : random.nextInt(4));
        }
        byte[] painted = new byte[w * h];
        for (int run : TvPixelRuns.of(w, h, pixels)) {
            for (int x = TvPixelRuns.x0(run); x < TvPixelRuns.x1(run); x++) {
                int at = TvPixelRuns.y(run) * w + x;
                assertEquals(0, painted[at], "pixel pintado duas vezes");
                painted[at] = (byte) TvPixelRuns.index(run);
            }
        }
        for (int i = 0; i < pixels.length; i++) {
            assertEquals(pixels[i], painted[i]);
        }
    }

    private static void assertRun(int run, int y, int x0, int x1, int index) {
        assertEquals(y, TvPixelRuns.y(run));
        assertEquals(x0, TvPixelRuns.x0(run));
        assertEquals(x1, TvPixelRuns.x1(run));
        assertEquals(index, TvPixelRuns.index(run));
    }
}
