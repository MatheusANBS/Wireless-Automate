package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.resonator.render.CrystalLook.Tex;
import org.junit.jupiter.api.Test;

class CrystalPlanTest {
    @Test
    void linhasAcesas() {
        assertEquals(11, CrystalPlan.rows());
        assertEquals(0, CrystalPlan.litRows(0));
        assertEquals(11, CrystalPlan.litRows(1));
        assertEquals(5, CrystalPlan.litRows(0.5));
        int prev = 0;
        for (int i = 0; i <= 100; i++) {
            int n = CrystalPlan.litRows(i / 100.0);
            assertTrue(n >= prev);
            prev = n;
        }
        // 3/11 em ponto flutuante não pode perder uma linha
        assertEquals(3, CrystalPlan.litRows(3 / 11.0));
    }

    @Test
    void lapidadoVivoSemTier() {
        for (int r = 0; r < 11; r++) {
            assertEquals(Tex.LAPIDADO, CrystalPlan.rowTex(r, (byte) 2, 0, 11));
            assertFalse(CrystalPlan.rowTinted(r, (byte) 2, 0, 11));
        }
    }

    @Test
    void lapidadoAfinadoTemFaixaTingida() {
        for (int r = 0; r < 11; r++) {
            boolean faixa = r == 5 || r == 6;
            assertEquals(faixa ? Tex.FAIXA : Tex.LAPIDADO, CrystalPlan.rowTex(r, (byte) 2, 2, 11), "linha " + r);
            assertEquals(faixa, CrystalPlan.rowTinted(r, (byte) 2, 2, 11), "linha " + r);
            assertEquals(faixa, CrystalPlan.band(r));
        }
    }

    @Test
    void opacoAcendendo() {
        for (int r = 0; r < 11; r++) {
            assertEquals(r < 4 ? Tex.SINAL : Tex.OPACO, CrystalPlan.rowTex(r, (byte) 3, 0, 4), "linha " + r);
        }
        // o Afinado aceso pela recarga também ganha a faixa, só nas linhas já acesas
        assertEquals(Tex.FAIXA, CrystalPlan.rowTex(5, (byte) 4, 1, 7));
        assertEquals(Tex.OPACO, CrystalPlan.rowTex(7, (byte) 4, 1, 7));
    }

    @Test
    void rachadoParado() {
        for (int r = 0; r < 11; r++) {
            assertEquals(Tex.RACHADO, CrystalPlan.rowTex(r, (byte) 5, 0, 0));
            assertFalse(CrystalPlan.rowTinted(r, (byte) 5, 0, 0));
        }
    }

    @Test
    void cortes() {
        assertEquals(12, CrystalPlan.CUTS.length);
        for (int i = 1; i < 12; i++) {
            assertTrue(CrystalPlan.CUTS[i] > CrystalPlan.CUTS[i - 1]);
        }
        double h = ArmilarSpec.CRISTAL_H + ArmilarSpec.CRISTAL_PONTA;
        assertEquals(-h, CrystalPlan.CUTS[0], 1e-12);
        assertEquals(h, CrystalPlan.CUTS[11], 1e-12);
    }

    @Test
    void linhasDaMalha() {
        QuadMesh[] rows = ArmilarMeshes.crystalRows();
        assertEquals(11, rows.length);
        double total = 0;
        for (int r = 0; r < 11; r++) {
            total += QuadMeshClipTest.area(rows[r]);
            QuadMeshClipTest.assertWithin(rows[r], CrystalPlan.CUTS[r], CrystalPlan.CUTS[r + 1]);
        }
        assertEquals(QuadMeshClipTest.area(ArmilarMeshes.crystal()), total, 1e-3);
    }
}
