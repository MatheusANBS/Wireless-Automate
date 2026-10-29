package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

import java.util.ArrayDeque;

/**
 * O desfazer do editor de pixel art: até {@link #MAX} estados da arte guardados antes de cada passo (decisão 1f da
 * spec da etapa 4: um traço, uma edição de cor, o tamanho, virar, girar, limpar e cada mover de pedaço). Um passo
 * novo apaga o refazer, menos o passo que não mudou nada ({@link #dropIfUnchanged}), que o devolve. Guardar só
 * acontece nos eventos (apertar o mouse, uma tecla), nunca por quadro. Lógica pura, com JUnit.
 */
public final class TvPixelHistory {
    public static final int MAX = 100;

    private final ArrayDeque<TvPixelCanvas.Snapshot> past = new ArrayDeque<>();
    private ArrayDeque<TvPixelCanvas.Snapshot> future = new ArrayDeque<>();
    /** O refazer que o último {@link #push} tirou, até saber se o passo mudou algo; trocado com o {@link #future}. */
    private ArrayDeque<TvPixelCanvas.Snapshot> spare = new ArrayDeque<>();
    /** O último evento foi um {@link #push}: o {@link #spare} é o refazer de antes dele. */
    private boolean pushed;

    /** Guarda a arte de antes de um passo; o mais antigo sai acima de {@link #MAX}. O refazer fica de lado. */
    public void push(TvPixelCanvas.Snapshot before) {
        past.addLast(before);
        if (past.size() > MAX) {
            past.removeFirst();
        }
        spare.clear();
        ArrayDeque<TvPixelCanvas.Snapshot> swap = spare;
        spare = future;
        future = swap;
        pushed = true;
    }

    /**
     * O passo que acabou de começar não mudou nada (um clique fora, um balde na mesma cor): sai do histórico, e o
     * refazer que ele tirou volta.
     */
    public void dropIfUnchanged(TvPixelCanvas canvas) {
        if (!past.isEmpty() && canvas.same(past.peekLast())) {
            past.removeLast();
            if (pushed && future.isEmpty()) {
                ArrayDeque<TvPixelCanvas.Snapshot> swap = future;
                future = spare;
                spare = swap;
            }
        }
        spare.clear();
        pushed = false;
    }

    public boolean undo(TvPixelCanvas canvas) {
        settle();
        if (past.isEmpty()) {
            return false;
        }
        future.addLast(canvas.snapshot());
        canvas.restore(past.removeLast());
        return true;
    }

    public boolean redo(TvPixelCanvas canvas) {
        settle();
        if (future.isEmpty()) {
            return false;
        }
        past.addLast(canvas.snapshot());
        canvas.restore(future.removeLast());
        return true;
    }

    /** Volta o último passo sem guardá-lo para refazer (o Esc desfaz o mover de um pedaço solto). */
    public boolean cancel(TvPixelCanvas canvas) {
        settle();
        if (past.isEmpty()) {
            return false;
        }
        canvas.restore(past.removeLast());
        return true;
    }

    /** O passo do último {@link #push} vale como está: o refazer posto de lado sai. */
    private void settle() {
        spare.clear();
        pushed = false;
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
