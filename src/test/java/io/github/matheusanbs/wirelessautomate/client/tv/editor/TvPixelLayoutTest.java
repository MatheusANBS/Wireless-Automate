package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvPixelLayoutTest {
    /** A altura dos 16 botões do {@link TvPixelToolbar}: 9 linhas de 18 px com 2 de vão e dois vãos de grupo. */
    private static final int TOOLS_USED = 9 * (TvPixelLayout.BUTTON + 2) - 2 + 2 * 6;

    @Test
    void limparTudoCabeNoPeDasFerramentasNaJanelaMinima() {
        TvPixelLayout l = TvPixelLayout.of(0, 0, TvEditorLayout.MIN_W, TvEditorLayout.MIN_H);
        assertTrue(l.clear().y() - l.tools().y() >= TOOLS_USED + 2, "o botão não encosta no girar");
        assertEquals(l.tools().x(), l.clear().x());
        assertEquals(TvPixelLayout.TOOLS_W, l.clear().w());
        assertEquals(l.side().bottom(), l.clear().bottom(), "alinhado com Cancelar e Salvar");
        assertTrue(l.clear().right() < l.board().x());
        assertTrue(l.board().h() >= 120, "a faixa de quadros deixa o quadro usável: " + l.board().h());
        assertTrue(l.strip().w() >= TvPixelStrip.MIN_W, "a faixa cabe inteira: " + l.strip().w());
    }

    @Test
    void asAreasNaoSeCruzam() {
        TvPixelLayout l = TvPixelLayout.of(10, 20, 640, 400);
        assertTrue(l.tools().right() < l.board().x());
        assertTrue(l.board().right() < l.side().x());
        assertTrue(l.board().bottom() < l.strip().y());
        assertTrue(l.strip().bottom() < l.footer().y());
        assertEquals(l.board().x(), l.strip().x());
        assertEquals(l.board().w(), l.strip().w());
        assertTrue(l.footer().bottom() < l.status().y());
        assertEquals(20 + 400 - TvPixelLayout.PAD, l.status().bottom());
        assertTrue(l.tools().bottom() < l.clear().y());
    }
}
