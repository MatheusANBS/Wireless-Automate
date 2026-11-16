package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.fx.FxRibbon;
import org.junit.jupiter.api.Test;

/** O arco da conjunção contra o {@code // o arco:} do filme ({@code partes.js}, rodado em Node). */
class ArmilarArcTest {
    private static void ponto(FxRibbon.Buffer b, int i, double x, double y, double z) {
        assertEquals(x, b.pts[i * 3], 1e-4, "x " + i);
        assertEquals(y, b.pts[i * 3 + 1], 1e-4, "y " + i);
        assertEquals(z, b.pts[i * 3 + 2], 1e-4, "z " + i);
    }

    @Test
    void pontasNaoOndulam() {
        for (double t : new double[] {0, 3.7, 11, 5000.123}) {
            FxRibbon.Buffer b = new FxRibbon.Buffer();
            assertEquals(61, ArmilarArc.fill(t, b));
            ponto(b, 0, 33.6, 46.6, 0);
            ponto(b, 60, ArmilarSpec.PORTA_ARCO_FIM[0], 8.6, 0);
        }
    }

    @Test
    void fimDoArcoVemDaEspec() {
        FxRibbon.Buffer b = new FxRibbon.Buffer();
        assertEquals(61, ArmilarArc.fill(2, b));
        ponto(b, 0, 33.6, 46.6, 0);
        // 42,8 do filme mais o deslocamento da Porta do jogo
        ponto(b, 60, 42.8 + ArmilarSpec.PORTA_DESLOC_X, 8.6, 0);
    }

    @Test
    void meioDoArcoComOndulacao() {
        FxRibbon.Buffer b = new FxRibbon.Buffer();
        ArmilarArc.fill(5, b);
        // o ponto 30 em t = 5 (filme em Node, com o fim do arco em 42,8); o fim deslocado puxa x em 1/8 do deslocamento
        ponto(b, 30, 44.9123643 + 0.125 * ArmilarSpec.PORTA_DESLOC_X, 29.9263605, -0.375601043);
    }

    @Test
    void passoNoMaximoUmPixel() {
        for (double t : new double[] {0, 1.3, 5, 11.4}) {
            FxRibbon.Buffer b = new FxRibbon.Buffer();
            ArmilarArc.fill(t, b);
            double maior = 0;
            for (int i = 1; i < 61; i++) {
                double dx = b.pts[i * 3] - b.pts[i * 3 - 3];
                double dy = b.pts[i * 3 + 1] - b.pts[i * 3 - 2];
                double dz = b.pts[i * 3 + 2] - b.pts[i * 3 - 1];
                maior = Math.max(maior, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            assertTrue(maior <= 1.0, "passo " + maior);
        }
    }

    @Test
    void caudaApagaAtrasDaCabeca() {
        double h = 0.6;
        assertEquals(1, ArmilarArc.tail(h, 0.3), 1e-12);
        assertEquals(1, ArmilarArc.tail(h, h - 0.05), 1e-12);
        assertEquals(0, ArmilarArc.tail(h, h), 1e-12);
        assertEquals(0, ArmilarArc.tail(h, 0.9), 1e-12);
        // o meio da rampa vale 0,5 (ss em 0,5)
        assertEquals(0.5, ArmilarArc.tail(h, h - 0.025), 1e-9);
        double anterior = 1;
        for (int i = 0; i <= 10; i++) {
            double v = ArmilarArc.tail(h, h - 0.05 + 0.005 * i);
            assertTrue(v <= anterior + 1e-12);
            anterior = v;
        }
    }
}
