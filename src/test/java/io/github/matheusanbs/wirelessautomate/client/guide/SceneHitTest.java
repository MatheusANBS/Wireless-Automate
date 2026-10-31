package io.github.matheusanbs.wirelessautomate.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SceneHitTest {
    /** Os 8 cantos de uma caixa projetada como num ângulo isométrico: cada eixo vai para uma direção da folha. */
    private static float[] isoBox() {
        float[] pts = new float[16];
        for (int c = 0; c < 8; c++) {
            float x = (c & 1) == 0 ? 0 : 1;
            float y = (c & 2) == 0 ? 0 : 1;
            float z = (c & 4) == 0 ? 0 : 1;
            pts[c * 2] = 10 * (x - z) + 100;
            pts[c * 2 + 1] = 5 * (x + z) - 10 * y + 100;
        }
        return pts;
    }

    @Test
    void corDaPaginaOuCoral() {
        assertEquals(0xFF4A8CFF, SceneHit.color("#4a8cff"));
        assertEquals(SceneHit.CORAL, SceneHit.color(""));
        assertEquals(SceneHit.CORAL, SceneHit.color(null));
        assertEquals(SceneHit.CORAL, SceneHit.color("#zzzzzz"));
        assertEquals(SceneHit.CORAL, SceneHit.color("4a8cff"));
    }

    @Test
    void corRealcadaFicaMaisClara() {
        assertEquals(0xFF555555, SceneHit.lighter(0xFF000000));
        assertEquals(0xFFFFFFFF, SceneHit.lighter(0xFFFFFFFF));
        int c = SceneHit.lighter(0xFF4A8CFF);
        assertTrue(((c >> 16) & 0xFF) > 0x4A && ((c >> 8) & 0xFF) > 0x8C);
    }

    @Test
    void distanciaAoSegmento() {
        assertEquals(3, SceneHit.segment(5, 3, 0, 0, 10, 0), 1e-9);
        assertEquals(5, SceneHit.segment(13, 4, 0, 0, 10, 0), 1e-9);
        assertEquals(5, SceneHit.segment(-3, -4, 0, 0, 10, 0), 1e-9);
        assertEquals(5, SceneHit.segment(3, 4, 0, 0, 0, 0), 1e-9);
    }

    @Test
    void pontoNaCaixaProjetada() {
        float[] pts = new float[32];
        System.arraycopy(isoBox(), 0, pts, 16, 16);
        // o centro da projeção e um ponto de cada face visível
        assertTrue(SceneHit.inBox(pts, 16, 100, 100));
        assertTrue(SceneHit.inBox(pts, 16, 100, 92));
        assertTrue(SceneHit.inBox(pts, 16, 95, 104));
        assertTrue(SceneHit.inBox(pts, 16, 105, 104));
        // fora: além das pontas e acima do topo
        assertFalse(SceneHit.inBox(pts, 16, 115, 100));
        assertFalse(SceneHit.inBox(pts, 16, 85, 100));
        assertFalse(SceneHit.inBox(pts, 16, 100, 85));
    }

    @Test
    void faceDeLadoNaoConta() {
        // a caixa achatada numa linha (vista de lado por todos os eixos): um ponto sobre a linha não está dentro
        float[] pts = new float[16];
        for (int c = 0; c < 8; c++) {
            pts[c * 2] = c;
            pts[c * 2 + 1] = 0;
        }
        assertFalse(SceneHit.inBox(pts, 0, 3, 0));
    }
}
