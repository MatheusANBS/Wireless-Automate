package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/** A pose das três órbitas, do {@code pose(i, t, u)} do filme ({@code partes.js}). Lógica pura. */
final class ArmilarPose {
    /** {inclinação, precessão, giro} de cada órbita, os {@code off} do filme. */
    private static final double[][] OFF = {{0.62, 0.3, 0.8}, {-0.8, -0.2, -0.55}, {0.95, 0.14, 0.4}};

    private ArmilarPose() {
    }

    /**
     * A inclinação da órbita: solta ({@code 1 − u}) com a oscilação lenta, mais o assentamento ao travar. {@code
     * sinceTuned} é o tempo desde que a lâmpada acendeu (no filme, {@code t − 10,6}); sem sintonia vista, um número
     * enorme.
     */
    static double tilt(int orbit, double t, double u, double sinceTuned) {
        double x = Math.max(0, sinceTuned);
        double sett = 0.06 * Math.exp(-x * 1.4) * Math.sin(x * 5.5 + orbit) * u;
        return (OFF[orbit][0] + 0.09 * Math.sin(t * 0.55 + orbit * 2)) * (1 - u) + sett;
    }

    static double prec(int orbit, double t) {
        return orbit * 2.1 + OFF[orbit][1] * t;
    }

    /** O quanto o giro anda por segundo: o próprio solto, e a volta dos anéis conforme a sintonia sobe. */
    static double omega(int orbit, double u) {
        return OFF[orbit][2] + u * (ArmilarSpec.S - OFF[orbit][2]);
    }

    /** O ângulo de giro em que as três órbitas estão alinhadas na conjunção; zero em {@code t = TC}. */
    static double aligned(double t) {
        return ArmilarSpec.S * (t - ArmilarSpec.TC);
    }
}
