package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.tv.TvPixels;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Os quadros da animação no editor de pixel art: a faixa, o desfazer, o teto e as ferramentas da arte inteira. */
class TvPixelFramesTest {
    private static TvPixelSession session() {
        TvPixelSession s = new TvPixelSession(TvPixelCanvas.blank(8, 8));
        s.choose(1);
        return s;
    }

    private static void dot(TvPixelSession s, int x, int y) {
        s.press(x, y, false, false, false);
        s.release();
    }

    @Test
    void adicionarDuplicarRemoverEDesfazer() {
        TvPixelSession s = session();
        TvPixelFrames f = s.frames();
        dot(s, 0, 0);
        assertTrue(f.duplicate());
        assertEquals(2, s.canvas().frames());
        assertEquals(1, s.canvas().current(), "a cópia vem logo depois e fica escolhida");
        assertEquals(1, s.canvas().get(0, 0));
        assertTrue(f.add());
        assertEquals(3, s.canvas().frames());
        assertEquals(2, s.canvas().current());
        assertEquals(0, s.canvas().get(0, 0), "o quadro novo é vazio");
        dot(s, 1, 1);
        assertTrue(f.remove());
        assertEquals(2, s.canvas().frames());
        assertEquals(1, s.canvas().current(), "remover escolhe o de antes");
        assertEquals(5, s.history().size(), "o ponto, duplicar, adicionar, o ponto e remover: um passo cada");
        assertTrue(s.undo());
        assertEquals(3, s.canvas().frames());
        assertEquals(2, s.canvas().current(), "o desfazer volta ao quadro em que a mudança foi");
        assertEquals(1, s.canvas().get(1, 1));
        assertTrue(s.redo());
        assertEquals(2, s.canvas().frames());
        TvPixelSession single = session();
        assertFalse(single.frames().remove(), "fica sempre um quadro");
    }

    @Test
    void escolherNaoEPassoEDesenhaNoEscolhido() {
        TvPixelSession s = session();
        s.frames().add();
        s.frames().select(0);
        int steps = s.history().size();
        dot(s, 3, 3);
        assertEquals(1, s.canvas().frame(0)[3 * 8 + 3]);
        assertEquals(0, s.canvas().frame(1)[3 * 8 + 3]);
        s.frames().step(-1);
        assertEquals(1, s.canvas().current(), "o anterior do primeiro é o último");
        s.frames().step(1);
        assertEquals(0, s.canvas().current());
        assertEquals(steps + 1, s.history().size());
    }

    @Test
    void mudarAOrdem() {
        TvPixelSession s = session();
        dot(s, 0, 0);
        s.frames().add();
        dot(s, 1, 0);
        s.frames().add();
        dot(s, 2, 0);
        s.frames().move(2, 0);
        assertEquals(0, s.canvas().current(), "o escolhido segue o quadro que andou");
        assertEquals(1, s.canvas().frame(0)[2]);
        assertEquals(1, s.canvas().frame(1)[0]);
        assertEquals(1, s.canvas().frame(2)[1]);
        s.undo();
        assertEquals(1, s.canvas().frame(2)[2]);
        s.frames().move(1, 1);
        assertTrue(s.history().canRedo(), "mover para o mesmo lugar não é passo");
    }

    @Test
    void oDesfazerDivideOsQuadrosQueNaoMudaram() {
        TvPixelSession s = session();
        for (int i = 0; i < 5; i++) {
            s.frames().add();
        }
        TvPixelCanvas.Snapshot before = s.canvas().snapshot();
        dot(s, 4, 4);
        TvPixelCanvas.Snapshot after = s.canvas().snapshot();
        for (int i = 0; i < 5; i++) {
            assertSame(before.frames()[i], after.frames()[i], "o quadro " + i + " não foi copiado");
        }
        assertNotSame(before.frames()[5], after.frames()[5]);
        assertNotSame(s.canvas().pixels(), after.frames()[5], "o escolhido no estado guardado é uma cópia");
    }

    @Test
    void velocidadeEModoSaoPassos() {
        TvPixelSession s = session();
        s.frames().add();
        s.frames().setFps(12);
        s.frames().setFps(99);
        assertEquals(TvPixels.MAX_FPS, s.canvas().fps());
        s.frames().setMode(TvPixels.Mode.PINGPONG);
        s.frames().setMode(TvPixels.Mode.PINGPONG);
        assertEquals(4, s.history().size());
        s.undo();
        assertEquals(TvPixels.Mode.LOOP, s.canvas().mode());
        s.undo();
        assertEquals(12, s.canvas().fps());
    }

