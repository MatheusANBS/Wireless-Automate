package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrystalMeshTest {
    private static final CrystalMesh[] MESHES = {CrystalMesh.CLUSTER, CrystalMesh.DIAMOND};

    @Test
    void contaOsTriangulosDeCadaMalha() {
        assertEquals(96, CrystalMesh.CLUSTER.triangles());
        assertEquals(288, CrystalMesh.CLUSTER.vertices());
        assertEquals(32, CrystalMesh.DIAMOND.triangles());
        assertEquals(96, CrystalMesh.DIAMOND.vertices());
    }

    @Test
    void asNormaisSaoUnitariasEBatemComOSentidoDosVertices() {
        for (CrystalMesh mesh : MESHES) {
            for (int t = 0; t < mesh.triangles(); t++) {
                int a = 3 * t;
                float nx = mesh.normalX(t);
                float ny = mesh.normalY(t);
                float nz = mesh.normalZ(t);
                assertEquals(1.0, Math.sqrt(nx * nx + ny * ny + nz * nz), 1e-5);
                double ex = mesh.x(a + 1) - mesh.x(a);
                double ey = mesh.y(a + 1) - mesh.y(a);
                double ez = mesh.z(a + 1) - mesh.z(a);
                double fx = mesh.x(a + 2) - mesh.x(a);
                double fy = mesh.y(a + 2) - mesh.y(a);
                double fz = mesh.z(a + 2) - mesh.z(a);
                double cx = ey * fz - ez * fy;
                double cy = ez * fx - ex * fz;
                double cz = ex * fy - ey * fx;
                assertTrue(cx * nx + cy * ny + cz * nz > 0, "triângulo " + t + " em sentido horário visto de fora");
            }
        }
    }

    @Test
    void asNormaisDoLosangoSaemParaFora() {
        CrystalMesh mesh = CrystalMesh.DIAMOND;
        for (int t = 0; t < mesh.triangles(); t++) {
            int a = 3 * t;
            double mx = (mesh.x(a) + mesh.x(a + 1) + mesh.x(a + 2)) / 3;
            double my = (mesh.y(a) + mesh.y(a + 1) + mesh.y(a + 2)) / 3 - 0.05;
            double mz = (mesh.z(a) + mesh.z(a + 1) + mesh.z(a + 2)) / 3;
            assertTrue(mesh.normalX(t) * mx + mesh.normalY(t) * my + mesh.normalZ(t) * mz > 0,
                    "triângulo " + t + " com a normal para dentro");
        }
    }

    @Test
    void asAlturasVaoDeZeroAUm() {
        for (CrystalMesh mesh : MESHES) {
            float min = 1;
            float max = 0;
            for (int v = 0; v < mesh.vertices(); v++) {
                float h = mesh.height(v);
                assertTrue(h >= -1e-6 && h <= 1 + 1e-6, "altura " + h);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
            assertEquals(0, min, 1e-6);
            assertEquals(1, max, 1e-6);
        }
    }

    @Test
    void osAneisDoLosangoFicamNoRaioENaCinta() {
        CrystalMesh mesh = CrystalMesh.DIAMOND;
        for (int v = 0; v < mesh.vertices(); v++) {
            float y = mesh.y(v);
            if (y == CrystalMesh.TOP || y == CrystalMesh.BOTTOM) {
                assertEquals(0, mesh.x(v));
                assertEquals(0, mesh.z(v));
                continue;
            }
            assertEquals(0.30, Math.hypot(mesh.x(v), mesh.z(v)), 1e-5);
            float h = mesh.height(v);
            assertTrue(h >= CrystalMesh.BELT_BOTTOM - 0.02 && h <= CrystalMesh.BELT_TOP + 0.02, "altura da cinta " + h);
        }
    }

    @Test
    void oAglomeradoTemQuatroPontasDeAlturasDiferentes() {
        double[] apex = {0.58, 0.30, 0.06, -0.02};
        for (double expected : apex) {
            boolean found = false;
            for (int v = 0; v < CrystalMesh.CLUSTER.vertices(); v++) {
                if (Math.abs(CrystalMesh.CLUSTER.y(v) - expected) < 1e-5) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "sem ápice em " + expected);
        }
    }
}
