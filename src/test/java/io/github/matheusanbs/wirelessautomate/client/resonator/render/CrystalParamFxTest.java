package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrystalParamFxTest {
    @Test
    void semEfeitoNaoMudaNada() {
        assertEquals(0, CrystalParam.fxKind(0));
        assertEquals(0, CrystalParam.fxValue(0), 1e-6);
    }

    @Test
    void reparoComEnergia() {
        for (double p : new double[] {0, 0.5, 1}) {
            float fx = CrystalParam.repairing(p, true);
            assertEquals(1, CrystalParam.fxKind(fx));
            assertEquals(p, CrystalParam.fxValue(fx), 1e-6);
        }
    }

    @Test
    void rachadoSemEnergia() {
        for (double p : new double[] {0, 0.5, 1}) {
            float fx = CrystalParam.repairing(p, false);
            assertEquals(2, CrystalParam.fxKind(fx));
            assertEquals(p, CrystalParam.fxValue(fx), 1e-6);
        }
    }

    @Test
    void dissolucao() {
        for (double a : new double[] {0, 0.5, 1}) {
            float fx = CrystalParam.dissolving(a);
            assertEquals(3, CrystalParam.fxKind(fx));
            assertEquals(a, CrystalParam.fxValue(fx), 1e-6);
        }
    }
}
