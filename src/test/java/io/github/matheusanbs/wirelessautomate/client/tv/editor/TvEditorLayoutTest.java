package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvEditorLayoutTest {
    @Test
    void cabeNaEscala3De1366Por768() {
        TvEditorLayout l = TvEditorLayout.of(4, 4, 447, 248);
        assertEquals(187, l.canvas().w());
        assertEquals(182, l.canvas().h());
        assertTrue(l.buttons().bottom() <= 4 + 248);
        assertTrue(l.props().right() <= 4 + 447);
        assertTrue(l.layers().h() > 150, "as Camadas usam a altura inteira da coluna");
        assertEquals(l.layers(), l.components());
    }

    @Test
    void sobraVaiParaAMiniatura() {
        TvEditorLayout small = TvEditorLayout.of(0, 0, 447, 248);
        TvEditorLayout big = TvEditorLayout.of(0, 0, 647, 348);
        assertEquals(small.canvas().w() + 200, big.canvas().w());
        assertEquals(small.canvas().h() + 100, big.canvas().h());
        assertEquals(small.props().w(), big.props().w());
        assertEquals(small.leftBody().w(), big.leftBody().w());
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
        assertTrue(l.leftTabs().bottom() <= l.leftBody().y());
        assertTrue(l.pieceButtons()[2].bottom() <= l.leftBody().bottom());
        assertTrue(l.leftBody().right() <= l.canvas().x());
        assertTrue(l.canvas().right() <= l.props().x());
        assertTrue(l.canvas().w() > 0 && l.canvas().h() > 0);
    }
}
