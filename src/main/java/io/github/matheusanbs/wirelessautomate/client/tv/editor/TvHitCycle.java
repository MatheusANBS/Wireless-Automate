package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import java.util.List;

/**
 * Alt + clique na miniatura: escolhe a peça de baixo na mesma posição, e cicla (depois da última volta à de cima).
 * Lógica pura, com JUnit.
 */
final class TvHitCycle {
    private TvHitCycle() {
    }

    /**
     * A peça que o próximo Alt + clique escolhe no ponto ({@code x}, {@code y}): a de cima, se nenhuma das peças ali
     * está escolhida; senão a primeira abaixo da escolhida mais alta; abaixo da última, a de cima de novo. -1 sem peça.
     */
    static <T> int next(TvEditOps.Access<T> a, List<T> pieces, double x, double y, TvSelection selection) {
        int first = -1;
        boolean passedChosen = false;
        for (int i = pieces.size() - 1; i >= 0; i--) {
            T p = pieces.get(i);
            if (a.hidden(p) || x < a.x(p) || x >= a.x(p) + a.width(p) || y < a.y(p) || y >= a.y(p) + a.height(p)) {
                continue;
            }
            if (first < 0) {
                first = i;
            }
            if (passedChosen) {
                return i;
            }
            if (selection.contains(i)) {
                passedChosen = true;
            }
        }
        return first;
    }
}
