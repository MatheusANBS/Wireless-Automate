package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * A figura de Lissajous da câmara, sem classes do Minecraft. Em sintonia ({@code q = 1}) as frequências ficam em
 * 3 : 2 : 1 e a figura fecha a cada volta de {@code u}; fora dela, as frequências oscilam com o tempo e a figura
 * escorrega. As amplitudes cabem no miolo 3 × 3 × 3 da câmara, centrado na origem.
 */
final class LissajousCurve {
    /** Os pontos da cauda, da ponta mais velha (0) à cabeça ({@code POINTS - 1}). */
    static final int POINTS = 260;
    /** O comprimento da cauda em {@code u}: uma volta inteira. */
    static final double TAIL_RADIANS = 2 * Math.PI;
    /** Quanto tempo a cauda carrega: a ponta velha usa as frequências de 3 s atrás. */
    static final double TAIL_SECONDS = 3.0;

    private LissajousCurve() {
    }

    /** O ponto {@code {x, y, z}} no parâmetro {@code u}, com a sintonia {@code q} (0 a 1) no tempo {@code t} em segundos. */
    static double[] point(double u, double q, double t) {
        double[] xyz = new double[3];
        write(xyz, 0, u, q, t);
        return xyz;
    }

    /** Preenche {@code xyz} com os {@link #POINTS} pontos da cauda que termina em {@code head}, no tempo {@code time}. */
    static void fill(double[] xyz, double head, double q, double time) {
        int last = POINTS - 1;
        for (int i = 0; i < POINTS; i++) {
            double back = 1 - i / (double) last;
            write(xyz, i * 3, head - back * TAIL_RADIANS, q, time - back * TAIL_SECONDS);
        }
    }

    /** Quanto a cabeça anda em {@code u} por segundo: mais depressa em sintonia, uma volta em 3 s. */
    static double advance(double q) {
        return (0.45 + 0.55 * q) * 2 * Math.PI / 3;
    }

    private static void write(double[] xyz, int at, double u, double q, double t) {
        double dr = 1 - q;
        double w1 = 3 + 0.61 * dr * Math.sin(0.37 * t);
        double w2 = 2 + 0.43 * dr * Math.cos(0.29 * t);
        double w3 = 1 + 0.37 * dr * Math.sin(0.23 * t);
        xyz[at] = 0.78 * Math.sin(w1 * u + Math.PI / 2);
        xyz[at + 1] = 0.55 * Math.sin(w2 * u);
        xyz[at + 2] = 0.78 * Math.sin(w3 * u);
    }
}
