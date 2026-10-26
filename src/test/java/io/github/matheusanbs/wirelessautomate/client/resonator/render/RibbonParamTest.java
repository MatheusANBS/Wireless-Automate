package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RibbonParamTest {
    @Test
    void maximoCabeEmUmShort() {
        assertTrue(RibbonParam.weave(15, 7, 100) < 32768);
        assertTrue(RibbonParam.disc(1, true, 1) < 32768);
    }

    @Test
    void weaveDecodificaPelosDeslocamentosDoShader() {
        int p = RibbonParam.weave(11, 9, 2.7);
        assertEquals(11, p & 15);
        assertEquals(7, (p >> 4) & 7);
        assertEquals(0, p & 128);
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
