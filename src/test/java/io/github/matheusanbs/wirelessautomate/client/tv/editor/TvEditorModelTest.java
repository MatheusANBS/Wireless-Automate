package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TvEditorModelTest {
    @Test
    void mudouDesdeOSalvarPeloConteudo() {
        TvEditorModel<List<String>> model = new TvEditorModel<>(List.of("a"));
        assertFalse(model.dirty());
        model.edit(List.of("a", "b"), TvSelection.of(1));
        assertTrue(model.dirty());
        assertEquals(TvSelection.of(1), model.selection());
        model.edit(List.of("a"), TvSelection.EMPTY);
        assertFalse(model.dirty(), "voltar ao salvo não é mudança");
    }

    @Test
    void versaoSobeSoComRascunhoNovo() {
        List<String> layout = List.of("a");
        TvEditorModel<List<String>> model = new TvEditorModel<>(layout);
        int before = model.version();
        model.edit(layout, TvSelection.of(0));
        assertEquals(before, model.version(), "o mesmo rascunho só troca a escolha");
        model.edit(List.of("b"), TvSelection.EMPTY);
        assertEquals(before + 1, model.version());
    }

    @Test
    void baseNovaDoServidorERecarregar() {
        TvEditorModel<List<String>> model = new TvEditorModel<>(List.of("a"));
        model.edit(List.of("b"), TvSelection.of(0));
        model.rebase(List.of("b"));
        assertFalse(model.dirty());
        model.select(TvSelection.of(0), 4);
        assertEquals(4, model.focusGroup());
        model.load(List.of("c"));
        assertEquals(List.of("c"), model.draft());
        assertEquals(List.of("c"), model.saved());
        assertTrue(model.selection().isEmpty());
        assertEquals(0, model.focusGroup());
    }

    @Test
    void escolhaComoConjunto() {
        TvSelection s = TvSelection.of(3, 1, 3);
        assertEquals(2, s.size());
        assertEquals(1, s.first());
        assertEquals(TvSelection.of(1), s.toggle(3));
        assertEquals(TvSelection.of(1, 2, 3), s.toggle(2));
        assertEquals(TvSelection.of(1), s.below(2));
        assertEquals(TvSelection.of(4, 5), TvSelection.range(4, 6));
    }
}
