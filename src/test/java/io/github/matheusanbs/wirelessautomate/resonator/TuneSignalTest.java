package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TuneSignalTest {
    @Test
    void desvioQuantizadoSoDentroDeDoisMhz() {
        assertEquals(0, TuneSignal.steps(98.0, 98.0));
        assertEquals(26, TuneSignal.steps(98.0, 96.7));
        assertEquals(-26, TuneSignal.steps(96.7, 98.0));
        assertEquals(39, TuneSignal.steps(98.0, 96.05));
        assertEquals(TuneSignal.NO_SIGNAL, TuneSignal.steps(98.0, 96.0));
        assertEquals(TuneSignal.NO_SIGNAL, TuneSignal.steps(98.0, 101.6));
    }

    @Test
    void faixaDaEficienciaVaiDeZeroASete() {
        assertEquals(0, TuneSignal.band(0.0));
        assertEquals(1, TuneSignal.band(0.01));
        assertEquals(4, TuneSignal.band(0.5));
        assertEquals(7, TuneSignal.band(0.95));
        assertEquals(7, TuneSignal.band(1.0));
        assertEquals(TuneSignal.BANDS - 1, TuneSignal.band(1.0));
    }

    @Test
    void lampadaAcendeNoPlato() {
        assertTrue(TuneSignal.tuned(98.0, 98.25));
        assertTrue(TuneSignal.tuned(98.0, 97.75));
        assertFalse(TuneSignal.tuned(98.0, 98.3));
        assertEquals(TuneSignal.PLATEAU_STEPS, TuneSignal.steps(98.25, 98.0));
    }
}
