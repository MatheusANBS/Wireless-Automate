package io.github.matheusanbs.wirelessautomate.client.tv.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvBudgetTest {
    private static final int GL = 16384;
    private static final long MB = 1024L * 1024;

    @Test
    void projecao() {
        double fov90 = TvBudget.screenPixelsPerBlock(1000, 10, 90, 1);
        assertEquals(50, fov90, 1e-9);
        assertEquals(25, TvBudget.screenPixelsPerBlock(1000, 20, 90, 1), 1e-9);
        assertEquals(25, TvBudget.screenPixelsPerBlock(1000, 10, 90, -0.5), 1e-9);
        assertEquals(12.5, TvBudget.screenPixelsPerBlock(1000, 10, 90, 0.1), 1e-9);
        assertTrue(TvBudget.screenPixelsPerBlock(1000, 0, 70, 1) < Double.POSITIVE_INFINITY);
    }

    @Test
    void potenciaDe2() {
        assertEquals(1, TvBudget.ceilPow2(0.3));
        assertEquals(2, TvBudget.ceilPow2(1.5));
        assertEquals(2, TvBudget.ceilPow2(2));
        assertEquals(4, TvBudget.ceilPow2(2.01));
        assertEquals(256, TvBudget.ceilPow2(200));
    }

    @Test
    void desejadoEntrePisoETeto() {
        assertEquals(32, TvBudget.desired(5, 32, 1, 1, GL));
        assertEquals(128, TvBudget.desired(60, 32, 1, 1, GL));
        assertEquals(512, TvBudget.desired(2000, 32, 1, 1, GL));
        assertEquals(64, TvBudget.desired(60, 16, 1, 1, GL));
        assertEquals(1024, TvBudget.desired(2000, 64, 1, 1, GL));
        assertEquals(16, TvBudget.desired(5, 16, 1, 1, GL));
    }

    @Test
    void tetoPeloMaiorLado() {
        assertEquals(128, TvBudget.desired(2000, 32, 32, 4, GL));
        assertEquals(64, TvBudget.desired(2000, 64, 64, 64, GL));
        assertEquals(4, TvBudget.desired(5, 32, 1024, 1, GL));
        assertEquals(256, TvBudget.desired(2000, 32, 8, 1, 2048));
    }

    @Test
    void histerese() {
        assertEquals(64, TvBudget.target(0, 30, 32, 1, 1, GL));
        assertEquals(64, TvBudget.target(64, 30, 32, 1, 1, GL));
        assertEquals(64, TvBudget.target(64, 42, 32, 1, 1, GL));
        assertEquals(128, TvBudget.target(64, 43, 32, 1, 1, GL));
        assertEquals(128, TvBudget.target(128, 31, 32, 1, 1, GL));
        assertEquals(64, TvBudget.target(128, 30, 32, 1, 1, GL));
        assertEquals(32, TvBudget.target(128, 15, 32, 1, 1, GL));
        assertEquals(64, TvBudget.target(64, 16, 32, 1, 1, GL));
        assertEquals(256, TvBudget.target(512, 300, 32, 16, 16, GL), "o teto pelo tamanho ganha da histerese");
    }

    @Test
    void semPiscaAoAndar() {
        int small = TvBudget.target(0, 40, 32, 2, 2, GL);
        for (double screen = 16; screen <= 42.5; screen += 0.25) {
            assertEquals(small, TvBudget.target(small, screen, 32, 2, 2, GL));
        }
        int big = TvBudget.target(small, 43, 32, 2, 2, GL);
        assertEquals(2 * small, big);
        for (double screen = 31; screen <= 85; screen += 0.25) {
            assertEquals(big, TvBudget.target(big, screen, 32, 2, 2, GL));
        }
    }

    @Test
    void bytesComMipmaps() {
        assertEquals(256L * 256 * 4 * 4 / 3, TvBudget.bytes(1, 1, 256));
        assertEquals(4096L * 4096 * 16 / 3, TvBudget.bytes(32, 32, 128));
    }

    @Test
    void orcamentoCortaQuemMaisGastaPorPixelVisto() {
        int[] w = {4, 4};
        int[] h = {4, 4};
        double[] screen = {100, 10};
        int[] ppb = {256, 256};
        long budget = TvBudget.bytes(4, 4, 256) + TvBudget.bytes(4, 4, 64);
        TvBudget.fit(2, w, h, screen, ppb, budget);
        assertArrayEquals(new int[] {256, 64}, ppb);
    }

    @Test
    void orcamentoParaNoPiso() {
        int[] w = {64};
        int[] h = {64};
        int[] ppb = {64};
        TvBudget.fit(1, w, h, new double[] {1}, ppb, 1);
        assertEquals(TvBudget.FLOOR_PPB, ppb[0]);
    }

    @Test
    void cabeNoOrcamento() {
        int n = 30;
        int[] w = new int[n];
        int[] h = new int[n];
        double[] screen = new double[n];
        int[] ppb = new int[n];
        for (int i = 0; i < n; i++) {
            w[i] = 1 + i % 8;
            h[i] = 1 + i % 5;
            screen[i] = 5 + i * 7;
            ppb[i] = TvBudget.desired(screen[i], 32, w[i], h[i], GL);
        }
        long budget = 400 * MB;
        TvBudget.fit(n, w, h, screen, ppb, budget);
        long total = 0;
        for (int i = 0; i < n; i++) {
            total += TvBudget.bytes(w[i], h[i], ppb[i]);
        }
        assertTrue(total <= budget, "total " + total);
    }

    @Test
    void escolheReducoesPrimeiroEAteDois() {
        int[] w = {1, 1, 1, 1};
        int[] h = {1, 1, 1, 1};
        int[] current = {64, 512, 256, 64};
        int[] fitted = {128, 256, 128, 256};
        int[] out = new int[TvBudget.MAX_RESIZES];
        long allocated = 0;
        for (int i = 0; i < 4; i++) {
            allocated += TvBudget.bytes(1, 1, current[i]);
        }
        int n = TvBudget.choose(4, w, h, current, fitted, allocated, Long.MAX_VALUE, out);
        assertEquals(2, n);
        assertEquals(1, out[0]);
        assertEquals(2, out[1]);
    }

    @Test
    void aumentoSoSeCabe() {
        int[] w = {1, 1};
        int[] h = {1, 1};
        int[] current = {64, 64};
        int[] fitted = {256, 128};
        int[] out = new int[TvBudget.MAX_RESIZES];
        long allocated = 2 * TvBudget.bytes(1, 1, 64);
        long budget = allocated + TvBudget.bytes(1, 1, 128) - TvBudget.bytes(1, 1, 64);
        int n = TvBudget.choose(2, w, h, current, fitted, allocated, budget, out);
        assertEquals(1, n);
        assertEquals(1, out[0]);
        assertEquals(0, TvBudget.choose(2, w, h, current, current, allocated, budget, out));
    }
}
