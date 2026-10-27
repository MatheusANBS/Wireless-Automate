package io.github.matheusanbs.wirelessautomate.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateWindowTest {
    @Test
    void semDoisInstantaneosATaxaEZero() {
        RateWindow w = new RateWindow();
        assertEquals(0, w.inPerSecond());
        w.sample(0, 100, 50);
        assertEquals(0, w.inPerSecond());
        assertEquals(0, w.outPerSecond());
    }

    @Test
    void aTaxaEADiferencaSobreOTempo() {
        RateWindow w = new RateWindow();
        w.sample(0, 0, 0);
        w.sample(20, 100, 40);
        assertEquals(100, w.inPerSecond(), 1e-9);
        assertEquals(40, w.outPerSecond(), 1e-9);
        w.sample(40, 300, 40);
        assertEquals(150, w.inPerSecond(), 1e-9);
        assertEquals(20, w.outPerSecond(), 1e-9);
    }

    @Test
    void umInstantaneoPorSegundoNoMaximo() {
        RateWindow w = new RateWindow();
        assertTrue(w.sample(0, 0, 0));
        assertFalse(w.sample(19, 999, 999));
        assertTrue(w.sample(20, 20, 0));
        assertEquals(20, w.inPerSecond(), 1e-9);
    }

    @Test
    void aJanelaEDeCincoSegundos() {
        RateWindow w = new RateWindow();
        for (int s = 0; s <= 5; s++) {
            w.sample(s * 20L, s == 0 ? 0 : 1_000, 0);
        }
        assertEquals(200, w.inPerSecond(), 1e-9);
        w.sample(120, 1_000, 0);
        assertEquals(0, w.inPerSecond(), 1e-9);
    }
}
