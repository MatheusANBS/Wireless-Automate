package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

import static io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelToolsTest.corner;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** O editor de pixel art pelo caminho do mouse e das teclas: traços, formas, seleção, desfazer e salvar. */
class TvPixelSessionTest {
    private static TvPixelSession session() {
        TvPixelSession s = new TvPixelSession(TvPixelCanvas.blank(8, 8));
        s.choose(1);
        return s;
    }

    private static void stroke(TvPixelSession s, int... points) {
        s.press(points[0], points[1], false, false, false);
        for (int i = 2; i < points.length; i += 2) {
            s.drag(points[i], points[i + 1], false);
        }
        s.release();
    }

    @Test
    void lapisUmPassoPorTracoEDesfazer() {
        TvPixelSession s = session();
        stroke(s, 0, 0, 3, 0);
        assertEquals("1111", corner(s.canvas(), 4, 1));
        assertEquals(1, s.history().size());
        assertTrue(s.dirty());
        assertTrue(s.undo());
        assertEquals("0000", corner(s.canvas(), 4, 1));
        assertTrue(s.redo());
        assertEquals("1111", corner(s.canvas(), 4, 1));
    }

    @Test
    void cliqueForaDaArteNaoGuardaPasso() {
        TvPixelSession s = session();
        s.press(-1, 3, false, false, false);
        s.release();
        stroke(s, 0, 0);
        stroke(s, 0, 0);
        assertEquals(1, s.history().size(), "o segundo traço não mudou nada");
    }

    @Test
    void pixelPerfectTiraOCantoDoL() {
        TvPixelSession s = session();
        s.togglePerfect();
        stroke(s, 0, 0, 1, 0, 1, 1);
        assertEquals("10\n01", corner(s.canvas(), 2, 2), "o canto (1, 0) voltou ao transparente");
        s.canvas().clear();
        s.brushBy(1);
        stroke(s, 0, 0, 1, 0, 1, 1);
        assertEquals(1, s.canvas().get(1, 0), "com pincel de 2 px o pixel perfect não vale");
    }

    @Test
    void cliqueDireitoApagaEClareia() {
        TvPixelSession s = session();
        stroke(s, 0, 0, 2, 0);
        s.press(1, 0, true, false, false);
        s.release();
        assertEquals("101", corner(s.canvas(), 3, 1), "no lápis, o direito é a borracha");
        s.canvas().set(4, 4, 5);
        s.pickTool(TvPixelTool.SHADE);
        s.press(4, 4, true, false, false);
        s.release();
        assertEquals(4, s.canvas().get(4, 4), "no clarear, o direito escurece");
        s.press(4, 4, false, false, false);
        s.drag(4, 4, false);
        s.release();
        assertEquals(5, s.canvas().get(4, 4));
    }

    @Test
    void contaGotasComCtrlEAnterior() {
        TvPixelSession s = session();
        s.canvas().set(2, 2, 9);
        s.press(2, 2, false, false, true);
        s.release();
        assertEquals(9, s.current());
        s.swapColors();
        assertEquals(1, s.current(), "o X volta para a cor anterior");
        assertEquals(0, s.history().size(), "pegar a cor não é passo");
    }

    @Test
    void linhaComShiftTravaEm45Graus() {
        TvPixelSession s = session();
        s.pickTool(TvPixelTool.LINE);
        s.press(0, 0, false, false, false);
        s.drag(3, 2, true);
        s.release();
        assertEquals(1, s.canvas().get(3, 3), "travada na diagonal");
        assertEquals(0, s.canvas().get(3, 2));
        s.pickTool(TvPixelTool.RECT);
        s.pickTool(TvPixelTool.RECT);
        assertTrue(s.rectFilled(), "R de novo alterna o cheio");
        s.canvas().clear();
        s.press(0, 0, false, false, false);
        s.drag(5, 1, true);
        s.release();
        assertEquals(1, s.canvas().get(5, 5), "com Shift, quadrado");
    }

    @Test
    void formaRefeitaAoMoverOMouse() {
        TvPixelSession s = session();
        s.pickTool(TvPixelTool.ELLIPSE);
        s.press(0, 0, false, false, false);
        s.drag(7, 7, false);
        s.drag(2, 2, false);
        s.release();
        assertEquals(0, s.canvas().get(7, 4), "a elipse grande saiu ao encolher");
        assertEquals(1, s.canvas().get(1, 0));
    }

