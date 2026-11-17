package io.github.matheusanbs.wirelessautomate.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FxRibbonTest {
    private static double dist(FxRibbon.Buffer b, int i, int j) {
        double dx = b.pts[i * 3] - b.pts[j * 3];
        double dy = b.pts[i * 3 + 1] - b.pts[j * 3 + 1];
        double dz = b.pts[i * 3 + 2] - b.pts[j * 3 + 2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test
    void amostraACadaUmDezesseisAvos() {
        // controles em px: uma curva de uns 40 px; o passo é 1 px = 1/16 de bloco
        float[] ctl = {0, 0, 0, 10, 8, 3, 22, 12, -2, 34, 4, 5, 40, 0, 0};
        FxRibbon.Buffer b = new FxRibbon.Buffer();
        int n = FxRibbon.catmullRom(ctl, 5, 1f, b);
        assertTrue(n > 40, "amostras: " + n);
        for (int i = 1; i < n; i++) {
            assertTrue(dist(b, i, i - 1) <= 1.0 + 1e-4, "passo " + i + ": " + dist(b, i, i - 1));
        }
    }

    @Test
    void passaPelosControlesENasPontas() {
        float[] ctl = {0, 0, 0, 10, 8, 3, 22, 12, -2};
        FxRibbon.Buffer b = new FxRibbon.Buffer();
        int n = FxRibbon.catmullRom(ctl, 3, 1f, b);
        assertEquals(0f, b.pts[0], 1e-6);
        assertEquals(22f, b.pts[(n - 1) * 3], 1e-6);
        // o controle do meio é amostra da curva
        boolean achou = false;
        for (int i = 0; i < n; i++) {
            achou |= Math.abs(b.pts[i * 3] - 10) < 1e-4 && Math.abs(b.pts[i * 3 + 1] - 8) < 1e-4;
        }
        assertTrue(achou);
    }

    @Test
    void doisPontosViramReta() {
        float[] ctl = {0, 0, 0, 5, 0, 0};
        FxRibbon.Buffer b = new FxRibbon.Buffer();
        int n = FxRibbon.catmullRom(ctl, 2, 1f, b);
        assertEquals(2, n);
    }

    @Test
    void bezierDoArcoDoFilme() {
        // os 4 pontos do arco da conjunção (partes.js, desenhar), em px
        float[] p = {33.6f, 46.6f, 0, 46, 44, 0, 49, 17, 0, 42.8f, 8.6f, 0};
        FxRibbon.Buffer b = new FxRibbon.Buffer();
        int n = FxRibbon.bezier(p, 60, b);
        assertEquals(61, n);
        assertEquals(33.6f, b.pts[0], 1e-5);
        assertEquals(42.8f, b.pts[60 * 3], 1e-5);
        for (int i = 1; i < n; i++) {
            assertTrue(dist(b, i, i - 1) <= 1.0 + 1e-4, "passo " + i);
        }
    }
}
