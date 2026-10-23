package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrystalMeshTest {
    @Test
    void temTrintaEDoisTriangulos() {
        assertEquals(32, CrystalMesh.TRIANGLES);
        assertEquals(96, CrystalMesh.VERTICES);
    }

    @Test
    void asNormaisSaemParaForaEBatemComOSentidoDosVertices() {
        for (int t = 0; t < CrystalMesh.TRIANGLES; t++) {
            int a = 3 * t;
            float nx = CrystalMesh.normalX(t);
            float ny = CrystalMesh.normalY(t);
            float nz = CrystalMesh.normalZ(t);
            assertEquals(1.0, Math.sqrt(nx * nx + ny * ny + nz * nz), 1e-5);
            double mx = (CrystalMesh.x(a) + CrystalMesh.x(a + 1) + CrystalMesh.x(a + 2)) / 3;
            double my = (CrystalMesh.y(a) + CrystalMesh.y(a + 1) + CrystalMesh.y(a + 2)) / 3 - 0.05;
            double mz = (CrystalMesh.z(a) + CrystalMesh.z(a + 1) + CrystalMesh.z(a + 2)) / 3;
            assertTrue(nx * mx + ny * my + nz * mz > 0, "triângulo " + t + " com a normal para dentro");
            double ex = CrystalMesh.x(a + 1) - CrystalMesh.x(a);
            double ey = CrystalMesh.y(a + 1) - CrystalMesh.y(a);
            double ez = CrystalMesh.z(a + 1) - CrystalMesh.z(a);
            double fx = CrystalMesh.x(a + 2) - CrystalMesh.x(a);
            double fy = CrystalMesh.y(a + 2) - CrystalMesh.y(a);
            double fz = CrystalMesh.z(a + 2) - CrystalMesh.z(a);
            double cx = ey * fz - ez * fy;
            double cy = ez * fx - ex * fz;
            double cz = ex * fy - ey * fx;
            assertTrue(cx * nx + cy * ny + cz * nz > 0, "triângulo " + t + " em sentido horário visto de fora");
        }
    }

    @Test
    void asAlturasVaoDeZeroAUm() {
        float min = 1;
        float max = 0;
        for (int v = 0; v < CrystalMesh.VERTICES; v++) {
            float h = CrystalMesh.height(v);
            assertTrue(h >= 0 && h <= 1);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        assertEquals(0, min, 1e-6);
        assertEquals(1, max, 1e-6);
    }

    @Test
    void cadaTrianguloTemOsTresCantosDaFaceta() {
        for (int t = 0; t < CrystalMesh.TRIANGLES; t++) {
            int a = 3 * t;
            assertEquals(0, CrystalMesh.u(a));
            assertEquals(0, CrystalMesh.v(a));
            assertEquals(1, CrystalMesh.u(a + 1));
            assertEquals(0, CrystalMesh.v(a + 1));
            assertEquals(0, CrystalMesh.u(a + 2));
            assertEquals(1, CrystalMesh.v(a + 2));
        }
    }

    @Test
    void osAneisDoCorpoFicamNoRaioENaAlturaDaSpec() {
        int lower = 0;
        int upper = 0;
        for (int v = 0; v < CrystalMesh.VERTICES; v++) {
            float y = CrystalMesh.y(v);
            if (y == CrystalMesh.TOP || y == CrystalMesh.BOTTOM) {
                assertEquals(0, CrystalMesh.x(v));
                assertEquals(0, CrystalMesh.z(v));
                continue;
            }
            double r = Math.hypot(CrystalMesh.x(v), CrystalMesh.z(v));
            assertTrue(r >= 0.25 - 1e-6 && r <= 0.31 + 1e-6, "raio " + r);
            if (y < 0) {
                lower++;
                assertTrue(y >= -0.22 - 1e-6 && y <= -0.18 + 1e-6, "altura do anel de baixo " + y);
            } else {
                upper++;
                assertTrue(y >= 0.24 - 1e-6 && y <= 0.28 + 1e-6, "altura do anel de cima " + y);
            }
        }
        assertTrue(lower > 0 && upper > 0);
    }

    @Test
    void asDiagonaisDoCorpoNaoAcendemEAsPontasNaoTemDiagonal() {
        int sides = 0;
        for (int t = 0; t < CrystalMesh.TRIANGLES; t++) {
            int hidden = CrystalMesh.hidden(t);
            if (hidden < 0) {
                continue;
            }
            sides++;
            assertTrue(hidden <= 2);
        }
        assertEquals(2 * CrystalMesh.SIDES, sides, "um par de triângulos por face lateral");
        // os dois triângulos de uma face escondem a mesma aresta: a que une os mesmos dois pontos
        for (int face = 0; face < CrystalMesh.SIDES; face++) {
            int[] first = hiddenEdge(4 * face);
            int[] second = hiddenEdge(4 * face + 1);
            assertTrue(samePoint(first[0], second[0]) && samePoint(first[1], second[1])
                    || samePoint(first[0], second[1]) && samePoint(first[1], second[0]),
                    "a face " + face + " esconde arestas diferentes");
            assertEquals(-1, CrystalMesh.hidden(4 * face + 2));
            assertEquals(-1, CrystalMesh.hidden(4 * face + 3));
        }
    }

    @Test
    void oUvCodificadoTrazOSinalSomadoDeOito() {
        for (int t = 0; t < CrystalMesh.TRIANGLES; t++) {
            for (int k = 0; k < 3; k++) {
                int v = 3 * t + k;
                assertEquals(CrystalMesh.u(v) + 8f * (CrystalMesh.hidden(t) + 1), CrystalMesh.encodedU(v), 1e-6);
            }
        }
    }

    /** Os dois vértices (índices) da aresta oposta ao canto escondido do triângulo {@code t}. */
    private static int[] hiddenEdge(int t) {
        int hidden = CrystalMesh.hidden(t);
        int[] edge = new int[2];
        int n = 0;
        for (int k = 0; k < 3; k++) {
            if (k != hidden) {
                edge[n++] = 3 * t + k;
            }
        }
        return edge;
    }

    private static boolean samePoint(int a, int b) {
        return CrystalMesh.x(a) == CrystalMesh.x(b) && CrystalMesh.y(a) == CrystalMesh.y(b)
                && CrystalMesh.z(a) == CrystalMesh.z(b);
    }
}
