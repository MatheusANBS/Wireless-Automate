package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * As três luzes que a Armilar joga no mundo, do {@code luzes(t, {a, u})} do filme ({@code partes.js}): o sol, o clarão
 * da conjunção no horizonte e a Porta de Energia. Lógica pura, em px locais; quem anota converte para o mundo.
 */
final class ArmilarLights {
    static final int COUNT = 3;
    /** Os campos de cada luz em {@code out}. */
    static final int X = 0, Y = 1, Z = 2, R = 3, G = 4, B = 5, K = 6, RADIUS = 7;
    /** Os raios do filme, em px. */
    static final double SUN_RADIUS = 64;
    static final double HORIZON_RADIUS = 26;
    static final double DOOR_RADIUS = 34;
    /** A luz da Porta com a extração contínua e sem conjunção, na força 1: metade da cheia; o pulso completa o resto. */
    static final double DOOR_BASE = 0.5;

    private ArmilarLights() {
    }

    /**
     * {@code {x, y, z (px), r, g, b, k, raio px}} do sol, do clarão do horizonte e da Porta. {@code a} é a montagem,
     * {@code flowK} a força da vazão (só a Porta depende dela: o fluxo base acende metade e o pulso da conjunção, por cima, o resto) e {@code e.prep} a preparação da conjunção: a pressão do
     * buffer cheio não escurece a luz, o filme não faz.
     */
    static void compute(double t, double a, double flowK, ArmilarPhase e, double[][] out) {
        double sun = 0.75 * a * (0.92 + 0.08 * Math.sin(t * 1.7)) * (1 - 0.3 * e.prep) + 0.5 * e.raio;
        set(out[0], 0, ArmilarSpec.SOL_Y, 0, 1, 0.55, 0.36, sun, SUN_RADIUS);
        set(out[1], 32.5, 47.5, 0, 1, 0.8, 0.35, 1.4 * e.clarao, HORIZON_RADIUS);
        set(out[2], ArmilarSpec.PORTA_LUZ[0], ArmilarSpec.PORTA_LUZ[1], ArmilarSpec.PORTA_LUZ[2], 1, 0.75, 0.3,
                1.6 * ArmilarFlow.combine(DOOR_BASE * flowK, e.porta * flowK), DOOR_RADIUS);
    }

    private static void set(double[] o, double x, double y, double z, double r, double g, double b, double k,
            double radius) {
        o[X] = x;
        o[Y] = y;
        o[Z] = z;
        o[R] = r;
        o[G] = g;
        o[B] = b;
        o[K] = k;
        o[RADIUS] = radius;
    }
}
