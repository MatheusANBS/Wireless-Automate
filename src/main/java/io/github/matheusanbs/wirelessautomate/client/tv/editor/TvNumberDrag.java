package io.github.matheusanbs.wirelessautomate.client.tv.editor;

/**
 * As contas do arrasto sobre um campo numérico: cada {@link #PIXELS_PER_STEP} px para a direita soma um passo (para a
 * esquerda, tira), com Shift quatro vezes mais; o valor fica entre o mínimo e o máximo. Lógica pura, com JUnit.
 */
final class TvNumberDrag {
    static final int PIXELS_PER_STEP = 4;
    /** O deslocamento a partir do qual o clique vira arrasto. */
    static final int THRESHOLD = 3;

    private TvNumberDrag() {
    }

    static boolean started(int dx) {
        return Math.abs(dx) >= THRESHOLD;
    }

    static int value(int start, int dx, int step, int min, int max, boolean big) {
        long next = start + (long) (dx / PIXELS_PER_STEP) * step * (big ? 4 : 1);
        return (int) Math.max(min, Math.min(max, next));
    }
}
