package io.github.matheusanbs.wirelessautomate.client.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * A fila dos efeitos do quadro: quem tem efeito à vista anota aqui durante o quadro, o {@link FxStages} desenha e
 * esvazia. Só na thread de render. A lista é uma só, reaproveitada.
 */
public final class FxQueue {
    private static final List<FxJob> JOBS = new ArrayList<>();

    private FxQueue() {
    }

    public static void submit(FxJob job) {
        JOBS.add(job);
    }

    public static boolean isEmpty() {
        return JOBS.isEmpty();
    }

    public static void clear() {
        JOBS.clear();
    }

    static int size() {
        return JOBS.size();
    }

    static FxJob get(int index) {
        return JOBS.get(index);
    }
}
