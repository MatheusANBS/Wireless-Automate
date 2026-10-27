package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import java.util.ArrayDeque;
import java.util.Deque;
import org.jetbrains.annotations.Nullable;

/**
 * O desfazer e o refazer do editor: até {@link #MAX} estados (o layout de antes e a escolha de antes), guardados
 * como referências a layouts imutáveis, sem cópia. Uma mudança nova zera o refazer. Um gesto (o arrastar, do apertar
 * ao soltar) conta como uma mudança só, e várias mudanças seguidas com a mesma chave (digitar num campo, as setas)
 * também. Pura, com JUnit; só em eventos, nunca por quadro.
 */
public final class TvEditHistory<D> {
    public static final int MAX = 100;

    /** Um estado a que se volta: o layout e a escolha de antes de uma mudança. */
    public record State<D>(D layout, TvSelection selection) {
    }

    private final Deque<State<D>> undo = new ArrayDeque<>();
    private final Deque<State<D>> redo = new ArrayDeque<>();
    private boolean gesture;
    private boolean gestureRecorded;
    private @Nullable Object lastKey;

    /**
     * Guarda o estado de antes de uma mudança. Dentro de um gesto só a primeira vale; com a mesma {@code key} da
     * mudança anterior (não nula) nada é guardado: ela continua a mesma.
     */
    public void record(D layout, TvSelection selection, @Nullable Object key) {
        if (gesture) {
            if (gestureRecorded) {
                return;
            }
            gestureRecorded = true;
            lastKey = null;
        } else {
            if (key != null && key.equals(lastKey)) {
                return;
            }
            lastKey = key;
        }
        undo.addLast(new State<>(layout, selection));
        if (undo.size() > MAX) {
            undo.removeFirst();
        }
        redo.clear();
    }

    /** O estado a que o desfazer volta, com o de agora indo para o refazer; {@code null} sem nada a desfazer. */
    public @Nullable State<D> undo(D layout, TvSelection selection) {
        State<D> state = undo.pollLast();
        if (state != null) {
            redo.addLast(new State<>(layout, selection));
            lastKey = null;
        }
        return state;
    }

    public @Nullable State<D> redo(D layout, TvSelection selection) {
        State<D> state = redo.pollLast();
        if (state != null) {
            undo.addLast(new State<>(layout, selection));
            lastKey = null;
        }
        return state;
    }

    /** O arrastar começa: tudo até {@link #endGesture} é uma mudança só. */
    public void beginGesture() {
        gesture = true;
        gestureRecorded = false;
    }

    public void endGesture() {
        gesture = false;
        gestureRecorded = false;
    }

    /** Fecha a sequência de mudanças com a mesma chave (o campo perdeu o foco): a próxima vira outra. */
    public void breakKey() {
        lastKey = null;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public int undoSize() {
        return undo.size();
    }

    public void clear() {
        undo.clear();
        redo.clear();
        gesture = false;
        gestureRecorded = false;
        lastKey = null;
    }
}
