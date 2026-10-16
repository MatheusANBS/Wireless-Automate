package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LissajousCurveTest {
    @Test
    void emSintoniaAFiguraFecha() {
        for (double u = 0; u < 7; u += 0.37) {
            assertArrayEquals(LissajousCurve.point(u, 1.0, 12.0), LissajousCurve.point(u + 2 * Math.PI, 1.0, 12.0), 1e-9);
        }
    }

    @Test
    void foraDaSintoniaAFiguraEscorrega() {
        double[] a = LissajousCurve.point(1.0, 0.0, 12.0);
        double[] b = LissajousCurve.point(1.0 + 2 * Math.PI, 0.0, 12.0);
        assertNotEquals(a[0], b[0], 1e-6);
    }

    @Test
    void ficaDentroDaCamara() {
        double[] xyz = new double[LissajousCurve.POINTS * 3];
        LissajousCurve.fill(xyz, 5.0, 0.4, 20.0);
        for (int i = 0; i < LissajousCurve.POINTS; i++) {
            assertTrue(Math.abs(xyz[i * 3]) <= 0.78 + 1e-9 && Math.abs(xyz[i * 3 + 1]) <= 0.55 + 1e-9
                    && Math.abs(xyz[i * 3 + 2]) <= 0.78 + 1e-9);
        }
        assertEquals(LissajousCurve.point(5.0, 0.4, 20.0)[0], xyz[(LissajousCurve.POINTS - 1) * 3], 1e-9);
    }

    @Test
    void avancaMaisRapidoEmSintonia() {
        assertTrue(LissajousCurve.advance(1.0) > LissajousCurve.advance(0.0));
        assertEquals(2 * Math.PI / 3, LissajousCurve.advance(1.0), 1e-9);
    }
}
