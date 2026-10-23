package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArcClockTest {
    @Test
    void osArcosCrescemComAFaixa() {
        int[] expected = {0, 0, 0, 1, 2, 3, 3, 3};
        for (int band = 0; band <= 7; band++) {
            assertEquals(expected[band], ArcClock.arcCount(band), "faixa " + band);
        }
    }

    @Test
    void aDescargaSoExisteDaFaixaCincoEmDiante() {
        for (int band = 0; band <= 4; band++) {
            assertEquals(0.0, ArcClock.strikePeriod(band), "faixa " + band);
            assertEquals(-1, ArcClock.strikeIndex(10.0, band));
            assertFalse(ArcClock.striking(10.0, band));
        }
        assertEquals(2.4, ArcClock.strikePeriod(5));
        assertEquals(1.6, ArcClock.strikePeriod(6));
        assertEquals(1.0, ArcClock.strikePeriod(7));
    }

    @Test
    void aDescargaAcendeNoInicioDoPeriodoPorUmaFracao() {
        assertTrue(ArcClock.striking(2.4 * 5, 5));
        assertTrue(ArcClock.striking(2.4 * 5 + 2.4 * 0.11, 5));
        assertFalse(ArcClock.striking(2.4 * 5 + 2.4 * 0.13, 5));
        assertEquals(5, ArcClock.strikeIndex(2.4 * 5 + 0.1, 5));
        assertEquals(6, ArcClock.strikeIndex(2.4 * 6, 5));
    }

    @Test
    void oArcoOciosoCabeNaJanelaENuncaSeSobrepoe() {
        for (boolean full : new boolean[] {false, true}) {
            double length = full ? 2.5 : 4.0;
            for (long window = 0; window < 500; window++) {
                double start = ArcClock.idleStart(window, full);
                double end = start + ArcClock.idleDuration(window);
                assertTrue(start >= window * length, "começa antes da janela " + window);
                assertTrue(end < (window + 1) * length, "passa da janela " + window);
                assertTrue(ArcClock.idleDuration(window) >= 0.15 && ArcClock.idleDuration(window) <= 0.30);
            }
        }
    }

    @Test
    void oProgressoDoArcoOciosoVaiDeZeroAUmSoDentroDaJanelaDele() {
        long window = 7;
        double start = ArcClock.idleStart(window, false);
        double duration = ArcClock.idleDuration(window);
        assertEquals(-1, ArcClock.idleProgress(start - 0.01, false), 1e-9);
        assertEquals(0.0, ArcClock.idleProgress(start, false), 1e-9);
        assertEquals(0.5, ArcClock.idleProgress(start + duration / 2, false), 1e-6);
        assertEquals(-1, ArcClock.idleProgress(start + duration + 0.01, false), 1e-9);
    }

    @Test
    void comOBufferCheioOsArcosOciososSaoMaisFrequentes() {
        int normal = 0;
        int full = 0;
        for (double s = 0; s < 400; s += 0.05) {
            if (ArcClock.idleProgress(s, false) >= 0) {
                normal++;
            }
            if (ArcClock.idleProgress(s, true) >= 0) {
                full++;
            }
        }
        assertTrue(full > normal * 1.3, "cheio " + full + " contra " + normal);
    }

    @Test
    void osSorteiosSaoDeterministicosEFicamEntreZeroEUm() {
        for (long n = -50; n < 500; n++) {
            for (int k = 0; k < 6; k++) {
                double h = ArcClock.hash01(n, k);
                assertTrue(h >= 0 && h < 1, "sorteio " + h);
                assertEquals(h, ArcClock.hash01(n, k));
            }
        }
        assertEquals(ArcClock.idleHash(12.3, false, 0), ArcClock.idleHash(12.9, false, 0), "a mesma janela");
    }

    @Test
    void osSonsTemUmEspacamentoMinimo() {
        assertTrue(ArcClock.canPlay(100, 110));
        assertFalse(ArcClock.canPlay(100, 109));
        assertTrue(ArcClock.canPlay(100, 50), "o relógio voltou");
        int played = 0;
        long last = Long.MIN_VALUE / 2;
        for (long tick = 0; tick < 200; tick++) {
            if (ArcClock.canPlay(last, tick)) {
                played++;
                last = tick;
            }
        }
        assertEquals(20, played, "no máximo 2 por segundo (20 ticks) em 10 s");
    }
}
