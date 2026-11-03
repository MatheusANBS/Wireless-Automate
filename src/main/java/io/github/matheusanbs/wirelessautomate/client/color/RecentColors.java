package io.github.matheusanbs.wirelessautomate.client.color;

/**
 * As últimas cores fixas escolhidas em qualquer seletor (Tela de Sinal e Tablet), até {@link #MAX}, a mais nova
 * primeiro, lembradas só na sessão. Uma cor repetida sobe para o começo. Lógica pura, com JUnit.
 */
public final class RecentColors {
    public static final int MAX = 8;
    private static final int[] COLORS = new int[MAX];
    private static int size;

    private RecentColors() {
    }

    /** Guarda uma cor fixa visível; o papel do tema e a transparência não entram. */
    public static void push(int color) {
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

    public static int size() {
        return size;
    }

    public static int get(int index) {
        return COLORS[index];
    }

    public static void clear() {
        size = 0;
    }
}
