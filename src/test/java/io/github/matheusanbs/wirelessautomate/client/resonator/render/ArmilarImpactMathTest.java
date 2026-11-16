package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** As brasas do impacto contra os números do filme ({@code partes.js}, rodado em Node). */
class ArmilarImpactMathTest {
    @Test
    void sorteioDoFilme() {
        assertEquals(0.211321159, ArmilarEmbers.impactR1(0), 1e-9);
        assertEquals(0.791346058, ArmilarEmbers.impactR2(0), 1e-9);
        assertEquals(0.330932785, ArmilarEmbers.impactR1(3), 1e-9);
        assertEquals(0.0717497752, ArmilarEmbers.impactR2(3), 1e-9);
        assertEquals(0.809379287, ArmilarEmbers.impactR1(15), 1e-9);
        assertEquals(0.193364645, ArmilarEmbers.impactR2(15), 1e-9);
    }

    @Test
    void brasaViva() {
        double[] out = new double[5];
        // brasa 0: t0 = 0,6328302898 e vida = 1,1539422404; em d = 0,9 a idade é 0,2671697102
        assertTrue(ArmilarEmbers.impact(0, 0.9, out));
        assertArrayEquals(new double[] {2.49701198, 2.24190915, -1.65772699, 0.231527802, 0}, out, 1e-6);
        // brasa 3 em d = 1: já no segundo tamanho (fv entre 0,33 e 0,66)
        assertTrue(ArmilarEmbers.impact(3, 1.0, out));
        assertArrayEquals(new double[] {1.99030647, 0.972845457, -0.640237228, 0.518692584, 1}, out, 1e-6);
        // brasa 15 em d = 0,9
        assertTrue(ArmilarEmbers.impact(15, 0.9, out));
        assertArrayEquals(new double[] {1.13837685, 1.35650559, 0.585840553, 0.159997740, 0}, out, 1e-6);
    }

    @Test
    void soViveNaJanela() {
        double[] out = new double[5];
        double t0 = 0.58 + ArmilarEmbers.impactR1(0) * 0.25;
        double vida = 0.6 + ArmilarEmbers.impactR2(0) * 0.7;
        assertFalse(ArmilarEmbers.impact(0, 0.6, out));
        assertFalse(ArmilarEmbers.impact(0, t0 - 0.001, out));
        assertTrue(ArmilarEmbers.impact(0, t0 + 0.001, out));
        assertTrue(ArmilarEmbers.impact(0, t0 + vida - 0.001, out));
        assertFalse(ArmilarEmbers.impact(0, t0 + vida + 0.001, out));
        assertFalse(ArmilarEmbers.impact(0, 2.0, out));
    }

    @Test
    void naFronteiraNasceNoPonto() {
        double[] out = new double[5];
        double t0 = 0.58 + ArmilarEmbers.impactR1(0) * 0.25;
        assertTrue(ArmilarEmbers.impact(0, t0, out));
        // idade 0: dist 0, só o 0,5 do deslocamento em x; fv 0
        assertArrayEquals(new double[] {0.5, 0, 0, 0, 0}, out, 1e-12);
    }
}
