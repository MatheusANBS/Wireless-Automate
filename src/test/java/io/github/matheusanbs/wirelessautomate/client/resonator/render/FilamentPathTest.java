package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Os 6 controles do filamento de um cristal ao sol: o {@code // filamentos} do filme. */
class FilamentPathTest {
    private static final double[] CRISTAL = {-14.374379, 40.547018, 12.259008};
    private static final double[] SOL = {0, 47.5, 0};

    private static float[] caminho(double t, double u) {
        float[] ctl = new float[18];
        ArmilarFilaments.path(CRISTAL, SOL, t, u, 1, 2, ctl);
        return ctl;
    }

    @Test
    void comecaNoCristalETerminaNoSol() {
        float[] ctl = caminho(3.3, 0);
        for (int j = 0; j < 3; j++) {
            assertEquals(CRISTAL[j], ctl[j], 1e-4, "início " + j);
            assertEquals(SOL[j], ctl[15 + j], 1e-4, "fim " + j);
        }
    }

    @Test
    void meioSeAfastaDaRetaNoMaximoDaAmplitude() {
        double u = 0;
        double amp = 0.4 + 1.5 * (1 - u);
        assertEquals(1.9, amp, 1e-12);
        float[] ctl = caminho(3.3, u);
        double[] d = {SOL[0] - CRISTAL[0], SOL[1] - CRISTAL[1], SOL[2] - CRISTAL[2]};
        double dl = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        // o lado do filme: normalize(cross(d, (0,2; 1; 0,3))); o alto: normalize(cross(lado, d))
        double[] side = unit(cross(d, new double[] {0.2, 1, 0.3}));
        double[] up = unit(cross(side, d));
        boolean saiu = false;
        for (int j = 1; j <= 4; j++) {
            double f = j / 5.0;
            double[] dev = new double[3];
            for (int k = 0; k < 3; k++) {
                dev[k] = ctl[j * 3 + k] - (CRISTAL[k] + d[k] * f);
            }
            double lateral = dot(dev, side);
            double alto = dot(dev, up);
            double wv = Math.sin(f * Math.PI) * amp;
            assertTrue(Math.abs(lateral) <= wv + 1e-4, "lateral " + j);
            assertTrue(Math.abs(alto) <= 0.5 * wv + 1e-4, "alto " + j);
            // nem para a frente nem para trás: o desvio é perpendicular à reta
            assertEquals(0, dot(dev, d) / dl, 1e-4, "ao longo " + j);
            saiu |= Math.abs(lateral) + Math.abs(alto) > 1e-3;
        }
        assertTrue(saiu, "o filamento ondula fora da sintonia");
    }

    @Test
    void emSintoniaOndulaMenos() {
        float[] solto = caminho(3.3, 0);
        float[] firme = caminho(3.3, 1);
        double[] d = {SOL[0] - CRISTAL[0], SOL[1] - CRISTAL[1], SOL[2] - CRISTAL[2]};
        double maxSolto = 0;
        double maxFirme = 0;
        for (int j = 1; j <= 4; j++) {
            double f = j / 5.0;
            double ds = 0;
            double df = 0;
            for (int k = 0; k < 3; k++) {
                double base = CRISTAL[k] + d[k] * f;
                ds += (solto[j * 3 + k] - base) * (solto[j * 3 + k] - base);
                df += (firme[j * 3 + k] - base) * (firme[j * 3 + k] - base);
            }
            maxSolto = Math.max(maxSolto, Math.sqrt(ds));
            maxFirme = Math.max(maxFirme, Math.sqrt(df));
        }
        assertTrue(maxFirme < maxSolto);
        assertTrue(maxFirme <= 0.4 * 1.5 + 1e-4, "amp 0,4 em sintonia");
    }

    @Test
    void cristalSobreOSolNaoViraNaN() {
        float[] ctl = new float[18];
        ArmilarFilaments.path(SOL, SOL, 1.0, 0, 0, 0, ctl);
        for (float v : ctl) {
            assertFalse(Float.isNaN(v) || Float.isInfinite(v));
        }
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] unit(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return new double[] {a[0] / l, a[1] / l, a[2] / l};
    }
}
