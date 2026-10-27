package io.github.matheusanbs.wirelessautomate.tv;

/**
 * Uma cor do layout: um ARGB fixo ou um papel do tema. O papel é guardado como {@code 0x0000000N} (N de 1 a 6, alfa
 * zero), o que nenhum ARGB visível usa; {@code 0} é transparente e não é papel.
 */
public final class TvColor {
    public static final int BG = 1;
    public static final int CARD = 2;
    public static final int TEXT = 3;
    public static final int MUTED = 4;
    public static final int HIGH = 5;
    public static final int LOW = 6;

    private TvColor() {
    }

    /** O valor guardado do papel (1 a 6). */
    public static int theme(int role) {
        return role;
    }

    public static boolean isTheme(int color) {
        return color >= BG && color <= LOW;
    }

    /** O papel (1 a 6) de uma cor de tema, ou 0 se é uma cor fixa. */
    public static int role(int color) {
        return isTheme(color) ? color : 0;
    }

    /** O ARGB a desenhar: o papel pela paleta do tema, a cor fixa igual. */
    public static int resolve(int color, TvTheme theme) {
        return isTheme(color) ? theme.color(color) : color;
    }
}
