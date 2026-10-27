package io.github.matheusanbs.wirelessautomate.tv;

/** Cortes de texto e de faixa que o modelo do layout aplica ao ler. */
final class TvText {
    private TvText() {
    }

    static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
