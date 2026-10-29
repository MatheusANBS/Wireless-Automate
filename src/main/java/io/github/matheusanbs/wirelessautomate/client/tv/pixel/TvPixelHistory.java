package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

import java.util.ArrayDeque;

/**
 * O desfazer do editor de pixel art: até {@link #MAX} estados da arte guardados antes de cada passo (decisão 1f da
 * spec da etapa 4: um traço, uma edição de cor, o tamanho, virar, girar, limpar e cada mover de pedaço). Um passo
 * novo apaga o refazer. Guardar só acontece nos eventos (apertar o mouse, uma tecla), nunca por quadro. Lógica pura,
 * com JUnit.
 */
public final class TvPixelHistory {
    public static final int MAX = 100;

    private final ArrayDeque<TvPixelCanvas.Snapshot> past = new ArrayDeque<>();
    private final ArrayDeque<TvPixelCanvas.Snapshot> future = new ArrayDeque<>();

    /** Guarda a arte de antes de um passo; o mais antigo sai acima de {@link #MAX}. */
    public void push(TvPixelCanvas.Snapshot before) {
        past.addLast(before);
        if (past.size() > MAX) {
            past.removeFirst();
        }
        future.clear();
    }

    /** O passo que acabou de começar não mudou nada (um clique fora, um balde na mesma cor): sai do histórico. */
    public void dropIfUnchanged(TvPixelCanvas canvas) {
        if (!past.isEmpty() && canvas.same(past.peekLast())) {
            past.removeLast();
        }
    }

    public boolean undo(TvPixelCanvas canvas) {
        if (past.isEmpty()) {
            return false;
        }
        future.addLast(canvas.snapshot());
        canvas.restore(past.removeLast());
        return true;
    }

    public boolean redo(TvPixelCanvas canvas) {
        if (future.isEmpty()) {
            return false;
        }
        past.addLast(canvas.snapshot());
        canvas.restore(future.removeLast());
        return true;
    }

    /** Volta o último passo sem guardá-lo para refazer (o Esc desfaz o mover de um pedaço solto). */
    public boolean cancel(TvPixelCanvas canvas) {
        if (past.isEmpty()) {
            return false;
        }
        canvas.restore(past.removeLast());
        return true;
    }

    public int size() {
        return past.size();
    }

    public boolean canUndo() {
        return !past.isEmpty();
    }

    public boolean canRedo() {
        return !future.isEmpty();
    }
}
