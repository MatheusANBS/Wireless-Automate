package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvDropdownPlaceTest {
    private static final TvRect WINDOW = new TvRect(0, 0, 400, 240);
    private static final int ROW = 11;

    private static void inside(TvDropdownPlace p) {
        assertTrue(p.x() >= WINDOW.x() && p.x() + p.w() <= WINDOW.right(), "dentro na largura: " + p);
        assertTrue(p.y() >= WINDOW.y() && p.y() + p.h() <= WINDOW.bottom(), "dentro na altura: " + p);
    }

    @Test
    void comLugarAbreLogoAbaixoComTodasAsLinhas() {
        TvRect anchor = new TvRect(268, 40, 120, 11);
        TvDropdownPlace p = TvDropdownPlace.of(anchor, WINDOW, 5, ROW, 12, 0);
        assertFalse(p.above());
        assertEquals(anchor.bottom(), p.y());
        assertEquals(5, p.rows());
        assertEquals(5 * ROW + 2 * TvDropdownPlace.PAD, p.h());
        assertEquals(anchor.x(), p.x());
        assertEquals(anchor.w(), p.w());
        inside(p);
    }

    @Test
    void pertoDoFundoAbreParaCima() {
        TvRect anchor = new TvRect(268, 210, 64, 11);
        TvDropdownPlace p = TvDropdownPlace.of(anchor, WINDOW, 8, ROW, 12, 0);
        assertTrue(p.above());
        assertEquals(anchor.y(), p.y() + p.h(), "encostada no controle por cima");
        assertEquals(8, p.rows());
        inside(p);
    }

    @Test
    void semLugarParaTodasFicaNoLadoMaiorERola() {
        TvRect anchor = new TvRect(10, 110, 64, 11);
        TvDropdownPlace p = TvDropdownPlace.of(anchor, WINDOW, 40, ROW, 40, 0);
        assertFalse(p.above(), "embaixo há 119 px e em cima 110: nenhum lado cabe as 40, fica o maior");
        assertEquals((119 - 2 * TvDropdownPlace.PAD) / ROW, p.rows());
        inside(p);
        TvDropdownPlace up = TvDropdownPlace.of(new TvRect(10, 125, 64, 11), WINDOW, 40, ROW, 40, 0);
        assertTrue(up.above(), "em cima há 125 px e embaixo 104");
        inside(up);
    }

    @Test
    void oTetoDeLinhasValeMesmoComLugar() {
        TvDropdownPlace p = TvDropdownPlace.of(new TvRect(0, 0, 64, 11), WINDOW, 17, ROW, 12, 0);
        assertEquals(12, p.rows());
        assertFalse(p.above());
    }

    @Test
    void aListaMaisLargaQueOControleNaoPassaDaBordaDireita() {
        TvRect anchor = new TvRect(330, 40, 64, 11);
        TvDropdownPlace p = TvDropdownPlace.of(anchor, WINDOW, 5, ROW, 12, 110);
        assertEquals(110, p.w());
        assertEquals(WINDOW.right() - 110, p.x());
        inside(p);
    }

    @Test
    void aLarguraNuncaPassaDaJanela() {
        TvDropdownPlace p = TvDropdownPlace.of(new TvRect(0, 0, 64, 11), WINDOW, 3, ROW, 12, 900);
        assertEquals(WINDOW.w(), p.w());
        inside(p);
    }
}
