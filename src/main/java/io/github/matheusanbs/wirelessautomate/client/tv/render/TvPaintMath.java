package io.github.matheusanbs.wirelessautomate.client.tv.render;

/** As contas puras do desenho da Tela: opacidade, cantos cortados e a fase do piscar. Sem classes do jogo. */
public final class TvPaintMath {
    /** Quantos ticks dura cada fase do piscar: dois redesenhos por segundo, aceso e apagado de meio em meio segundo. */
    public static final int BLINK_TICKS = 10;
    /** O menor alfa que o texto do jogo desenha: abaixo disso o {@code Font} o trata como opaco. */
    public static final int MIN_TEXT_ALPHA = 4;

    private TvPaintMath() {
    }

    /** O ARGB com o alfa multiplicado pela opacidade (0 a 100%); 100 devolve a mesma cor. */
    public static int withOpacity(int argb, int opacity) {
        if (opacity >= 100) {
            return argb;
        }
        int alpha = (argb >>> 24) * Math.max(opacity, 0) / 100;
        return alpha << 24 | argb & 0xFFFFFF;
    }

    /**
     * Quantos pixels de desenho a linha {@code row} (0 em cima) de um retângulo de raio {@code radius} perde de cada
     * lado: o canto é um quarto de círculo em degraus de 1 px; fora do canto, 0. {@code row} só vale de 0 a
     * {@code radius - 1} (a linha de baixo conta a partir da borda de baixo).
     */
    public static int inset(int radius, int row) {
        if (row < 0 || row >= radius) {
            return 0;
        }
        double dy = radius - row - 0.5;
        return (int) Math.round(radius - Math.sqrt(radius * (double) radius - dy * dy));
    }

    /** O bloco de {@link #BLINK_TICKS} ticks em que o tempo do mundo está; muda a cada troca de fase. */
    public static long blinkSlot(long gameTime) {
        return Math.floorDiv(gameTime, (long) BLINK_TICKS);
    }

    /** Verdadeiro na fase apagada do piscar. */
    public static boolean blinkOff(long gameTime) {
        return (blinkSlot(gameTime) & 1) == 1;
    }
}
