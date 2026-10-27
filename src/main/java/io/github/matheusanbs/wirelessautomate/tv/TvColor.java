package io.github.matheusanbs.wirelessautomate.tv;

import org.jetbrains.annotations.Nullable;

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

    private static final char[] DIGITS = "0123456789ABCDEF".toCharArray();

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

    /** O texto de uma cor fixa: {@code #RRGGBB}, ou {@code #RRGGBBAA} com transparência; vazio no papel do tema e em 0. */
    public static String hex(int color) {
        if (color == 0 || isTheme(color)) {
            return "";
        }
        int alpha = color >>> 24;
        StringBuilder out = new StringBuilder(9).append('#');
        for (int shift = 20; shift >= 0; shift -= 4) {
            out.append(DIGITS[color >> shift & 0xF]);
        }
        if (alpha != 0xFF) {
            out.append(DIGITS[alpha >> 4]).append(DIGITS[alpha & 0xF]);
        }
        return out.toString();
    }

    /**
     * A cor de um texto {@code #RRGGBB} ou {@code #RRGGBBAA} (o {@code #} é opcional), ou {@code null} se não é uma
     * cor; alfa zero é a cor 0, transparente.
     */
    public static @Nullable Integer fromHex(String text) {
        String digits = text.startsWith("#") ? text.substring(1) : text;
        if (digits.length() != 6 && digits.length() != 8 || !digits.matches("[0-9a-fA-F]+")) {
            return null;
        }
        int rgb = Integer.parseInt(digits.substring(0, 6), 16);
        int alpha = digits.length() == 8 ? Integer.parseInt(digits.substring(6), 16) : 0xFF;
        return alpha == 0 ? 0 : alpha << 24 | rgb;
    }
}
