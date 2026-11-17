package io.github.matheusanbs.wirelessautomate.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FxSheetTest {
    private static final FxSheet SOL = new FxSheet("sol", 32, 32, 6, FxSheet.Layer.LIGHT, false);

    @Test
    void quadroPorRelogio() {
        assertEquals(0, SOL.frameAt(0.0, 8));
        assertEquals(1, SOL.frameAt(1.0 / 8 + 1e-9, 8));
        assertEquals(5, SOL.frameAt(5.5 / 8, 8));
        assertEquals(0, SOL.frameAt(6.0 / 8 + 1e-9, 8));
        // o relógio negativo também dá um quadro válido (o qd do filme)
        assertEquals(5, SOL.frameAt(-0.01, 8));
    }

    @Test
    void quadroComNQuadros() {
        FxSheet fumaca = new FxSheet("fumaca", 16, 16, 4, FxSheet.Layer.INK, false);
        // o filme toca 3 dos 4 quadros da fumaça: o terceiro volta ao primeiro
        assertEquals(0, fumaca.frameAt(0, 4, 3));
        assertEquals(1, fumaca.frameAt(0.26, 4, 3));
        assertEquals(0, fumaca.frameAt(0.76, 4, 3));
        assertEquals(2, fumaca.frameAt(-0.01, 4, 3));
    }

    @Test
    void quadroPorFracao() {
        assertEquals(0, SOL.frameByFraction(-1));
        assertEquals(0, SOL.frameByFraction(0));
        assertEquals(3, SOL.frameByFraction(0.5));
        assertEquals(5, SOL.frameByFraction(1.0));
        assertEquals(5, SOL.frameByFraction(7));
    }

    @Test
    void uvFicaDentroDoQuadro() {
        for (int f = 0; f < 6; f++) {
            float v0 = SOL.v0(f);
            float v1 = SOL.v1(f);
            assertTrue(v0 >= f / 6f && v1 <= (f + 1) / 6f && v0 < v1, "quadro " + f);
        }
        assertEquals(192, SOL.height());
        // o quadro de fora do intervalo é limitado
        assertEquals(SOL.v0(5), SOL.v0(99));
    }
}
