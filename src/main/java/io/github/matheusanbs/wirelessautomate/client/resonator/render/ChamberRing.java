package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * O anel da câmara, puro: o lugar fixo de cada cristal, nos eixos do mundo, a semente de cada câmara e o tempo do
 * {@code GameTime} do shader em segundos.
 */
final class ChamberRing {
    static final double RADIUS = 0.95;
    static final double HEIGHT = -0.15;
    private static final double TAU = 2 * Math.PI;

    private ChamberRing() {
    }

    /** O ângulo do cristal {@code i} de {@code count} no anel, fixo nos eixos do mundo. */
    static double angle(int i, int count) {
        return TAU * i / count + Math.PI / 4;
    }

    /** O lugar do cristal {@code i} de {@code count}, relativo ao centro, em {@code out}, que é devolvido. */
    static double[] position(int i, int count, double[] out) {
        double a = angle(i, count);
        out[0] = Math.cos(a) * RADIUS;
        out[1] = HEIGHT;
        out[2] = Math.sin(a) * RADIUS;
        return out;
    }

    /** O desencontro fixo de cada câmara (o ângulo áureo), de 0 a 2π. */
    static double seed(int i) {
        return (0.7 + i * 2.399963) % TAU;
    }

    /** O tempo do {@code GameTime} do shader, em segundos: repete a cada 24000 ticks. */
    static double seconds(long gameTime, float partialTick) {
        return (Math.floorMod(gameTime, 24000L) + partialTick) / 20.0;
    }
}
