package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvHsv;
import io.github.matheusanbs.wirelessautomate.tv.TvPixels;

/**
 * As rampas da paleta (decisão 1c da spec da etapa 4): a paleta de 16 é uma grade de 4 × 4 e cada linha é uma rampa,
 * do escuro à esquerda ao claro à direita (a linha 0 começa no transparente, que nunca entra). O clarear anda uma casa
 * para a direita na linha do pixel e o escurecer uma para a esquerda, nunca para outra linha. "Criar rampa" refaz a
 * linha da cor escolhida a partir dela, em HSV, com o desvio de matiz da pixel art: cada casa para a esquerda fica
 * {@link #VALUE_STEP} mais escura, {@link #SATURATION_STEP} mais saturada e {@link #HUE_STEP}° mais perto do azul
 * ({@link #DARK_HUE}°); para a direita, o contrário, puxando para o amarelo ({@link #LIGHT_HUE}°). Lógica pura, com
 * JUnit.
 */
public final class TvPixelRamp {
    public static final int ROW = 4;
    public static final double HUE_STEP = 14;
    public static final double SATURATION_STEP = 0.08;
    public static final double VALUE_STEP = 0.17;
    public static final double DARK_HUE = 235;
    public static final double LIGHT_HUE = 55;
    /** O brilho mínimo de uma casa criada, para a ponta escura não virar preto puro. */
    private static final double MIN_VALUE = 0.07;

    private TvPixelRamp() {
    }

    /** O índice depois de clarear ({@code dir} 1) ou escurecer (-1) uma casa; na ponta da linha, o mesmo. */
    public static int step(int index, int dir) {
        int next = index + dir;
        if (index == TvPixels.TRANSPARENT || next <= TvPixels.TRANSPARENT || next / ROW != index / ROW) {
            return index;
        }
        return next;
    }

    /**
     * As cores da linha de {@code index} refeitas como a rampa da cor dele, na própria {@code palette}; a casa
     * escolhida e a cor 0 ficam. O alfa da cor escolhida vale para a linha inteira.
     */
    public static void make(int[] palette, int index) {
        if (index == TvPixels.TRANSPARENT) {
            return;
        }
        int argb = palette[index];
        float[] hsv = TvHsv.fromRgb(argb & 0xFFFFFF);
        double hue = hsv[0] * 360.0;
        int alpha = argb & 0xFF000000;
        int row = index / ROW * ROW;
        for (int j = row; j < row + ROW; j++) {
            int k = j - index;
            if (j == TvPixels.TRANSPARENT || k == 0) {
                continue;
            }
            double h = toward(hue, k < 0 ? DARK_HUE : LIGHT_HUE, HUE_STEP * Math.abs(k));
            double s = clamp(hsv[1] - SATURATION_STEP * k, 0, 1);
            double v = clamp(hsv[2] + VALUE_STEP * k, MIN_VALUE, 1);
            palette[j] = alpha | TvHsv.toRgb((float) (h / 360.0), (float) s, (float) v);
        }
    }

    /** O matiz {@code from} andando até {@code max} graus pelo caminho mais curto em direção a {@code target}. */
    static double toward(double from, double target, double max) {
        double d = (target - from + 540) % 360 - 180;
        return (from + Math.signum(d) * Math.min(Math.abs(d), max) + 360) % 360;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
