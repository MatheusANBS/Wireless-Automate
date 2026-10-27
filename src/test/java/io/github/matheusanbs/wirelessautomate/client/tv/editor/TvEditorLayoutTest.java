package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvEditorLayoutTest {
    @Test
    void cabeNaEscala3De1366Por768() {
        TvEditorLayout l = TvEditorLayout.of(4, 4, 447, 248);
        assertEquals(203, l.canvas().w());
        assertEquals(132, l.canvas().h());
        assertEquals(TvEditorLayout.LAYERS_H, l.layers().h());
        assertTrue(l.layers().bottom() <= 4 + 248);
        assertTrue(l.props().right() <= 4 + 447);
        assertTrue(l.components().h() > 0, "sobra lugar para os Componentes");
    }

    @Test
    void sobraVaiParaAMiniatura() {
        TvEditorLayout small = TvEditorLayout.of(0, 0, 447, 248);
        TvEditorLayout big = TvEditorLayout.of(0, 0, 647, 348);
        assertEquals(small.canvas().w() + 200, big.canvas().w());
        assertEquals(small.canvas().h() + 100, big.canvas().h());
        assertEquals(small.props().w(), big.props().w());
        assertEquals(small.layers().h(), big.layers().h());
    }

    @Test
    void minimo() {
        assertTrue(TvEditorLayout.fits(400, 240));
        assertFalse(TvEditorLayout.fits(333, 184), "a escala 4 de 1366 × 768 não cabe");
        assertFalse(TvEditorLayout.fits(500, 239));
    }

    @Test
    void areasNaoSeCruzam() {
        TvEditorLayout l = TvEditorLayout.of(0, 0, 400, 240);
        assertTrue(l.base().bottom() <= l.canvas().y());
        assertTrue(l.canvas().bottom() <= l.buttons().y());
        assertTrue(l.buttons().bottom() <= l.layers().y());
        assertTrue(l.pieceButtons()[2].bottom() <= l.componentsLabel().y());
        assertTrue(l.canvas().right() <= l.props().x());
        assertTrue(l.canvas().w() > 0 && l.canvas().h() > 0);
    }
}
