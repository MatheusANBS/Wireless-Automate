package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrystalMeshTest {
    @Test
    void temDozeTriangulos() {
        assertEquals(12, CrystalMesh.TRIANGLES);
        assertEquals(36, CrystalMesh.VERTICES);
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
    void oAnelFicaNoRaioENaAlturaDaSpec() {
        for (int v = 0; v < CrystalMesh.VERTICES; v++) {
            float y = CrystalMesh.y(v);
            if (y == CrystalMesh.TOP || y == CrystalMesh.BOTTOM) {
                assertEquals(0, CrystalMesh.x(v));
                assertEquals(0, CrystalMesh.z(v));
                continue;
            }
            double r = Math.hypot(CrystalMesh.x(v), CrystalMesh.z(v));
            assertTrue(r >= 0.27 - 1e-6 && r <= 0.33 + 1e-6, "raio " + r);
            assertTrue(y >= 0.015 - 1e-6 && y <= 0.065 + 1e-6, "altura " + y);
        }
    }
}
