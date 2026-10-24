package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * O anel da câmara, puro: o lugar de cada cristal, nos eixos do mundo, orbitando a esfera devagar (uma volta a cada
 * {@link #ORBIT_TICKS}, para o cristal de trás não ficar sempre atrás), a semente de cada câmara e o tempo do
 * {@code GameTime} do shader em segundos. O ângulo da órbita sai do resto do tick pelo período, que é inteiro: nunca
 * salta.
 */
final class ChamberRing {
    static final double RADIUS = 0.95;
    static final double HEIGHT = -0.15;
    /** Uma volta da órbita, em ticks (42 s). */
    static final long ORBIT_TICKS = 840;
    private static final double TAU = 2 * Math.PI;

    private ChamberRing() {
    }

    /** O ângulo do cristal {@code i} de {@code count} no anel, sem a órbita. */
    static double angle(int i, int count) {
        return TAU * i / count + Math.PI / 4;
    }

    /** O giro da órbita em {@code ticks} (com a fração do tick), em radianos. */
    static double orbit(double ticks) {
        long whole = (long) Math.floor(ticks);
        return TAU * (Math.floorMod(whole, ORBIT_TICKS) + (ticks - whole)) / ORBIT_TICKS;
    }

    /**
     * O lugar do cristal {@code i} de {@code count} em {@code ticks} (com a fração do tick), relativo ao centro, em
     * {@code out}, que é devolvido.
     */
    static double[] position(int i, int count, double ticks, double[] out) {
        double a = angle(i, count) + orbit(ticks);
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
