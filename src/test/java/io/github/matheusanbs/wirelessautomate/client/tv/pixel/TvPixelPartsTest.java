package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

import static io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelToolsTest.corner;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvHsv;
import org.junit.jupiter.api.Test;

/** A arte, a rampa, a seleção, o desfazer e a vista do editor de pixel art. */
class TvPixelPartsTest {
    @Test
    void mudarOTamanhoMantemOCantoDeCima() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        canvas.set(0, 0, 1);
        canvas.set(7, 7, 2);
        canvas.set(3, 2, 3);
        canvas.resize(12, 9);
        assertEquals(12, canvas.width());
        assertEquals(1, canvas.get(0, 0));
        assertEquals(2, canvas.get(7, 7));
        assertEquals(3, canvas.get(3, 2));
        assertEquals(0, canvas.get(11, 8), "completa com transparente");
        canvas.resize(8, 8);
        canvas.resize(5, 5);
        assertEquals(8, canvas.width(), "abaixo de 8 não muda");
        canvas.resize(4 + 4, 64);
        assertEquals(3, canvas.get(3, 2));
    }

    @Test
    void aPaletaPadraoEACorZeroTransparente() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(16, 16);
        assertEquals(0, canvas.color(0));
        assertEquals(0xFF1B1920, canvas.color(1));
        assertEquals(0xFFF0603E, canvas.color(6));
        assertEquals(0xFFB8BEF5, canvas.color(15));
        int before = canvas.version();
        canvas.setColor(0, 0xFFFFFFFF);
        assertEquals(0, canvas.color(0), "a cor 0 não muda");
        assertEquals(before, canvas.version());
    }

    @Test
    void passoDaRampaNuncaTrocaDeLinha() {
        assertEquals(6, TvPixelRamp.step(5, 1));
        assertEquals(4, TvPixelRamp.step(5, -1));
        assertEquals(4, TvPixelRamp.step(4, -1), "a ponta escura fica");
        assertEquals(7, TvPixelRamp.step(7, 1), "a ponta clara fica");
        assertEquals(1, TvPixelRamp.step(1, -1), "a linha 0 nunca chega ao transparente");
        assertEquals(0, TvPixelRamp.step(0, 1), "o transparente não clareia");
        assertEquals(3, TvPixelRamp.step(2, 1));
    }

    @Test
    void criarRampaComDesvioDeMatiz() {
        int[] palette = TvPixelCanvas.defaultPalette();
        int base = 0xFF40A060;
        palette[9] = base;
        TvPixelRamp.make(palette, 9);
        assertEquals(base, palette[9], "a cor escolhida fica");
        float[] mid = TvHsv.fromRgb(base & 0xFFFFFF);
        float[] dark = TvHsv.fromRgb(palette[8] & 0xFFFFFF);
        float[] light = TvHsv.fromRgb(palette[10] & 0xFFFFFF);
        float[] lighter = TvHsv.fromRgb(palette[11] & 0xFFFFFF);
        assertEquals(mid[2] - 0.17, dark[2], 0.01, "uma casa para a esquerda: 0,17 mais escura");
        assertEquals(mid[1] + 0.08, dark[1], 0.02, "e 0,08 mais saturada");
        assertEquals(mid[2] + 0.17, light[2], 0.01);
        assertEquals(mid[1] - 0.16, lighter[1], 0.02, "duas casas para a direita: 0,16 menos saturada");
        double hue = mid[0] * 360;
        assertEquals(hue + 14, dark[0] * 360, 1.5, "o escuro puxa o matiz 14° para o azul (235°)");
        assertEquals(hue - 14, light[0] * 360, 1.5, "o claro puxa 14° para o amarelo (55°)");
        assertEquals(hue - 28, lighter[0] * 360, 1.5);
        assertEquals(0xFFFFB08A, palette[7], "as outras linhas não mudam");
    }

    @Test
    void criarRampaNaLinhaZeroNaoTocaNoTransparente() {
        int[] palette = TvPixelCanvas.defaultPalette();
        TvPixelRamp.make(palette, 2);
        assertEquals(0, palette[0]);
        assertTrue((palette[1] >>> 24) == 0xFF && (palette[3] >>> 24) == 0xFF);
        assertTrue(TvHsv.fromRgb(palette[1] & 0xFFFFFF)[2] < TvHsv.fromRgb(palette[3] & 0xFFFFFF)[2]);
    }

    @Test
    void matizPeloCaminhoMaisCurto() {
        assertEquals(221, TvPixelRamp.toward(207, 235, 14), 1e-9);
        assertEquals(235, TvPixelRamp.toward(230, 235, 14), 1e-9, "não passa do alvo");
        assertEquals(346, TvPixelRamp.toward(0, 235, 14), 1e-9, "do vermelho ao azul pelo lado curto");
    }

    @Test
    void levantarEAssentarSemCobrirComOTransparente() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        canvas.set(0, 0, 1);
        canvas.set(3, 0, 5);
        TvPixelSelection selection = new TvPixelSelection();
        selection.select(canvas, 0, 0, 2, 1);
        selection.lift(canvas);
        assertTrue(selection.floating());
        assertEquals(0, canvas.get(0, 0), "levantar deixa o buraco");
        selection.moveTo(2, 0);
        selection.commit(canvas);
        assertEquals(1, canvas.get(2, 0));
        assertEquals(5, canvas.get(3, 0), "o transparente do pedaço não cobriu nada");
        assertFalse(selection.floating());
        assertEquals(2, selection.x());
    }

    @Test
    void pedacoQueSaiDaArteFicaCortado() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        canvas.set(0, 0, 1);
        canvas.set(1, 0, 1);
        TvPixelSelection selection = new TvPixelSelection();
        selection.select(canvas, 0, 0, 2, 1);
        selection.lift(canvas);
        selection.moveTo(7, 0);
        selection.commit(canvas);
        assertEquals(1, canvas.get(7, 0));
        assertEquals(1, selection.width(), "a marca é só a parte de dentro");
    }

    @Test
    void girarUmPedacoNaoQuadrado() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        canvas.set(0, 0, 1);
        canvas.set(1, 0, 2);
        canvas.set(2, 0, 3);
        TvPixelSelection selection = new TvPixelSelection();
        selection.select(canvas, 0, 0, 3, 1);
        selection.lift(canvas);
        selection.transform(TvPixelTransform.ROTATE);
        assertEquals(1, selection.width());
        assertEquals(3, selection.height());
        selection.commit(canvas);
        assertEquals("1\n2\n3", corner(canvas, 1, 3), "girado no sentido horário, de cima para baixo");
    }

    @Test
    void virarEGirarUmaGrade() {
        byte[] src = {1, 2, 3, 4, 5, 6};
        assertArrayEquals(new byte[] {3, 2, 1, 6, 5, 4}, TvPixelTransform.FLIP_H.apply(src, 3, 2));
        assertArrayEquals(new byte[] {4, 5, 6, 1, 2, 3}, TvPixelTransform.FLIP_V.apply(src, 3, 2));
        assertArrayEquals(new byte[] {4, 1, 5, 2, 6, 3}, TvPixelTransform.ROTATE.apply(src, 3, 2));
    }

    @Test
    void historicoDeCemPassos() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        TvPixelHistory history = new TvPixelHistory();
        for (int i = 0; i < 120; i++) {
            history.push(canvas.snapshot());
            canvas.set(i % 8, i / 8 % 8, 1 + i % 15);
        }
        assertEquals(TvPixelHistory.MAX, history.size());
        int undone = 0;
        while (history.undo(canvas)) {
            undone++;
        }
        assertEquals(100, undone);
        assertTrue(history.redo(canvas));
        history.push(canvas.snapshot());
        assertFalse(history.canRedo(), "um passo novo apaga o refazer");
    }

    @Test
    void passoSemMudancaSaiDoHistorico() {
        TvPixelCanvas canvas = TvPixelCanvas.blank(8, 8);
        TvPixelHistory history = new TvPixelHistory();
        history.push(canvas.snapshot());
        history.dropIfUnchanged(canvas);
        assertEquals(0, history.size());
        history.push(canvas.snapshot());
        canvas.set(0, 0, 1);
        history.dropIfUnchanged(canvas);
        assertEquals(1, history.size());
    }

    @Test
    void enquadrarEscolheONivelQueCabe() {
        TvPixelView view = new TvPixelView();
        view.fit(200, 170, 16, 16);
        assertEquals(8, view.zoom(), "(170 − 24) / 16 = 9,1: o nível 8");
        assertEquals(36, view.panX());
        assertEquals(21, view.panY());
        view.fit(100, 100, 64, 64);
        assertEquals(TvPixelView.MIN_ZOOM, view.zoom(), "nunca abaixo do menor nível");
        view.fit(210, 160, 128, 128);
        assertEquals(1, view.zoom(), "a arte de 128 × 128 cabe inteira no quadro da janela mínima");
        assertTrue(view.panX() >= 0 && view.panY() >= 0, "centrada, sem sair do quadro");
    }

    @Test
    void zoomNoPontoDoMouseMantemOPixel() {
        TvPixelView view = new TvPixelView();
        view.fit(200, 170, 16, 16);
        int cellX = view.cellX(101);
        int cellY = view.cellY(77);
        assertTrue(view.zoomAt(101, 77, 1));
        assertEquals(10, view.zoom());
        assertEquals(cellX, view.cellX(101));
        assertEquals(cellY, view.cellY(77));
        for (int i = 0; i < 20; i++) {
            view.zoomAt(101, 77, 1);
        }
        assertEquals(TvPixelView.MAX_ZOOM, view.zoom());
        assertFalse(view.zoomAt(101, 77, 1), "acima do maior nível, nada");
        view.centerOn(3, 4, 200, 170);
        assertEquals(3, view.cellX(100));
        assertEquals(4, view.cellY(85));
        view.panBy(-48, 0);
        assertEquals(4, view.cellX(100));
    }
}
