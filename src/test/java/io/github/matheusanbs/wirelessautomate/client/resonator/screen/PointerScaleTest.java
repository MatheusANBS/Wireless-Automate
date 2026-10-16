package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
import org.junit.jupiter.api.Test;

class PointerScaleTest {
    @Test
    void escalaDeMenosDoisAMaisDoisNaLargura() {
        assertEquals(35.0, PointerScale.pixelsPerMhz(172), 1e-9);
        assertEquals(86, PointerScale.x(172, 0));
        assertEquals(16, PointerScale.x(172, -2));
        assertEquals(156, PointerScale.x(172, 2));
        assertArrayEquals(new int[] {77, 95}, PointerScale.plateau(172));
    }

    @Test
    void ponteiroMostraOndeODialEsta() {
        assertEquals(-1.3, PointerScale.pointerMhz(26), 1e-9);
        assertEquals(0.0, PointerScale.pointerMhz(0), 1e-9);
        assertEquals(2.0, PointerScale.pointerMhz(TuneSignal.NO_SIGNAL), 1e-9);
    }

    @Test
    void tracosACadaMeioEROtulosACadaUm() {
        assertTrue(PointerScale.isTick(-1.5));
        assertFalse(PointerScale.isTick(0.3));
        assertTrue(PointerScale.isMajor(1.0));
        assertFalse(PointerScale.isMajor(0.5));
    }
}
