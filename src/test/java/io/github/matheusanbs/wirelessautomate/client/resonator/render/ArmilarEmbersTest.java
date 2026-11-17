package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** As brasas em órbita contra os números do filme ({@code partes.js}, rodado em Node). */
class ArmilarEmbersTest {
    private static void orbita(int k, double t, double prep, double alfa, double... esperado) {
        double[] out = new double[3];
        ArmilarEmbers.orbit(k, t, prep, out);
        assertArrayEquals(esperado, out, 1e-6, "órbita " + k);
        assertEquals(alfa, ArmilarEmbers.orbitAlpha(k, t), 1e-6, "alfa " + k);
    }

    @Test
    void ouroDoFilme() {
        orbita(0, 0, 0, 0.55, 8.5, 0, 0);
        orbita(5, 3.3, 0, 0.597029601, -6.03735103, -2.12675212, 8.28328996);
        orbita(11, 7.1, 1, 0.893678177, 2.26941044, -0.058632461, 3.08833814);
        orbita(7, 100.5, 0.5, 0.358169151, -6.039774, -1.54541097, 5.03584095);
    }

    @Test
    void preparacaoSugaOsRaios() {
        double[] solta = new double[3];
        double[] presa = new double[3];
        ArmilarEmbers.orbit(3, 2, 0, solta);
        ArmilarEmbers.orbit(3, 2, 1, presa);
        // o raio no plano encolhe a 35%, a altura não muda
        assertEquals(0.35, Math.hypot(presa[0], presa[2]) / Math.hypot(solta[0], solta[2]), 1e-9);
        assertEquals(solta[1], presa[1], 1e-12);
    }
}
