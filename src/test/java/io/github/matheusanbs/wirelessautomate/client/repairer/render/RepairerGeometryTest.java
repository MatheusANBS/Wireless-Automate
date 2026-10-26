package io.github.matheusanbs.wirelessautomate.client.repairer.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepairerGeometryTest {
    private static final float EPS = 1e-4f;

    @Test
    void quartoZeroDevolveAsTabelas() {
        assertEquals(11.69552f, RepairerGeometry.pedestalX(0, 0), EPS);
        assertEquals(9.53073f, RepairerGeometry.pedestalZ(0, 0), EPS);
        assertEquals(13.2f, RepairerGeometry.runeX(0, 0), EPS);
        assertEquals(8f, RepairerGeometry.runeZ(0, 0), EPS);
    }

    @Test
    void umQuartoGiraOPedestal() {
        assertEquals(6.46927f, RepairerGeometry.pedestalX(1, 0), EPS);
        assertEquals(11.69552f, RepairerGeometry.pedestalZ(1, 0), EPS);
    }

    @Test
    void quatroQuartosVoltamAoOriginal() {
        float[] out = new float[2];
        RepairerGeometry.rotate(11.69552f, 9.53073f, 4, out);
        assertEquals(11.69552f, out[0], EPS);
        assertEquals(9.53073f, out[1], EPS);
        assertEquals(RepairerGeometry.dotX(0, 5), RepairerGeometry.dotX(4, 5), EPS);
    }

    @Test
    void quartoPelaDirecao2D() {
        assertEquals(0, RepairerGeometry.quarterFrom2D(2));
        assertEquals(1, RepairerGeometry.quarterFrom2D(3));
        assertEquals(2, RepairerGeometry.quarterFrom2D(0));
        assertEquals(3, RepairerGeometry.quarterFrom2D(1));
    }

    @Test
    void distanciasAngulares() {
        assertEquals(Math.toRadians(22.5), RepairerGeometry.runeDistance(0, 0), 1e-9);
        assertEquals(Math.toRadians(157.5), RepairerGeometry.runeDistance(4, 0), 1e-9);
        assertEquals(0, RepairerGeometry.dotDistance(3, 3), 1e-9);
    }
}
