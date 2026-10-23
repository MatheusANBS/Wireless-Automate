package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChamberRingTest {
    @Test
    void oAnelFicaFixoEEspalhado() {
        double[] a = ChamberRing.position(0, 4, 123.4, new double[3]);
        double[] b = ChamberRing.position(2, 4, 123.4, new double[3]);
        assertEquals(-a[0], b[0], 1e-12);
        assertEquals(-a[2], b[2], 1e-12);
        assertEquals(ChamberRing.HEIGHT, a[1]);
        assertEquals(ChamberRing.RADIUS, Math.hypot(a[0], a[2]), 1e-12);
    }

    @Test
    void aOrbitaDaUmaVoltaENaoSalta() {
        assertEquals(0, ChamberRing.orbit(0), 1e-12);
        assertEquals(0, ChamberRing.orbit(ChamberRing.ORBIT_TICKS), 1e-12);
        assertEquals(Math.PI, ChamberRing.orbit(ChamberRing.ORBIT_TICKS / 2.0), 1e-12);
        // logo antes da volta, quase 2π; logo depois, quase 0: a mesma posição
        double before = ChamberRing.orbit(5 * ChamberRing.ORBIT_TICKS - 0.01);
        double after = ChamberRing.orbit(5 * ChamberRing.ORBIT_TICKS + 0.01);
        assertEquals(2 * Math.PI, before - after, 1e-3);
    }

    @Test
    void oTempoÉODoGameTime() {
        assertEquals(0.5, ChamberRing.seconds(10, 0f), 1e-12);
        assertEquals(0.025, ChamberRing.seconds(24000, 0.5f), 1e-12);
    }
}
