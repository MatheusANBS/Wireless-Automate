package io.github.matheusanbs.wirelessautomate.client.color;

import io.github.matheusanbs.wirelessautomate.tv.TvColor;

/**
 * O valor de um seletor de cor aberto: o guardado (ARGB, 0 = nenhuma ou o papel 1 a 6 do tema da Tela de Sinal,
 * {@link TvColor}) e o matiz, a saturação, o brilho e o alfa que o quadrado, as faixas e o campo mexem. Lógica pura,
 * com JUnit; a janela do seletor só converte o mouse em frações de 0 a 1 e chama daqui.
 */
public final class ColorState {
    private final boolean alpha;
    /** As cores ARGB dos papéis 1 a 6 do tema; zeros sem tema. */
    private final int[] themeColors;
    private int value;
    private float h;
    private float s;
    private float v = 1;
    private int a = 255;

    public ColorState(int value, boolean alpha, int[] themeColors) {
        this.alpha = alpha;
        this.themeColors = themeColors;
        take(value);
    }

    public int value() {
        return value;
    }

    public float h() {
        return h;
    }

    public float s() {
        return s;
    }

    public float v() {
        return v;
    }

    public int a() {
        return a;
    }

    /** O RGB opaco do matiz, saturação e brilho atuais (a cor do quadrado e da faixa de alfa). */
    public int rgb() {
        return 0xFF000000 | ColorHsv.toRgb(h, s, v);
    }

    /** Lê matiz, saturação, brilho e alfa de um valor guardado (papel do tema, 0 ou ARGB). */
    public void take(int stored) {
        value = stored;
        int argb = TvColor.isTheme(stored) ? themeColors[stored - 1] : stored;
        if (argb == 0) {
            return;
        }
        float[] hsv = ColorHsv.fromRgb(argb & 0xFFFFFF);
        h = hsv[0];
        s = hsv[1];
        v = hsv[2];
        a = alpha ? Math.max(1, argb >>> 24) : 255;
    }

    /** O ponto do quadrado, em frações da largura ({@code fx}) e da altura ({@code fy}). */
    public void square(double fx, double fy) {
        s = (float) clamp(fx, 0, 1);
        v = 1 - (float) clamp(fy, 0, 1);
        apply();
    }

    /** O ponto da faixa de matiz, em fração da altura; para antes de dar a volta ao vermelho. */
    public void hue(double fy) {
        h = (float) clamp(fy, 0, 0.9999);
        apply();
    }

    /** O ponto da faixa de alfa, em fração da altura (em cima, opaco); nunca zero, que é "nenhuma". */
    public void alpha(double fy) {
        a = Math.max(1, Math.min(255, Math.round((1 - (float) clamp(fy, 0, 1)) * 255)));
        apply();
    }

    /** O texto do campo hexadecimal; devolve se virou uma cor. O zero só vale com {@code allowNone}. */
    public boolean typed(String text, boolean allowNone) {
        Integer parsed = TvColor.fromHex(text);
        if (parsed == null || parsed == 0 && !allowNone) {
            return false;
        }
        take(parsed);
        return true;
    }

    /** Uma cor pega (conta-gotas, recente); transparente não vale. */
    public boolean picked(int argb) {
        if (argb >>> 24 == 0) {
            return false;
        }
        take(alpha ? argb : argb | 0xFF000000);
        return true;
    }

    /** Escolhe o papel {@code role} (1 a 6) do tema, ou 0 para nenhuma. */
    public void stored(int role) {
        value = role;
    }

    private void apply() {
        value = a << 24 | ColorHsv.toRgb(h, s, v);
    }

    private static double clamp(double x, double min, double max) {
        return x < min ? min : x > max ? max : x;
    }
}
