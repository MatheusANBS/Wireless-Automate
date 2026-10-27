package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TvEditOpsTest {
    /** Uma peça mínima: caixa, grupo, escondida e se é uma Forma (não ocupa lugar). */
    record P(String id, int x, int y, int w, int h, int g, boolean hidden, boolean shape) {
        P(String id, int x, int y, int w, int h, int g) {
            this(id, x, y, w, h, g, false, false);
        }
    }

    static final TvEditOps.Access<P> A = new TvEditOps.Access<>() {
        public int x(P p) {
            return p.x();
        }

        public int y(P p) {
            return p.y();
        }

        public int width(P p) {
            return p.w();
        }

        public int height(P p) {
            return p.h();
        }

        public int group(P p) {
            return p.g();
        }

        public boolean hidden(P p) {
            return p.hidden();
        }

        public boolean occupies(P p) {
            return !p.shape();
        }

        public P withBox(P p, int x, int y, int w, int h) {
            return new P(p.id(), x, y, w, h, p.g(), p.hidden(), p.shape());
        }

        public P withGroup(P p, int g) {
            return new P(p.id(), p.x(), p.y(), p.w(), p.h(), g, p.hidden(), p.shape());
        }
    };

    private static List<String> ids(List<P> pieces) {
        return pieces.stream().map(P::id).toList();
    }

    @Test
    void acertaAPecaDeCimaEPulaAEscondida() {
        List<P> pieces = List.of(new P("a", 0, 0, 20, 20, 0), new P("b", 10, 10, 20, 20, 0),
                new P("c", 10, 10, 20, 20, 0, true, false));
        assertEquals(1, TvEditOps.hit(A, pieces, 15, 15));
        assertEquals(0, TvEditOps.hit(A, pieces, 5, 5));
        assertEquals(-1, TvEditOps.hit(A, pieces, 40, 40));
    }

    @Test
    void cliqueEscolheOGrupoInteiroEDoisCliquesEntram() {
        List<P> pieces = List.of(new P("a", 0, 0, 8, 8, 1), new P("b", 8, 0, 8, 8, 1), new P("c", 20, 0, 8, 8, 0));
        TvEditOps.Pick pick = TvEditOps.click(A, pieces, TvSelection.EMPTY, 0, 0, false, false);
        assertEquals(TvSelection.of(0, 1), pick.selection());
        assertEquals(0, pick.focusGroup());
        TvEditOps.Pick inside = TvEditOps.click(A, pieces, pick.selection(), 0, 1, false, true);
        assertEquals(TvSelection.of(1), inside.selection());
        assertEquals(1, inside.focusGroup());
        TvEditOps.Pick other = TvEditOps.click(A, pieces, inside.selection(), 1, 0, false, false);
        assertEquals(TvSelection.of(0), other.selection(), "dentro do grupo, um clique escolhe só a peça");
        TvEditOps.Pick loose = TvEditOps.click(A, pieces, other.selection(), other.focusGroup(), 2, false, false);
        assertEquals(TvSelection.of(2), loose.selection());
        TvEditOps.Pick none = TvEditOps.click(A, pieces, loose.selection(), 0, -1, false, false);
        assertTrue(none.selection().isEmpty());
    }

    @Test
    void shiftSomaETiraEJaEscolhidaMantem() {
        List<P> pieces = List.of(new P("a", 0, 0, 8, 8, 0), new P("b", 8, 0, 8, 8, 0));
        TvSelection both = TvEditOps.click(A, pieces, TvSelection.of(0), 0, 1, true, false).selection();
        assertEquals(TvSelection.of(0, 1), both);
        assertEquals(both, TvEditOps.click(A, pieces, both, 0, 0, false, false).selection());
        assertEquals(TvSelection.of(1), TvEditOps.click(A, pieces, both, 0, 0, true, false).selection());
    }

    @Test
    void lugarLivreIgnoraFormaEEscondida() {
        List<P> pieces = List.of(new P("fundo", 0, 0, 64, 64, 0, false, true), new P("a", 0, 0, 40, 12, 0),
                new P("x", 40, 0, 24, 12, 0, true, false));
        assertArrayEquals(new int[] {40, 0}, TvEditOps.freeSpot(A, pieces, 20, 10, 64, 64));
        assertArrayEquals(new int[] {0, 12}, TvEditOps.freeSpot(A, pieces, 48, 10, 64, 64));
    }

    @Test
    void lugarLivreSemEspacoDaOCanto() {
        List<P> pieces = List.of(new P("a", 0, 0, 32, 32, 0));
        assertArrayEquals(new int[] {0, 0}, TvEditOps.freeSpot(A, pieces, 20, 20, 32, 32));
    }

    @Test
    void inserirPoeEmCimaNoLugarLivreECabeNaArea() {
        List<P> pieces = List.of(new P("a", 0, 0, 40, 12, 0));
        TvEditOps.Edit<P> edit = TvEditOps.insert(A, pieces, new P("n", 99, 99, 48, 10, 0), 64, 32);
        assertEquals(List.of("a", "n"), ids(edit.pieces()));
        assertEquals(new P("n", 0, 12, 48, 10, 0), edit.pieces().get(1));
        assertEquals(TvSelection.of(1), edit.selection());
        TvEditOps.Edit<P> big = TvEditOps.insert(A, List.of(), new P("g", 0, 0, 100, 100, 0), 32, 32);
        assertEquals(new P("g", 0, 0, 32, 32, 0), big.pieces().get(0));
    }

    @Test
    void moverNaoSaiDaArea() {
        List<P> pieces = List.of(new P("a", 4, 4, 8, 8, 0), new P("b", 16, 4, 8, 8, 0), new P("c", 0, 20, 4, 4, 0));
        TvSelection both = TvSelection.of(0, 1);
        List<P> moved = TvEditOps.move(A, pieces, both, 8, 4, 64, 32);
        assertEquals(12, moved.get(0).x());
        assertEquals(24, moved.get(1).x());
        assertEquals(8, moved.get(0).y());
        assertSame(pieces.get(2), moved.get(2));
        List<P> clamped = TvEditOps.move(A, pieces, both, 100, -100, 64, 32);
        assertEquals(64 - 8, clamped.get(1).x(), "a borda direita da escolha para na área");
        assertEquals(0, clamped.get(0).y());
        assertEquals(44, clamped.get(0).x(), "as duas andam juntas");
    }

    @Test
    void redimensionarTemMinimoENaoPassaDaArea() {
        List<P> pieces = List.of(new P("a", 8, 8, 16, 8, 0));
        TvSelection one = TvSelection.of(0);
        assertEquals(new P("a", 8, 8, 24, 12, 0), TvEditOps.resize(A, pieces, one, 8, 4, 64, 32).get(0));
        assertEquals(new P("a", 8, 8, TvSnap.MIN_SIZE, TvSnap.MIN_SIZE, 0),
                TvEditOps.resize(A, pieces, one, -100, -100, 64, 32).get(0));
        assertEquals(new P("a", 8, 8, 56, 24, 0), TvEditOps.resize(A, pieces, one, 500, 500, 64, 32).get(0));
    }

    @Test
    void redimensionarGrupoEmProporcao() {
        List<P> pieces = List.of(new P("a", 0, 0, 8, 8, 1), new P("b", 8, 0, 8, 8, 1));
        List<P> out = TvEditOps.resize(A, pieces, TvSelection.of(0, 1), 16, 0, 64, 32);
        assertEquals(new P("a", 0, 0, 16, 8, 1), out.get(0));
        assertEquals(new P("b", 16, 0, 16, 8, 1), out.get(1));
        assertTrue(TvEditOps.resizable(A, pieces, TvSelection.of(0, 1)));
        assertFalse(TvEditOps.resizable(A, List.of(pieces.get(0), pieces.get(1), new P("c", 0, 0, 4, 4, 0)),
                TvSelection.of(0, 2)));
    }

    @Test
    void removerTiraAsEscolhidas() {
        List<P> pieces = List.of(new P("a", 0, 0, 4, 4, 0), new P("b", 0, 0, 4, 4, 0), new P("c", 0, 0, 4, 4, 0));
        TvEditOps.Edit<P> edit = TvEditOps.remove(pieces, TvSelection.of(0, 2));
        assertEquals(List.of("b"), ids(edit.pieces()));
        assertTrue(edit.selection().isEmpty());
    }

    @Test
    void duplicarDeslocaEFazGruposNovos() {
        List<P> pieces = List.of(new P("a", 0, 0, 8, 8, 2), new P("b", 8, 0, 8, 8, 2), new P("c", 60, 28, 4, 4, 0));
        TvEditOps.Edit<P> edit = TvEditOps.duplicate(A, pieces, TvSelection.of(0, 1, 2), 64, 32);
        assertEquals(6, edit.pieces().size());
        assertEquals(TvSelection.of(3, 4, 5), edit.selection());
        assertEquals(new P("a", 4, 4, 8, 8, 3), edit.pieces().get(3));
        assertEquals(new P("b", 12, 4, 8, 8, 3), edit.pieces().get(4));
        assertEquals(new P("c", 60, 28, 4, 4, 0), edit.pieces().get(5), "na borda, a cópia fica dentro da área");
    }

    @Test
    void subirEDescerCamada() {
        List<P> pieces = List.of(new P("a", 0, 0, 4, 4, 0), new P("b", 0, 0, 4, 4, 0), new P("c", 0, 0, 4, 4, 0));
        TvEditOps.Edit<P> up = TvEditOps.raise(pieces, TvSelection.of(0));
        assertEquals(List.of("b", "a", "c"), ids(up.pieces()));
        assertEquals(TvSelection.of(1), up.selection());
        TvEditOps.Edit<P> top = TvEditOps.raise(pieces, TvSelection.of(1, 2));
        assertEquals(List.of("a", "b", "c"), ids(top.pieces()), "a de cima não sobe mais e a vizinha escolhida espera");
        TvEditOps.Edit<P> down = TvEditOps.lower(pieces, TvSelection.of(2));
        assertEquals(List.of("a", "c", "b"), ids(down.pieces()));
        assertEquals(TvSelection.of(1), down.selection());
    }

    @Test
    void levarParaOutraCamadaPelaLista() {
        List<P> pieces = List.of(new P("a", 0, 0, 4, 4, 0), new P("b", 0, 0, 4, 4, 0), new P("c", 0, 0, 4, 4, 0),
                new P("d", 0, 0, 4, 4, 0));
        TvEditOps.Edit<P> top = TvEditOps.moveTo(pieces, TvSelection.of(0), 4);
        assertEquals(List.of("b", "c", "d", "a"), ids(top.pieces()));
        assertEquals(TvSelection.of(3), top.selection());
        TvEditOps.Edit<P> bottom = TvEditOps.moveTo(pieces, TvSelection.of(2, 3), 0);
        assertEquals(List.of("c", "d", "a", "b"), ids(bottom.pieces()));
        assertEquals(TvSelection.of(0, 1), bottom.selection());
        TvEditOps.Edit<P> middle = TvEditOps.moveTo(pieces, TvSelection.of(3), 1);
        assertEquals(List.of("a", "d", "b", "c"), ids(middle.pieces()));
    }

    @Test
    void agruparJuntaNaCamadaDaMaisAlta() {
        List<P> pieces = List.of(new P("a", 0, 0, 4, 4, 0), new P("b", 0, 0, 4, 4, 0), new P("c", 0, 0, 4, 4, 1),
                new P("d", 0, 0, 4, 4, 0));
        TvEditOps.Edit<P> edit = TvEditOps.group(A, pieces, TvSelection.of(0, 2));
        assertEquals(List.of("b", "a", "c", "d"), ids(edit.pieces()));
        assertEquals(TvSelection.of(1, 2), edit.selection());
        assertEquals(2, edit.pieces().get(1).g());
        assertEquals(2, edit.pieces().get(2).g());
        assertEquals(0, edit.pieces().get(0).g());
        TvEditOps.Edit<P> single = TvEditOps.group(A, pieces, TvSelection.of(1));
        assertSame(pieces, single.pieces(), "uma peça só não agrupa");
    }

    @Test
    void desagruparSoAsEscolhidas() {
        List<P> pieces = List.of(new P("a", 0, 0, 4, 4, 3), new P("b", 0, 0, 4, 4, 3));
        assertTrue(TvEditOps.anyGrouped(A, pieces, TvSelection.of(0)));
        TvEditOps.Edit<P> edit = TvEditOps.ungroup(A, pieces, TvSelection.of(0));
        assertEquals(0, edit.pieces().get(0).g());
        assertEquals(3, edit.pieces().get(1).g());
        assertFalse(TvEditOps.anyGrouped(A, edit.pieces(), TvSelection.of(0)));
    }

    @Test
    void caixaDaEscolhaEMembrosDoGrupo() {
        List<P> pieces = List.of(new P("a", 4, 8, 8, 8, 1), new P("b", 20, 0, 4, 4, 0), new P("c", 0, 12, 4, 20, 1));
        assertArrayEquals(new int[] {0, 8, 12, 32}, TvEditOps.bounds(A, pieces, TvSelection.of(0, 2)));
        assertEquals(TvSelection.of(0, 2), TvEditOps.members(A, pieces, 1));
        assertEquals(2, TvEditOps.nextGroup(A, pieces));
    }
}
