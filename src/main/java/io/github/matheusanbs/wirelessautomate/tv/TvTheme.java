package io.github.matheusanbs.wirelessautomate.tv;

import java.util.List;

/** Um tema: um nome e as seis cores (fundo, cartão, texto, apagado, alto e baixo), todas ARGB. */
public record TvTheme(String name, int bg, int card, int text, int muted, int high, int low) {
    public static final int MAX_NAME = 24;

    /** O nome dos quatro prontos é a chave de tradução, não o texto. */
    public static final TvTheme GRAPHITE = new TvTheme("tv.theme.graphite", 0xFF0F1117, 0xFF1A1C24, 0xFFECE5D6,
            0xFF8D8577, 0xFF3DB56E, 0xFFF26A48);
    public static final TvTheme PHOSPHOR = new TvTheme("tv.theme.phosphor", 0xFF06100A, 0xFF0B1A10, 0xFF7CFF9E,
            0xFF2F8A4A, 0xFFB9FFCB, 0xFFE5FF7C);
    public static final TvTheme AMBER = new TvTheme("tv.theme.amber", 0xFF140C02, 0xFF211404, 0xFFFFB54A,
            0xFF8A5D1D, 0xFFFFD58A, 0xFFFF7A3A);
    public static final TvTheme PORCELAIN = new TvTheme("tv.theme.porcelain", 0xFFF2EBDD, 0xFFE4DAC6, 0xFF2A2730,
            0xFF7A7366, 0xFF2F9D5C, 0xFFD5453A);
    public static final List<TvTheme> PRESETS = List.of(GRAPHITE, PHOSPHOR, AMBER, PORCELAIN);
    public static final TvTheme DEFAULT = GRAPHITE;

    public TvTheme {
        name = TvText.cut(name, MAX_NAME);
    }

    /** O mesmo tema com a cor do {@code role} (1 a 6) trocada; o nome fica. */
    public TvTheme withColor(int role, int argb) {
        return switch (role) {
            case TvColor.BG -> new TvTheme(name, argb, card, text, muted, high, low);
            case TvColor.CARD -> new TvTheme(name, bg, argb, text, muted, high, low);
            case TvColor.MUTED -> new TvTheme(name, bg, card, text, argb, high, low);
            case TvColor.HIGH -> new TvTheme(name, bg, card, text, muted, argb, low);
            case TvColor.LOW -> new TvTheme(name, bg, card, text, muted, high, argb);
            default -> new TvTheme(name, bg, card, argb, muted, high, low);
        };
    }

    /** Se a paleta é a mesma, sem olhar o nome (o editor casa a paleta da Tela com um tema da lista). */
    public boolean sameColors(TvTheme other) {
        return bg == other.bg && card == other.card && text == other.text && muted == other.muted
                && high == other.high && low == other.low;
    }

    /** A cor do papel (1 a 6, as constantes do {@link TvColor}); papel fora da faixa dá o texto. */
    public int color(int role) {
        return switch (role) {
            case TvColor.BG -> bg;
            case TvColor.CARD -> card;
            case TvColor.MUTED -> muted;
            case TvColor.HIGH -> high;
            case TvColor.LOW -> low;
            default -> text;
        };
    }
}
