package io.github.matheusanbs.wirelessautomate.network;

/**
 * Onde a volta do tick seguinte começa, quando a volta inteira coube no orçamento: logo depois da
 * última origem que moveu algo, como uma fila em que quem foi atendido vai para o fim. Se o espaço
 * nos destinos é pouco, cada tick serve as próximas da fila, e não sempre as primeiras da lista.
 * Se ninguém moveu, ou se a fila voltaria ao mesmo começo (todas moveram), anda uma origem.
 * Lógica pura, testada por JUnit.
 */
final class SourceCursor {
    /**
     * @param start     onde a volta deste tick começou
     * @param lastMover índice da última origem que moveu algo na volta, ou -1
     * @param count     origens na lista
     */
    static int next(int start, int lastMover, int count) {
        if (count <= 0) {
            return 0;
        }
        int step = start + 1 == count ? 0 : start + 1;
        if (lastMover < 0) {
            return step;
        }
        int next = lastMover + 1 == count ? 0 : lastMover + 1;
        return next == start ? step : next;
    }

    private SourceCursor() {
    }
}
