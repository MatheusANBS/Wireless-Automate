package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChamberRingTest {
    @Test
    void oAnelFicaFixoEEspalhado() {
        double[] a = ChamberRing.position(0, 4, new double[3]);
        double[] b = ChamberRing.position(2, 4, new double[3]);
        assertEquals(-a[0], b[0], 1e-12);
        assertEquals(-a[2], b[2], 1e-12);
        assertEquals(ChamberRing.HEIGHT, a[1]);
        assertEquals(ChamberRing.RADIUS, Math.hypot(a[0], a[2]), 1e-12);
    }

    @Test
    void oTempoÉODoGameTime() {
        assertEquals(0.5, ChamberRing.seconds(10, 0f), 1e-12);
        assertEquals(0.025, ChamberRing.seconds(24000, 0.5f), 1e-12);
    }
}
