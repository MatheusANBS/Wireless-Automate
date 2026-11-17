package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArmilarMeshesTest {
    private static final float E = 1e-4f;

    @Test
    void contagensDoFilme() {
        // 4 quads por segmento do anel (anelFacetado) mais 30 por nó (a base e as 4 garras, 6 quads cada)
        assertEquals(96 + 2 * 30, ArmilarMeshes.ring(0).quads());
        assertEquals(128 + 3 * 30, ArmilarMeshes.ring(1).quads());
        assertEquals(160 + 3 * 30, ArmilarMeshes.ring(2).quads());
        for (int i = 0; i < 3; i++) {
            assertEquals(12, ArmilarMeshes.pins(i).quads());
        }
        assertEquals(18, ArmilarMeshes.crystal().quads());
        assertEquals(16 * 6, ArmilarMeshes.horizon().quads());
        assertEquals(4 * 6, ArmilarMeshes.markers().quads());
    }

    @Test
    void anelBateComOFilme() {
        QuadMesh m = ArmilarMeshes.ring(0);
        // quad 0 (topo), 1 (fora), 2 (dentro), 3 (base) do segmento 0: p0 e normal de shared.js
        assertVertex(m, 0, 11.5f, 1f, 0f, 0f, 1f, 0f);
        assertVertex(m, 1, 14.5f, 1f, 0f, 0.99144f, 0f, -0.13053f);
        assertVertex(m, 2, 11.10815f, 1f, -2.97642f, -0.99144f, 0f, 0.13053f);
        assertVertex(m, 3, 14.5f, -1f, 0f, 0f, -1f, 0f);
        // o segundo e o terceiro vértices do topo
        assertEquals(11.10815f, m.x(0, 1), E);
        assertEquals(-2.97642f, m.z(0, 1), E);
        assertEquals(14.00592f, m.x(0, 2), E);
        assertEquals(-3.75288f, m.z(0, 2), E);
        // o uv do topo: u de 0 a seg, v de 0 a 3 texels de 10; o da face de fora, v de 3 a 5
        assertEquals(0f, m.u(0, 0), E);
        assertEquals(0f, m.v(0, 0), E);
        assertEquals(0.3f, m.v(1, 0), E);
        assertEquals(0.5f, m.v(2, 0), E);
        assertEquals(0.7f, m.v(3, 0), E);
    }

    @Test
    void cristalBateComOFilme() {
        QuadMesh m = ArmilarMeshes.crystal();
        // lado
        assertEquals(0.75f, m.x(0, 0), E);
        assertEquals(1.5f, m.y(0, 0), E);
        assertEquals(1.29904f, m.z(0, 0), E);
        assertEquals(1.5f, m.x(0, 1), E);
        assertEquals(-1.5f, m.y(0, 2), E);
        assertEquals(0.86603f, m.nx(0), E);
        assertEquals(0.5f, m.nz(0), E);
        // ponta de cima e ponta de baixo
        assertEquals(3.4f, m.y(1, 1), E);
        assertEquals(0.71491f, m.nx(1), E);
        assertEquals(0.5644f, m.ny(1), E);
        assertEquals(0.41275f, m.nz(1), E);
        assertEquals(-3.4f, m.y(2, 1), E);
        assertEquals(-0.5644f, m.ny(2), E);
    }

    @Test
    void anelEhContinuoSemFresta() {
        for (int orbit = 0; orbit < 3; orbit++) {
            QuadMesh m = ArmilarMeshes.ring(orbit);
            int n = ArmilarSpec.SEGMENTOS[orbit];
            for (int k = 0; k < n; k++) {
                int next = (k + 1) % n;
                // o fim do topo do segmento k (vértice 1) é o começo do topo do segmento k + 1 (vértice 0)
                assertEquals(m.x(4 * k, 1), m.x(4 * next, 0), E, "x " + orbit + "/" + k);
                assertEquals(m.z(4 * k, 1), m.z(4 * next, 0), E, "z " + orbit + "/" + k);
            }
        }
    }

    @Test
    void normaisUnitariasEParaFora() {
        for (int orbit = 0; orbit < 3; orbit++) {
            QuadMesh m = ArmilarMeshes.ring(orbit);
            for (int q = 0; q < m.quads(); q++) {
                double len = Math.sqrt(m.nx(q) * m.nx(q) + m.ny(q) * m.ny(q) + m.nz(q) * m.nz(q));
                assertEquals(1.0, len, 1e-4, "quad " + q);
                assertTrue(m.tone(q) > 0 && m.tone(q) <= 1);
            }
            // a face de fora de cada segmento aponta para longe do eixo
            int n = ArmilarSpec.SEGMENTOS[orbit];
            for (int k = 0; k < n; k++) {
                int q = 4 * k + 1;
                double cx = (m.x(q, 0) + m.x(q, 2)) / 2;
                double cz = (m.z(q, 0) + m.z(q, 2)) / 2;
                assertTrue(m.nx(q) * cx + m.nz(q) * cz > 0, "fora " + orbit + "/" + k);
            }
        }
    }

    private static void assertVertex(QuadMesh m, int q, float x, float y, float z, float nx, float ny, float nz) {
        assertEquals(x, m.x(q, 0), E, "x");
        assertEquals(y, m.y(q, 0), E, "y");
        assertEquals(z, m.z(q, 0), E, "z");
        assertEquals(nx, m.nx(q), E, "nx");
        assertEquals(ny, m.ny(q), E, "ny");
        assertEquals(nz, m.nz(q), E, "nz");
    }
}
