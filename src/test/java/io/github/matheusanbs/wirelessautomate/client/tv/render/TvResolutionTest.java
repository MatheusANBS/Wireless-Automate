package io.github.matheusanbs.wirelessautomate.client.tv.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvResolutionTest {
    @Test
    void qualidadeNormal() {
        assertEquals(256, TvResolution.pixelsPerBlock(1, 1, 32));
        assertEquals(256, TvResolution.pixelsPerBlock(4, 4, 32));
        assertEquals(256, TvResolution.pixelsPerBlock(16, 16, 32));
        assertEquals(128, TvResolution.pixelsPerBlock(32, 32, 32));
        assertEquals(64, TvResolution.pixelsPerBlock(64, 1, 32));
    }

    @Test
    void metadeEDobro() {
        assertEquals(128, TvResolution.pixelsPerBlock(1, 1, 16));
        assertEquals(512, TvResolution.pixelsPerBlock(1, 1, 64));
        assertEquals(64, TvResolution.pixelsPerBlock(32, 32, 16));
        assertEquals(32, TvResolution.pixelsPerBlock(64, 64, 16));
    }

    @Test
    void tetoNoMaiorLado() {
        assertEquals(256, TvResolution.pixelsPerBlock(16, 1, 64));
        assertEquals(128, TvResolution.pixelsPerBlock(32, 32, 64));
        assertEquals(64, TvResolution.pixelsPerBlock(64, 1, 64));
    }

    @Test
    void pisoGanhaDoTeto() {
        assertEquals(32, TvResolution.pixelsPerBlock(256, 1, 16));
        assertEquals(32, TvResolution.pixelsPerBlock(256, 1, 64));
    }
}
