package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import org.junit.jupiter.api.Test;

class TvCanvasViewTest {
    private static TvCanvasView view(int rectW, int rectH, TvArea area) {
        TvCanvasView view = new TvCanvasView();
        view.layout(new TvRect(10, 20, rectW, rectH));
        view.fit(area);
        return view;
    }

    /** Toda linha de grade k·4 é a borda de uma caixa de x = k·4, e as caixas vizinhas se encostam sem fresta. */
    private static void conferir(TvCanvasView view, TvArea area) {
        int w = area.pixelWidth();
        int h = area.pixelHeight();
        assertEquals(view.x() + view.width(), view.screenX(w), "o fim da textura é o último pixel de desenho");
        assertEquals(view.y() + view.height(), view.screenY(h), "o fim da textura, em y");
        int previousX = view.screenX(0);
        for (int px = 4; px <= w; px += 4) {
            int line = view.screenX(px);
            int boxRight = view.screenX(px - 4 + 4);
            assertEquals(line, boxRight, "a borda direita da caixa em " + (px - 4) + " é a linha " + px);
            assertTrue(line >= previousX, "as linhas nunca voltam em " + px);
            previousX = line;
        }
        int previousY = view.screenY(0);
        for (int py = 4; py <= h; py += 4) {
            int line = view.screenY(py);
            assertTrue(line >= previousY, "as linhas nunca voltam em " + py);
            previousY = line;
        }
        for (int px = 0; px <= w; px += 4) {
            assertEquals(px, view.toX(view.screenX(px)), 0.5 / view.stepX() + 1e-9, "ida e volta em " + px);
        }
    }

    @Test
    void gradeEncaixaNasCaixasEmEscalasInteirasEFracionarias() {
        int[][] areas = {{1, 1}, {3, 3}, {6, 2}, {12, 12}, {32, 32}};
        int[][] rects = {{200, 150}, {300, 200}, {120, 90}, {64, 64}};
        for (int[] a : areas) {
            TvArea area = new TvArea(a[0], a[1], 0, 0);
            for (int[] r : rects) {
                TvCanvasView view = view(r[0], r[1], area);
                conferir(view, area);
                for (int dir = 0; dir < 5; dir++) {
                    view.zoom(1, 40 + dir * 17, 50 + dir * 9);
                    conferir(view, area);
                    view.pan(-13 * dir, 7 * dir);
                    conferir(view, area);
                }
                for (int dir = 0; dir < 5; dir++) {
                    view.zoom(-1, 90, 70);
                    conferir(view, area);
                }
            }
        }
    }

    @Test
    void escalaInteiraDaPassoDeBlocoExato() {
        TvArea area = new TvArea(3, 3, 0, 0);
        TvCanvasView view = view(300, 200, area);
        double step = view.stepX();
        assertEquals(Math.rint(step), step, 0, "a escala de caber é inteira");
        for (int px = 0; px <= area.pixelWidth(); px++) {
            assertEquals(view.x() + px * (int) step, view.screenX(px));
        }
    }
}
