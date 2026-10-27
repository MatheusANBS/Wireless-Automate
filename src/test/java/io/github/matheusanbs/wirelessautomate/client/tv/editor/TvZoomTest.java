package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvZoomTest {
    @Test
    void niveisFicamEntreCaberEOito() {
        assertEquals(0, TvZoom.step(0, -1));
        assertEquals(1, TvZoom.step(0, 1));
        assertEquals(TvZoom.STEPS.length - 1, TvZoom.step(TvZoom.STEPS.length - 1, 1));
        assertEquals(1, TvZoom.STEPS[0]);
        assertEquals(8, TvZoom.STEPS[TvZoom.STEPS.length - 1]);
    }

    @Test
    void oPontoSobOMouseFicaParado() {
        int origin = 40;
        int anchor = 100;
        float from = 2;
        float to = 4;
        int next = TvZoom.anchored(anchor, origin, from, to);
        double before = (anchor - origin) / (double) from;
        double after = (anchor - next) / (double) to;
        assertEquals(before, after, 0.01);
    }

    @Test
    void imagemMenorFicaCentradaEMaiorNaoDeixaVazio() {
        assertEquals(10 + 30, TvZoom.clamp(0, 40, 10, 100));
        assertEquals(10, TvZoom.clamp(500, 300, 10, 100));
        assertEquals(10 + 100 - 300, TvZoom.clamp(-900, 300, 10, 100));
        assertTrue(TvZoom.clamp(-50, 300, 10, 100) >= 10 + 100 - 300);
    }
}
