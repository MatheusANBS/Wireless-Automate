package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
import io.github.matheusanbs.wirelessautomate.resonator.Tuning;

/**
 * A escala do ponteiro de sintonia: a estação no centro e de −2 a +2 MHz até 16 px das bordas, como o mockup. O
 * ponteiro mostra onde o dial está em relação à estação (o desvio com o sinal trocado). Lógica pura.
 */
final class PointerScale {
    /** Até onde a escala vai para cada lado; fora disso o servidor manda {@link TuneSignal#NO_SIGNAL}. */
    static final double RANGE_MHZ = 2.0;
    /** O platô de ±0,25 MHz, em MHz. */
    static final double PLATEAU_MHZ = TuneSignal.PLATEAU_STEPS * Tuning.DIAL_STEP;
    /** A folga entre a ponta da escala e a borda da faixa. */
    private static final int EDGE = 16;
    private static final double EPSILON = 1e-9;

    private PointerScale() {
    }

    static double pixelsPerMhz(int width) {
        return (width / 2.0 - EDGE) / RANGE_MHZ;
    }

    /** A coluna de {@code mhz} numa faixa de {@code width} px, a partir da borda esquerda. */
    static int x(int width, double mhz) {
        return (int) Math.round(width / 2.0 + mhz * pixelsPerMhz(width));
    }

    /**
     * Onde o ponteiro fica, em MHz da escala, para o desvio em passos. Sem sinal devolve {@link #RANGE_MHZ}: quem
     * chama escolhe o lado (o do último sinal visto, ou a direita).
     */
    static double pointerMhz(int steps) {
        if (steps == TuneSignal.NO_SIGNAL) {
            return RANGE_MHZ;
        }
        return -steps * Tuning.DIAL_STEP;
    }

    /** As colunas das bordas do platô, esquerda e direita. */
    static int[] plateau(int width) {
        return new int[] {x(width, -PLATEAU_MHZ), x(width, PLATEAU_MHZ)};
    }

    /** Um traço a cada 0,5 MHz. */
    static boolean isTick(double mhz) {
        return isMultiple(mhz, 0.5);
    }

    /** Um traço maior com rótulo a cada 1 MHz. */
    static boolean isMajor(double mhz) {
        return isMultiple(mhz, 1.0);
    }

    private static boolean isMultiple(double value, double step) {
        double ratio = value / step;
        return Math.abs(ratio - Math.rint(ratio)) < EPSILON;
    }
}
