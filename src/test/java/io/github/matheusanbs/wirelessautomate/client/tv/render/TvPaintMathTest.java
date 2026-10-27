package io.github.matheusanbs.wirelessautomate.client.tv.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvPaintMathTest {
    @Test
    void opacidadeCheiaNaoMudaACor() {
        assertEquals(0xFF336699, TvPaintMath.withOpacity(0xFF336699, 100));
    }

    @Test
    void opacidadeMultiplicaOAlfa() {
        assertEquals(0x7F336699, TvPaintMath.withOpacity(0xFF336699, 50));
        assertEquals(0x40336699, TvPaintMath.withOpacity(0x80336699, 50));
    }

    @Test
    void opacidadeZeroApagaEMantemORgb() {
        assertEquals(0x00336699, TvPaintMath.withOpacity(0xFF336699, 0));
        assertEquals(0x00123456, TvPaintMath.withOpacity(0x00123456, 40));
    }

    @Test
    void canto1Corta1PixelNaPrimeiraLinha() {
        assertEquals(0, TvPaintMath.inset(1, 0));
    }

    @Test
    void canto2CortaUmPixelSoNaPontaDeCima() {
        assertEquals(1, TvPaintMath.inset(2, 0));
        assertEquals(0, TvPaintMath.inset(2, 1));
    }

    @Test
    void cantoDeOitoDescePorDegrausSemSubir() {
        int previous = Integer.MAX_VALUE;
        for (int row = 0; row < 8; row++) {
            int inset = TvPaintMath.inset(8, row);
            assertTrue(inset <= previous, "linha " + row);
            assertTrue(inset >= 0 && inset < 8);
            previous = inset;
        }
        assertEquals(0, TvPaintMath.inset(8, 7));
        assertTrue(TvPaintMath.inset(8, 0) >= 4);
    }

    @Test
    void foraDoCantoNaoCorta() {
        assertEquals(0, TvPaintMath.inset(4, 4));
        assertEquals(0, TvPaintMath.inset(4, -1));
        assertEquals(0, TvPaintMath.inset(0, 0));
    }

    @Test
    void piscaAlternaACada10Ticks() {
        assertFalse(TvPaintMath.blinkOff(0));
        assertFalse(TvPaintMath.blinkOff(9));
        assertTrue(TvPaintMath.blinkOff(10));
        assertTrue(TvPaintMath.blinkOff(19));
        assertFalse(TvPaintMath.blinkOff(20));
        assertEquals(TvPaintMath.blinkSlot(10), TvPaintMath.blinkSlot(19));
    }
}
