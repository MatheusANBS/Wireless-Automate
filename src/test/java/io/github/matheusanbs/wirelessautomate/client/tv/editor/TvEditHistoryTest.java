package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TvEditHistoryTest {
    private static TvEditorModel<List<String>> model(String... start) {
        return new TvEditorModel<>(List.of(start));
    }

    @Test
    void desfazerERefazerNaOrdem() {
        TvEditorModel<List<String>> m = model("a");
        m.edit(List.of("a", "b"), TvSelection.of(1));
        m.edit(List.of("a", "b", "c"), TvSelection.of(2));
        assertTrue(m.undo());
        assertEquals(List.of("a", "b"), m.draft());
        assertEquals(TvSelection.of(1), m.selection(), "a escolha de antes volta junto");
        assertTrue(m.undo());
        assertEquals(List.of("a"), m.draft());
        assertFalse(m.dirty());
        assertFalse(m.undo(), "nada mais a desfazer");
        assertTrue(m.redo());
        assertTrue(m.redo());
        assertEquals(List.of("a", "b", "c"), m.draft());
        assertTrue(m.dirty());
        assertFalse(m.redo());
    }

    @Test
    void mudancaNovaZeraORefazer() {
        TvEditorModel<List<String>> m = model("a");
        m.edit(List.of("b"), TvSelection.EMPTY);
        m.undo();
        assertTrue(m.history().canRedo());
        m.edit(List.of("c"), TvSelection.EMPTY);
        assertFalse(m.history().canRedo());
        assertFalse(m.redo());
    }

    @Test
    void guardaNoMaximoCemEstados() {
        TvEditorModel<Integer> m = new TvEditorModel<>(0);
        for (int i = 1; i <= 130; i++) {
            m.edit(i, TvSelection.EMPTY);
        }
        assertEquals(TvEditHistory.MAX, m.history().undoSize());
        int steps = 0;
        while (m.undo()) {
            steps++;
        }
        assertEquals(100, steps);
        assertEquals(30, m.draft(), "os 30 mais antigos caíram");
    }

    @Test
    void arrastarContaComoUmaMudancaSo() {
        TvEditorModel<Integer> m = new TvEditorModel<>(0);
        m.history().beginGesture();
        m.edit(4, TvSelection.EMPTY);
        m.edit(8, TvSelection.EMPTY);
        m.edit(12, TvSelection.EMPTY);
        m.history().endGesture();
        assertEquals(1, m.history().undoSize());
        m.undo();
        assertEquals(0, m.draft());
        m.redo();
        assertEquals(12, m.draft());
    }

    @Test
    void arrastarSemMudarNaoGuardaNada() {
        TvEditorModel<Integer> m = new TvEditorModel<>(0);
        m.history().beginGesture();
        m.history().endGesture();
        assertFalse(m.history().canUndo());
    }

    @Test
    void mudancasComAMesmaChaveViramUma() {
        TvEditorModel<String> m = new TvEditorModel<>("");
        m.edit("a", TvSelection.EMPTY, "texto");
        m.edit("ab", TvSelection.EMPTY, "texto");
        m.edit("abc", TvSelection.EMPTY, "texto");
        assertEquals(1, m.history().undoSize());
        m.edit("x", TvSelection.EMPTY);
        m.edit("xy", TvSelection.EMPTY, "texto");
        assertEquals(3, m.history().undoSize(), "outra mudança no meio quebra a sequência");
        m.undo();
        assertEquals("x", m.draft());
        m.undo();
        m.undo();
        assertEquals("", m.draft());
    }

    @Test
    void perderOFocoFechaASequencia() {
        TvEditorModel<String> m = new TvEditorModel<>("");
        m.edit("a", TvSelection.EMPTY, "texto");
        m.history().breakKey();
        m.edit("ab", TvSelection.EMPTY, "texto");
        assertEquals(2, m.history().undoSize());
    }

    @Test
    void adotarNaoEntraNoDesfazerERecarregarLimpa() {
        TvEditorModel<String> m = new TvEditorModel<>("a");
        m.adopt("b", TvSelection.EMPTY);
        assertFalse(m.history().canUndo());
        m.edit("c", TvSelection.EMPTY);
        m.load("z");
        assertFalse(m.history().canUndo());
        assertNull(m.history().undo("z", TvSelection.EMPTY));
    }
}
