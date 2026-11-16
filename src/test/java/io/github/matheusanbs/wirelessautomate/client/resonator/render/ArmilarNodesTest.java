package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArmilarNodesTest {
    @Test
    void tabelaDosOitoNos() {
        int[] orbit = {0, 0, 1, 1, 1, 2, 2, 2};
        int[] slot = {0, 1, 0, 1, 2, 0, 1, 2};
        int[] pop = {0, 1, 3, 4, 5, 6, 7, 8};
        for (int n = 0; n < 8; n++) {
            assertEquals(orbit[n], ArmilarNodes.orbit(n), "orbit " + n);
            assertEquals(slot[n], ArmilarNodes.slot(n), "slot " + n);
            assertEquals(pop[n], ArmilarNodes.popIndex(n), "popIndex " + n);
        }
        assertEquals(4 * Math.PI / 3, ArmilarNodes.angle(4), 1e-12);
        assertEquals(Math.PI, ArmilarNodes.angle(1), 1e-12);
    }

    private static void centro(int node, double scale, double prec, double tilt, double spin, double... esperado) {
        double[] out = new double[3];
        ArmilarNodes.center(node, scale, prec, tilt, spin, out);
        assertArrayEquals(esperado, out, 1e-4, "nó " + node);
    }

    @Test
    void centrosDoFilme() {
        centro(4, 1, 1.44, -0.856128782, -1.815, -14.374379, 40.547018, 12.259008);
        centro(5, 0.5, 4.662, 0.909385914, 1.32, 5.5925553, 58.973282, 3.512423);
        centro(1, 1, 3.3, 0.0277092284, 6.28318531, 12.815385, 52.498081, -2.187488);
        centro(7, 1, 5.908, -0.00626574024, 7.72318531, 13.406235, 52.599061, 22.254717);
    }
}
