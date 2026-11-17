package io.github.matheusanbs.wirelessautomate.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FxLightsTest {
    @AfterEach
    void limpar() {
        FxLights.clear();
    }

    @Test
    void quedaEmDegraus() {
        assertEquals(1, FxLights.step(0, 4), 1e-12);
        assertEquals(0, FxLights.step(4, 4), 1e-12);
        assertEquals(0, FxLights.step(5, 4), 1e-12);
        assertEquals(0.2, FxLights.step(2, 4), 1e-12);
        double prev = 2;
        for (double d = 0; d <= 4.5; d += 0.01) {
            double v = FxLights.step(d, 4);
            assertTrue(v <= prev + 1e-12, "monótona em " + d);
            assertEquals(0, v * 5 - Math.rint(v * 5), 1e-9, "degrau de 0,2 em " + d);
            prev = v;
        }
    }

    @Test
    void tetoSuave() {
        assertEquals(1, FxLights.ceilDivisor(0.5), 1e-12);
        assertEquals(1, FxLights.ceilDivisor(1), 1e-12);
        assertEquals(2.2, FxLights.ceilDivisor(2), 1e-12);
        assertTrue(FxLights.ceilDivisor(3) > FxLights.ceilDivisor(2));
    }

    @Test
    void fillPegaAsTrintaEDuasMaisProximas() {
        // embaralha a ordem de entrada: a luz i está a (i * 7 % 40) blocos da câmera
        for (int i = 0; i < 40; i++) {
            FxLights.add(i * 7 % 40, 0, 0, 1, 0.5f, 0.25f, 1 + i, 3);
        }
        assertEquals(40, FxLights.count());
        float[] pos = new float[128];
        float[] col = new float[128];
        int n = FxLights.fill(0, 0, 0, pos, col);
        assertEquals(32, n);
        for (int o = 0; o < n; o++) {
            assertEquals(o, pos[o * 4], 1e-6, "da mais próxima à mais distante");
            assertEquals(3, pos[o * 4 + 3], 1e-6);
            // a luz de distância o é a de índice i com i * 7 % 40 == o
            int i = 0;
            while (i * 7 % 40 != o) {
                i++;
            }
            assertEquals(1 + i, col[o * 4 + 3], 1e-6);
        }
    }

    @Test
    void relativoACamera() {
        FxLights.add(10, 20, 30, 1, 1, 1, 1, 2);
        float[] pos = new float[128];
        float[] col = new float[128];
        assertEquals(1, FxLights.fill(8, 20, 33, pos, col));
        assertEquals(2, pos[0], 1e-6);
        assertEquals(0, pos[1], 1e-6);
        assertEquals(-3, pos[2], 1e-6);
    }

    @Test
    void passaDaCapacidadeDescarta() {
        for (int i = 0; i < FxLights.CAPACITY + 5; i++) {
            FxLights.add(i, 0, 0, 1, 1, 1, 1, 1);
        }
        assertEquals(FxLights.CAPACITY, FxLights.count());
    }

    @Test
    void ultimoQuadroGuardaAContagemAntesDeZerar() {
        FxLights.beginFrame();
        FxLights.beginFrame();
        assertEquals(0, FxLights.lastFrameCount());
        // o quadro anota 3 luzes, a passada limpa (count zera) e o valor fica para o e2e
        for (int i = 0; i < 3; i++) {
            FxLights.add(i, 0, 0, 1, 1, 1, 1, 1);
        }
        FxLights.clear();
        assertEquals(0, FxLights.count());
        assertEquals(3, FxLights.lastFrameCount());
        // a virada para o quadro seguinte mantém o do quadro que acabou
        FxLights.beginFrame();
        assertEquals(3, FxLights.lastFrameCount());
        // um quadro sem luz zera na virada seguinte
        FxLights.beginFrame();
        assertEquals(0, FxLights.lastFrameCount());
    }
}
