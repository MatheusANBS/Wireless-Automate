package io.github.matheusanbs.wirelessautomate.client.tv.editor;

/**
 * As últimas cores fixas escolhidas no seletor (até {@link #MAX}, a mais nova primeiro), lembradas só na sessão. Uma
 * cor repetida sobe para o começo. Lógica pura, com JUnit.
 */
final class TvRecentColors {
    static final int MAX = 8;
    private static final int[] COLORS = new int[MAX];
    private static int size;

    private TvRecentColors() {
    }

    /** Guarda uma cor fixa visível; o papel do tema e a transparência não entram. */
    static void push(int color) {
        if (color == 0 || color >>> 24 == 0) {
            return;
        }
        int at = 0;
        while (at < size && COLORS[at] != color) {
            at++;
        }
        if (at == size) {
            size = Math.min(MAX, size + 1);
            at = size - 1;
        }
        System.arraycopy(COLORS, 0, COLORS, 1, at);
        COLORS[0] = color;
    }

    static int size() {
        return size;
    }

    static int get(int index) {
        return COLORS[index];
    }

    static void clear() {
        size = 0;
    }
}
