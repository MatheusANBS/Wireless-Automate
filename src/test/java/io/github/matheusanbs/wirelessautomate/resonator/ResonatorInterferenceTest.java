package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResonatorInterferenceTest {
    @Test
    void distanciaDeChebyshev() {
        assertEquals(24, ResonatorInterference.distance(0, 64, 0, 24, 70, -10));
        assertEquals(5, ResonatorInterference.distance(-3, 0, 0, 2, 0, 0));
        assertEquals(0, ResonatorInterference.distance(1, 2, 3, 1, 2, 3));
    }

    @Test
    void raioZeroDesliga() {
        assertFalse(ResonatorInterference.counts(0, 0));
        assertTrue(ResonatorInterference.counts(32, 32));
        assertFalse(ResonatorInterference.counts(33, 32));
    }

    @Test
    void perdaPorVizinhoComTeto() {
        assertEquals(0, ResonatorInterference.lossPermille(0, 0.15, 0.6));
        assertEquals(150, ResonatorInterference.lossPermille(1, 0.15, 0.6));
        assertEquals(450, ResonatorInterference.lossPermille(3, 0.15, 0.6));
        assertEquals(600, ResonatorInterference.lossPermille(4, 0.15, 0.6));
        assertEquals(600, ResonatorInterference.lossPermille(40, 0.15, 0.6));
        assertEquals(1000, ResonatorInterference.lossPermille(2, 1, 1));
        assertEquals(0, ResonatorInterference.lossPermille(3, 0, 0.6));
    }

    @Test
    void fatorDaPerda() {
        assertEquals(1.0, ResonatorInterference.factor(0));
        assertEquals(0.85, ResonatorInterference.factor(150), 1e-9);
        assertEquals(0.4, ResonatorInterference.factor(600), 1e-9);
        assertEquals(0.0, ResonatorInterference.factor(2000));
    }

    @Test
    void somaContaSoODentroDoRaioEOMaisPerto() {
        ResonatorInterference.Tally tally = new ResonatorInterference.Tally(32);
        tally.add(40);
        assertEquals(0, tally.count());
        assertEquals(ResonatorInterference.NONE, tally.nearest());
        tally.add(30);
        tally.add(24);
        tally.add(32);
        assertEquals(3, tally.count());
        assertEquals(24, tally.nearest());
    }

    @Test
    void somaComRaioZeroNaoConta() {
        ResonatorInterference.Tally tally = new ResonatorInterference.Tally(0);
        tally.add(0);
        tally.add(5);
        assertEquals(0, tally.count());
        assertEquals(ResonatorInterference.NONE, tally.nearest());
    }
}