    @Test
    void tetoDePixelsContaOsQuadros() {
        TvPixelSession s = session();
        s.frames().maxPixels(8 * 8 * 3);
        assertTrue(s.frames().add());
        assertTrue(s.frames().duplicate());
        assertFalse(s.frames().canAdd());
        assertFalse(s.frames().add());
        s.resize(16, 8);
        assertEquals(8, s.canvas().width(), "três quadros de 16 × 8 passam do teto");
        s.frames().maxPixels(Integer.MAX_VALUE);
        for (int i = 3; i < TvPixels.MAX_FRAMES; i++) {
            assertTrue(s.frames().add());
        }
        assertFalse(s.frames().add(), "até 64 quadros");
    }

    @Test
    void virarGirarETamanhoValemParaTodosOsQuadros() {
        TvPixelSession s = new TvPixelSession(TvPixelCanvas.blank(8, 12));
        s.choose(2);
        dot(s, 0, 0);
        s.frames().duplicate();
        dot(s, 1, 0);
        s.transform(TvPixelTransform.ROTATE);
        assertEquals(12, s.canvas().width());
        assertEquals(2, s.canvas().frame(0)[11], "o primeiro quadro girou");
        assertEquals(2, s.canvas().frame(1)[11]);
        assertEquals(2, s.canvas().frame(1)[12 + 11], "o segundo também");
        s.resize(16, 16);
        assertEquals(16 * 16, s.canvas().frame(0).length);
        s.clearAll();
        assertEquals(0, s.canvas().frame(1)[11], "limpar vale para o quadro escolhido");
        assertEquals(2, s.canvas().frame(0)[11], "e só para ele");
    }

    @Test
    void aAreaDeCopiaValeEntreQuadros() {
        TvPixelSession s = session();
        dot(s, 2, 2);
        s.pickTool(TvPixelTool.SELECT);
        s.press(2, 2, false, false, false);
        s.drag(2, 2, false);
        s.release();
        s.copy();
        s.frames().add();
        s.deselect();
        s.paste(5, 5);
        s.settle();
        assertEquals(1, s.canvas().get(5, 5));
        s.frames().select(0);
        assertEquals(0, s.canvas().get(5, 5), "colou só no quadro novo");
    }

    @Test
    void pedacoSoltoAssentaAoTrocarDeQuadro() {
        TvPixelSession s = session();
        dot(s, 0, 0);
        s.frames().add();
        s.frames().select(0);
        s.selectAll();
        s.nudge(1, 0);
        s.frames().select(1);
        assertFalse(s.selection().floating());
        assertEquals(1, s.canvas().frame(0)[1], "o pedaço ficou no quadro de antes");
        assertEquals(0, s.canvas().frame(1)[1]);
    }

    @Test
    void previaPapelVegetalETodosOsQuadros() {
        TvPixelSession s = session();
        assertEquals(-1, s.frames().onionFrame(), "sem quadro anterior, nada por baixo");
        s.frames().add();
        s.frames().add();
        s.frames().setFps(2);
        assertEquals(1, s.frames().onionFrame());
        s.frames().select(0);
        assertEquals(2, s.frames().onionFrame(), "o anterior do primeiro é o último");
        s.frames().toggleOnion();
        assertEquals(-1, s.frames().onionFrame());
        assertEquals(1, s.frames().previewFrame(500));
        s.frames().togglePlaying();
        assertEquals(0, s.frames().previewFrame(500), "pausada, a prévia mostra o escolhido");
        dot(s, 7, 7);
        byte[] all = s.canvas().allFrames();
        assertEquals(3 * 64, all.length);
        assertEquals(1, all[63]);
        assertArrayEquals(new byte[64], Arrays.copyOfRange(all, 64, 128));
    }

    @Test
    void abreUmaArteAnimada() {
        byte[] pixels = new byte[64 * 3];
        pixels[64 + 5] = 3;
        TvPixelCanvas canvas = new TvPixelCanvas(8, 8, TvPixelCanvas.defaultPalette(), pixels, 3, 10,
                TvPixels.Mode.PINGPONG);
        assertEquals(3, canvas.frames());
        assertEquals(10, canvas.fps());
        assertEquals(TvPixels.Mode.PINGPONG, canvas.mode());
        assertEquals(3, canvas.frame(1)[5]);
        assertArrayEquals(pixels, canvas.allFrames());
    }
}
