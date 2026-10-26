package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RibbonParamTest {
    @Test
    void maximoCabeEmUmShort() {
        assertTrue(RibbonParam.bolt(7, 15, true, 100) < 32768);
        assertTrue(RibbonParam.disc(1, true, 1) < 32768);
    }

    @Test
    void boltDecodificaPelosDeslocamentosDoShader() {
        int p = RibbonParam.bolt(4, 3, true, 2.7);
        assertEquals(4, p & 7);
        assertEquals(3, (p >> 3) & 15);
        assertEquals(128, p & 128);
        assertEquals(27, p >> 8);
    }

    @Test
    void discDecodificaRaioAnelEEspessura() {
        int p = RibbonParam.disc(0.5, true, 0.1);
        assertEquals(64, p & 127);
        assertEquals(128, p & 128);
        assertEquals(13, p >> 8);
    }
}
