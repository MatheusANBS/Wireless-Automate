package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuadMeshClipTest {
    private static final double INF = 1e9;

    static double tri(QuadMesh m, int q, int a, int b, int c) {
        double ux = m.x(q, b) - m.x(q, a);
        double uy = m.y(q, b) - m.y(q, a);
        double uz = m.z(q, b) - m.z(q, a);
        double vx = m.x(q, c) - m.x(q, a);
        double vy = m.y(q, c) - m.y(q, a);
        double vz = m.z(q, c) - m.z(q, a);
        double cx = uy * vz - uz * vy;
        double cy = uz * vx - ux * vz;
        double cz = ux * vy - uy * vx;
        return 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    /** A soma das áreas dos dois triângulos (p0,p1,p2) e (p0,p2,p3) de cada quad. */
    static double area(QuadMesh m) {
        double s = 0;
        for (int q = 0; q < m.quads(); q++) {
            s += tri(m, q, 0, 1, 2) + tri(m, q, 0, 2, 3);
        }
        return s;
    }

    static void assertWithin(QuadMesh m, double a, double b) {
        for (int q = 0; q < m.quads(); q++) {
            for (int v = 0; v < 4; v++) {
                assertTrue(m.y(q, v) >= a - 1e-5 && m.y(q, v) <= b + 1e-5,
                        "y " + m.y(q, v) + " fora de [" + a + ", " + b + "]");
            }
        }
    }

    @Test
    void semCorteNaoMudaAArea() {
        QuadMesh c = ArmilarMeshes.crystal();
        QuadMesh all = c.clipY(-INF, INF);
        assertEquals(area(c), area(all), 1e-3);
        assertEquals(c.quads(), all.quads());
    }

    @Test
    void cortarEmZeroConservaAArea() {
        QuadMesh c = ArmilarMeshes.crystal();
        double sum = area(c.clipY(-100, 0)) + area(c.clipY(0, 100));
        assertEquals(area(c), sum, 1e-3);
    }

    @Test
    void verticesFicamNoIntervalo() {
        QuadMesh c = ArmilarMeshes.crystal();
        double[][] ranges = {{-3.4, -1.5}, {-0.7, 0.9}, {1.0, 2.45}, {-2, 5}};
        for (double[] r : ranges) {
            assertWithin(c.clipY(r[0], r[1]), r[0], r[1]);
        }
    }

    @Test
    void normalETomSaoDoQuadDeOrigem() {
        QuadMesh c = ArmilarMeshes.crystal();
        QuadMesh cut = c.clipY(-0.7, 0.9);
        assertTrue(cut.quads() > 0);
        for (int q = 0; q < cut.quads(); q++) {
            boolean achou = false;
            for (int o = 0; o < c.quads(); o++) {
                if (cut.nx(q) == c.nx(o) && cut.ny(q) == c.ny(o) && cut.nz(q) == c.nz(o)
                        && cut.tone(q) == c.tone(o)) {
                    achou = true;
                    break;
                }
            }
            assertTrue(achou, "quad " + q);
        }
        // sem corte, na mesma ordem: exato quad a quad
        QuadMesh all = c.clipY(-INF, INF);
        for (int q = 0; q < c.quads(); q++) {
            assertEquals(c.nx(q), all.nx(q));
            assertEquals(c.ny(q), all.ny(q));
            assertEquals(c.nz(q), all.nz(q));
            assertEquals(c.tone(q), all.tone(q));
        }
    }

    @Test
    void uvInterpolaLinearmente() {
        // o quad 0 do cristal é o lado: as arestas 0-3 e 1-2 vão de y = 1,5 a y = -1,5; em y = 0 está o ponto médio
        QuadMesh c = ArmilarMeshes.crystal();
        QuadMesh cut = c.clipY(0, INF);
        int found = 0;
        for (int v = 0; v < 4; v++) {
            if (Math.abs(cut.y(0, v)) < 1e-6) {
                found++;
                boolean viaTres = Math.abs(cut.x(0, v) - c.x(0, 3)) < 1e-6 && Math.abs(cut.z(0, v) - c.z(0, 3)) < 1e-6;
                int a = viaTres ? 3 : 2;
                int b = viaTres ? 0 : 1;
                assertEquals((c.u(0, a) + c.u(0, b)) / 2, cut.u(0, v), 1e-6);
                assertEquals((c.v(0, a) + c.v(0, b)) / 2, cut.v(0, v), 1e-6);
            }
        }
        assertEquals(2, found);
    }
}
