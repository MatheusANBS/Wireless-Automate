package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvAreaTest {
    @Test
    void cresceParaCimaEDireitaDeixaOBlocoNoCantoDeBaixoEsquerdo() {
        TvArea area = TvArea.grown(6, 3, TvArea.Grow.UP_RIGHT);
        assertEquals(new TvArea(6, 3, 0, 0), area);
        assertEquals(0, area.rightOf(0));
        assertEquals(0, area.upOf(0));
        assertEquals(5, area.rightOf(17));
        assertEquals(2, area.upOf(17));
    }

    @Test
    void cresceParaCimaEEsquerdaDeixaOBlocoNoCantoDeBaixoDireito() {
        TvArea area = TvArea.grown(6, 3, TvArea.Grow.UP_LEFT);
        assertEquals(new TvArea(6, 3, 5, 0), area);
        assertEquals(-5, area.rightOf(0));
        assertEquals(0, area.rightOf(5));
    }

    @Test
    void centroArredondaParaBaixoEEsquerda() {
        assertEquals(new TvArea(6, 3, 2, 1), TvArea.grown(6, 3, TvArea.Grow.CENTER));
        assertEquals(new TvArea(5, 5, 2, 2), TvArea.grown(5, 5, TvArea.Grow.CENTER));
        assertEquals(new TvArea(1, 1, 0, 0), TvArea.grown(1, 1, TvArea.Grow.CENTER));
    }

    @Test
    void growReconheceOsTresPresetsENullNoResto() {
        assertEquals(TvArea.Grow.UP_RIGHT, new TvArea(4, 2, 0, 0).grow());
        assertEquals(TvArea.Grow.UP_LEFT, new TvArea(4, 2, 3, 0).grow());
        assertEquals(TvArea.Grow.CENTER, new TvArea(4, 3, 1, 1).grow());
        assertNull(new TvArea(4, 3, 3, 2).grow());
        assertEquals(TvArea.Grow.UP_RIGHT, TvArea.SINGLE.grow());
    }

    @Test
    void pixelsECelulas() {
        TvArea area = new TvArea(6, 3, 0, 0);
        assertEquals(18, area.cells());
        assertEquals(192, area.pixelWidth());
        assertEquals(96, area.pixelHeight());
    }

    @Test
    void recusaAreaInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new TvArea(0, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TvArea(2, 2, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new TvArea(2, 2, 0, -1));
    }

    @Test
    void fitsRespeitaOTeto() {
        assertTrue(TvArea.fits(32, 32, 32, 32));
        assertFalse(TvArea.fits(33, 1, 32, 32));
        assertFalse(TvArea.fits(0, 1, 32, 32));
    }
}
