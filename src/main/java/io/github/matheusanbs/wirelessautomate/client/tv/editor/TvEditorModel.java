package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import java.util.Objects;

/**
 * O estado do editor: o rascunho (um layout imutável, genérico aqui para o JUnit), a base salva com que ele se
 * compara ("mudou desde o salvar"), a escolha e o grupo em que se entrou. A {@link #version()} sobe a cada rascunho
 * novo, para a miniatura e a lista de Camadas saberem que precisam refazer, sem comparar o layout por quadro. Puro.
 */
public final class TvEditorModel<D> {
    private D draft;
    private D saved;
    private TvSelection selection = TvSelection.EMPTY;
    private int focusGroup;
    private boolean dirty;
    private int version;

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
        version++;
    }

    /** Um rascunho novo e a escolha depois dele; o mesmo rascunho não conta como mudança. */
    public void edit(D layout, TvSelection selection) {
        this.selection = selection;
        if (layout == draft) {
            return;
        }
        draft = layout;
        dirty = !Objects.equals(draft, saved);
        version++;
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
