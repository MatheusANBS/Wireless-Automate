package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrystalParamTest {
    @Test
    void idaEVoltaDeTodasAsCombinacoes() {
        for (int seed = 0; seed < 16; seed++) {
            for (int state = 0; state <= CrystalParam.CRACKED; state++) {
                for (boolean far : new boolean[] {false, true}) {
                    int p = CrystalParam.pack(seed, state, far);
                    assertEquals(seed, CrystalParam.seed(p));
                    assertEquals(state, CrystalParam.state(p));
                    assertEquals(far, CrystalParam.far(p));
                    assertTrue(p >= 0 && p < 32768, "o parâmetro cabe num short: " + p);
                }
            }
        }
    }

    @Test
    void oRachadoFicaDepoisDoFimDaRecarga() {
        assertEquals(1003, CrystalParam.CRACKED);
        assertEquals(1002, CrystalParam.recharging(1.0));
        assertEquals(2, CrystalParam.recharging(0.0));
        assertEquals(2, CrystalParam.recharging(-1.0));
        assertEquals(1002, CrystalParam.recharging(7.0));
    }

    @Test
    void oBitLongeNaoMexeNoEstado() {
        int near = CrystalParam.pack(5, CrystalParam.recharging(0.5), false);
        int far = CrystalParam.pack(5, CrystalParam.recharging(0.5), true);
        assertEquals(CrystalParam.state(near), CrystalParam.state(far));
        assertFalse(CrystalParam.far(near));
        assertTrue(CrystalParam.far(far));
    }
}
