package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.GuiTheme;

/**
 * As cores de um tema das telas, uma por token do {@link GuiPaint} (spec
 * {@code docs/superpowers/specs/2026-10-14-tema-claro-escuro-design.md}). O claro é a identidade
 * "Porcelana e Sinal" de sempre; o escuro troca a porcelana por grafite quente e mantém o coral.
 * Lógica pura, sem classes do Minecraft, com JUnit.
 */
public record GuiPalette(
        int panel, int bevelLight, int inset, int bevelDark, int rowHover,
        int fg, int muted, int disabled, int tabIdle,
        int accent, int accentDeep, int ok, int warn, int danger, int paused, int brass, int ruleText,
        int tooltipMuted, int tooltipAccent, int darkText, int ring) {

    public static final GuiPalette LIGHT = new GuiPalette(
            0xFFF2EBDD, 0xFFFFFBF3, 0xFFDDD3BF, 0xFFBDB19A, 0xFFF8F3E8,
            0xFF2A2730, 0xFF7A7366, 0xFFB5AC9D, 0xFF5A5660,
            0xFFF0603E, 0xFFC2442A, 0xFF2F9D5C, 0xFFD88A1A, 0xFFD5453A, 0xFF6B6FD0, 0xFFB48E2E, 0xFF5A36B0,
            0xFFBDB19A, 0xFFFFB39E, 0xFF0F0E12, 0xFF1B1920);

    public static final GuiPalette DARK = new GuiPalette(
            0xFF26232B, 0xFF312D37, 0xFF1C1A20, 0xFF4A4550, 0xFF3A3640,
            0xFFECE5D6, 0xFFA79F90, 0xFF625C66, 0xFF9A9284,
            0xFFF26A48, 0xFFC2442A, 0xFF3DB56E, 0xFFE39A2E, 0xFFE55A4E, 0xFF8A8EE8, 0xFFD2AE4E, 0xFFB79AF5,
            0xFFBDB19A, 0xFFFFB39E, 0xFF0F0E12, 0xFF1B1920);

    public static GuiPalette of(GuiTheme theme) {
        return theme == GuiTheme.DARK ? DARK : LIGHT;
    }

    /** Contraste entre duas cores opacas, pela fórmula do WCAG 2 (1 a 21). */
    public static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int argb) {
        return 0.2126 * channel(argb >> 16) + 0.7152 * channel(argb >> 8) + 0.0722 * channel(argb);
    }

    private static double channel(int value) {
        double c = (value & 0xFF) / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
