package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * O estado do editor: o rascunho (um layout imutável, genérico aqui para o JUnit), a base salva com que ele se
 * compara ("mudou desde o salvar"), a escolha e o grupo em que se entrou, e o {@link TvEditHistory} do desfazer. A
 * {@link #version()} sobe a cada rascunho novo (inclusive o desfazer e o refazer), para a miniatura e a lista de
 * Camadas saberem que precisam refazer, sem comparar o layout por quadro. Puro.
 */
public final class TvEditorModel<D> {
    private D draft;
    private D saved;
    private TvSelection selection = TvSelection.EMPTY;
    private int focusGroup;
    private boolean dirty;
    private int version;
    private final TvEditHistory<D> history = new TvEditHistory<>();

    public TvEditorModel(D layout) {
        this.draft = layout;
        this.saved = layout;
    }

    /** Começa de novo pelo {@code layout}: rascunho igual à base, nada escolhido. */
    public void load(D layout) {
        draft = layout;
        saved = layout;
        selection = TvSelection.EMPTY;
        focusGroup = 0;
        dirty = false;
        history.clear();
        version++;
    }

    /** Um rascunho novo e a escolha depois dele; o mesmo rascunho não conta como mudança. */
    public void edit(D layout, TvSelection selection) {
        edit(layout, selection, null);
    }

    /**
     * Como {@link #edit(Object, TvSelection)}, com a {@code key} da mudança: várias seguidas com a mesma chave
     * entram no desfazer como uma só.
     */
    public void edit(D layout, TvSelection selection, @Nullable Object key) {
        if (layout != draft) {
            history.record(draft, this.selection, key);
        }
        this.selection = selection;
        if (layout == draft) {
            return;
        }
        draft = layout;
        dirty = !Objects.equals(draft, saved);
        version++;
    }

    /** O rascunho trocado sem entrar no desfazer (o servidor mudou o que estava salvo e não há mudança local). */
    public void adopt(D layout, TvSelection selection) {
        this.selection = selection;
        if (layout == draft) {
            return;
        }
        draft = layout;
        dirty = !Objects.equals(draft, saved);
        version++;
    }

    /** Volta uma mudança; {@code false} sem nada a desfazer. */
    public boolean undo() {
        TvEditHistory.State<D> state = history.undo(draft, selection);
        return state != null && restore(state);
    }

    public boolean redo() {
        TvEditHistory.State<D> state = history.redo(draft, selection);
        return state != null && restore(state);
    }

    private boolean restore(TvEditHistory.State<D> state) {
        draft = state.layout();
        selection = state.selection();
        focusGroup = 0;
        dirty = !Objects.equals(draft, saved);
        version++;
        return true;
    }

    public TvEditHistory<D> history() {
        return history;
    }

    public void select(TvSelection selection, int focusGroup) {
        this.selection = selection;
        this.focusGroup = focusGroup;
    }

    /** A base nova que o servidor confirmou; o rascunho continua o mesmo. */
    public void rebase(D layout) {
        saved = layout;
        dirty = !Objects.equals(draft, saved);
    }

    public D draft() {
        return draft;
    }

    public D saved() {
        return saved;
    }

    public TvSelection selection() {
        return selection;
    }

    public int focusGroup() {
        return focusGroup;
    }

    public boolean dirty() {
        return dirty;
    }

    public int version() {
        return version;
    }
}