    @Test
    void desfazerDepoisDeMoverUmPedaco() {
        TvPixelSession s = session();
        stroke(s, 0, 0, 1, 0);
        s.pickTool(TvPixelTool.SELECT);
        s.press(0, 0, false, false, false);
        s.drag(1, 0, false);
        s.release();
        assertEquals(2, s.selection().width());
        s.press(0, 0, false, false, false);
        s.drag(3, 2, false);
        s.release();
        assertTrue(s.selection().floating());
        s.settle();
        assertEquals(1, s.canvas().get(3, 2));
        assertEquals(0, s.canvas().get(0, 0));
        assertEquals(2, s.history().size(), "o traço e o mover");
        assertTrue(s.undo());
        assertEquals("11", corner(s.canvas(), 2, 1), "o pedaço voltou ao lugar");
        assertEquals(0, s.canvas().get(3, 2));
        assertFalse(s.selection().active());
    }

    @Test
    void escDesfazOMoverETiraASelecao() {
        TvPixelSession s = session();
        stroke(s, 0, 0);
        s.selectAll();
        s.nudge(2, 0);
        assertTrue(s.selection().floating());
        assertTrue(s.escape());
        assertEquals(1, s.canvas().get(0, 0));
        assertFalse(s.selection().active());
        assertEquals(1, s.history().size(), "o mover cancelado não vira passo");
        assertFalse(s.history().canRedo());
        assertFalse(s.escape(), "sem seleção, o Esc é da tela");
    }

    @Test
    void ferramentasSoPintamDentroDaSelecao() {
        TvPixelSession s = session();
        s.pickTool(TvPixelTool.SELECT);
        s.press(0, 0, false, false, false);
        s.drag(3, 7, false);
        s.release();
        s.pickTool(TvPixelTool.FILL);
        s.press(0, 0, false, false, false);
        s.release();
        assertEquals(1, s.canvas().get(3, 3));
        assertEquals(0, s.canvas().get(4, 3), "o balde parou na seleção");
    }

    @Test
    void copiarColarERecortar() {
        TvPixelSession s = session();
        stroke(s, 0, 0, 1, 0);
        s.selectAll();
        s.deselect();
        s.pickTool(TvPixelTool.SELECT);
        s.press(0, 0, false, false, false);
        s.drag(1, 0, false);
        s.release();
        s.copy();
        s.deselect();
        s.paste(4, 5);
        assertTrue(s.selection().floating());
        assertEquals(4, s.selection().x());
        assertEquals(5, s.selection().y());
        s.settle();
        assertEquals(1, s.canvas().get(5, 5));
        s.deselect();
        s.paste(-3, -3);
        assertEquals(0, s.selection().x(), "nunca antes do canto da arte");
        s.escape();
        s.pickTool(TvPixelTool.SELECT);
        s.press(4, 5, false, false, false);
        s.drag(5, 5, false);
        s.release();
        s.cut();
        assertEquals(0, s.canvas().get(5, 5));
        assertFalse(s.selection().active());
        assertTrue(s.hasClipboard());
    }

    @Test
    void virarGirarENaoQuadrada() {
        TvPixelSession s = new TvPixelSession(TvPixelCanvas.blank(8, 12));
        s.choose(2);
        stroke(s, 0, 0);
        s.transform(TvPixelTransform.ROTATE);
        assertEquals(12, s.canvas().width());
        assertEquals(8, s.canvas().height());
        assertEquals(2, s.canvas().get(11, 0), "o canto de cima à esquerda foi para o de cima à direita");
        s.transform(TvPixelTransform.FLIP_H);
        assertEquals(2, s.canvas().get(0, 0));
        assertTrue(s.undo());
        assertTrue(s.undo());
        assertEquals(8, s.canvas().width());
        assertEquals(2, s.canvas().get(0, 0));
    }

    @Test
    void tamanhoCorRampaELimparSaoPassos() {
        TvPixelSession s = session();
        stroke(s, 7, 7);
        s.resize(10, 8);
        assertEquals(2, s.history().size());
        s.beginColorEdit();
        s.setColor(3, 0xFF112233);
        s.setColor(3, 0xFF445566);
        assertEquals(3, s.history().size(), "uma edição de cor é um passo");
        s.choose(9);
        s.makeRamp();
        assertEquals(4, s.history().size());
        s.clearAll();
        assertEquals(5, s.history().size());
        s.undo();
        assertEquals(1, s.canvas().get(7, 7));
    }

    @Test
    void salvarAssentaOPedacoEMarcaLimpo() {
        TvPixelSession s = session();
        stroke(s, 0, 0);
        s.selectAll();
        s.nudge(1, 1);
        s.prepareSave();
        assertEquals(1, s.canvas().get(1, 1));
        s.markClean();
        assertFalse(s.dirty());
    }
}
