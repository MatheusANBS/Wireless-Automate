package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * O que o cliente sabe da sintonia sem conhecer a estação: o desvio em passos de 0,05 MHz (só dentro de ±2 MHz,
 * o que o ponteiro mostra), a faixa da eficiência (8 faixas, para a câmara) e a lâmpada. Lógica pura.
 */
public final class TuneSignal {
    /** Fora de ±2 MHz o ponteiro não tem o que mostrar. */
    public static final int NO_SIGNAL = 999;
    public static final int BANDS = 8;
    /** O platô de ±0,25 MHz em passos do dial. */
    public static final int PLATEAU_STEPS = (int) Math.round(Tuning.PLATEAU / Tuning.DIAL_STEP);
    /**
     * Folga das bordas: sem ela, o erro de ponto flutuante (0,25000000000000006, 3,0000000000000004) apaga a lâmpada
     * ou sobe a faixa enquanto o ponteiro mostra o valor exato.
     */
    private static final double EPSILON = 1e-9;

    private TuneSignal() {
    }

    public static int steps(double station, double dial) {
        double deviation = station - dial;
        if (Math.abs(deviation) >= Tuning.ZERO) {
            return NO_SIGNAL;
        }
        return (int) Math.round(deviation / Tuning.DIAL_STEP);
    }

    /** 0 só a 0%; 7 acima de 6/7. */
    public static int band(double efficiency) {
        double e = Math.max(0, Math.min(1, efficiency));
        return (int) Math.ceil(e * (BANDS - 1) - EPSILON);
    }

    public static boolean tuned(double station, double dial) {
        return Math.abs(station - dial) <= Tuning.PLATEAU + EPSILON;
    }
}
