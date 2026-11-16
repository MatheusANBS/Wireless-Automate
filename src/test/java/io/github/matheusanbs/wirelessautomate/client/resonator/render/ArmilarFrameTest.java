package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import org.junit.jupiter.api.Test;

class ArmilarFrameTest {
    private static final double E = 1e-9;

    private static int[] originOf(Face front) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (int lx = 0; lx < 5; lx++) {
            for (int lz = 0; lz < 5; lz++) {
                int[] w = ResonatorLayout.world(front, 100, 64, 100, lx, 0, lz);
                minX = Math.min(minX, w[0]);
                minZ = Math.min(minZ, w[2]);
            }
        }
        return new int[] {minX, 64, minZ};
    }

    @Test
    void yawCumpreAOrientacaoDoLeste() {
        for (Face front : ResonatorLayout.FRONTS) {
            Face east = ResonatorLayout.toWorld(front, Face.EAST);
            ArmilarFrame f = new ArmilarFrame();
            f.set(front, originOf(front));
            assertEquals(east.dx(), Math.cos(f.yaw()), 1e-6, "cos " + front);
            assertEquals(east.dz(), -Math.sin(f.yaw()), 1e-6, "sen " + front);
        }
        ArmilarFrame f = new ArmilarFrame();
        f.set(Face.SOUTH, originOf(Face.SOUTH));
        assertEquals(0.0, f.yaw(), 1e-6);
        f.set(Face.WEST, originOf(Face.WEST));
        assertEquals(-Math.PI / 2, f.yaw(), 1e-6);
        f.set(Face.EAST, originOf(Face.EAST));
        assertEquals(Math.PI / 2, f.yaw(), 1e-6);
    }

    @Test
    void centroDaCasaLocalViraOCentroDoBlocoDoMundo() {
        double[] out = new double[3];
        for (Face front : ResonatorLayout.FRONTS) {
            ArmilarFrame f = new ArmilarFrame();
            f.set(front, originOf(front));
            for (int lx = 0; lx < 5; lx++) {
                for (int lz = 0; lz < 5; lz++) {
                    int[] w = ResonatorLayout.world(front, 100, 64, 100, lx, 0, lz);
                    f.toWorld((lx - 2) * 16, 0, (lz - 2) * 16, out);
                    assertEquals(w[0] + 0.5, out[0], E, "x " + front + " " + lx + "," + lz);
                    assertEquals(w[2] + 0.5, out[2], E, "z " + front + " " + lx + "," + lz);
                    assertEquals(64.0, out[1], E, "y");
                }
            }
        }
    }

    @Test
    void nucleoFicaNaFrenteACasaDoCentro() {
        double[] out = new double[3];
        double[] dir = new double[3];
        for (Face front : ResonatorLayout.FRONTS) {
            ArmilarFrame f = new ArmilarFrame();
            f.set(front, originOf(front));
            // o Núcleo é a casa local (2, 0, 4): o centro do bloco no mundo é (100,5; 64; 100,5)
            f.toWorld(0, 0, 32, out);
            int[] core = ResonatorLayout.world(front, 100, 64, 100, 2, 0, 4);
            assertEquals(core[0] + 0.5, out[0], E, "x " + front);
            assertEquals(core[2] + 0.5, out[2], E, "z " + front);
            // a frente do Núcleo é a direção front a partir do centro: (0, 0, 40) fica a 2,5 blocos dela
            f.toWorld(0, 0, 40, out);
            f.dirToWorld(0, 0, 40, dir);
            assertEquals(2.5 * front.dx(), dir[0], E, "dx " + front);
            assertEquals(2.5 * front.dz(), dir[2], E, "dz " + front);
            assertEquals(f.centerX() + dir[0], out[0], E);
            assertEquals(f.centerZ() + dir[2], out[2], E);
        }
    }
}
