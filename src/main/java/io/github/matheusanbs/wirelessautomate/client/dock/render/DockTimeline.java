package io.github.matheusanbs.wirelessautomate.client.dock.render;

/**
 * As curvas do laço de 6 s da Doca, tiradas das duas animações do {@code docs/preview/doca-de-padroes.bbmodel}
 * ({@code animation.doca.idle} e {@code animation.doca.trabalhando}, assadas de 0,05 em 0,05 s). Puro: o tempo
 * {@code s} é o segundo do laço (0 a 6) e os valores saem nas unidades do Blockbench (graus e pixels do modelo).
 *
 * <p>Os giros, os balanços e o sobe e desce são lineares e senoides; o pacote e os fragmentos andam e mudam de
 * escala em trechos {@code smoothstep} (cada trecho: começo, fim, valor de partida e de chegada); a escala do cristal
 * no trabalho é a única curva irregular e vem da própria tabela dos keyframes ({@link #CRYSTAL_SCALE_WORK}), interpolada em
 * linha reta como no Blockbench. No começo e no fim do laço todas as peças do trabalho estão no mesmo ponto (escala 0
 * do pacote e dos fragmentos, cristal em 1): o laço emenda nele mesmo e no repouso.
 */
final class DockTimeline {
    /** O laço, em segundos. */
    static final double LOOP = 6;
    /** Os giros por laço, em graus, como nos keyframes: o repouso e o trabalho do anel de latão e do cristal. */
    static final double BRASS_REST = 360;
    static final double BRASS_WORK = 720;
    static final double CRYSTAL_REST = -360;
    static final double CRYSTAL_WORK = -720;
    /** Os anéis de porcelana e de grafite giram igual nos dois laços. */
    static final double PORCELAIN = -360;
    static final double GRAPHITE = 360;
    /** O pacote em torno de si e o grupo dos fragmentos em torno do centro, só no trabalho. */
    static final double PACKET_SPIN = 2160;
    static final double FRAGMENT_SPIN = 720;
    /** O raio da órbita dos fragmentos, em pixels. */
    static final double FRAGMENT_ORBIT = 3;

    /** O pacote ao longo do caminho da vigia (x = 7,5) ao centro (−7,5): entra e volta. */
    private static final double[] PACKET_OFFSET = {
        0.2, 1.2, 0, -7.5,
        4.6, 5.7, -7.5, 0,
    };
    /** O pacote aparece, encolhe um pouco, é absorvido, reaparece no centro, cresce e some na vigia. */
    private static final double[] PACKET_SCALE = {
        0.05, 0.3, 0, 1,
        0.3, 1.0, 1, 0.9,
        1.0, 1.25, 0.9, 0,
        4.55, 4.75, 0, 0.9,
        4.75, 5.45, 0.9, 1,
        5.45, 5.8, 1, 0,
    };
    /** O deslocamento de cada fragmento a partir da órbita: nasce no centro, sai a 6,6 e volta em espiral. */
    private static final double[] FRAGMENT_OFFSET = {
        1.4, 1.8, -3, 0,
        2.3, 2.8, 0, 3.6,
        3.3, 4.4, 3.6, -3,
    };
    private static final double[] FRAGMENT_SCALE = {
        1.4, 1.75, 0, 1,
        2.3, 2.8, 1, 0,
        3.3, 3.5, 0, 0.7,
        3.5, 4.2, 0.7, 0.5,
        4.2, 4.4, 0.5, 0,
    };
    /** A escala do cristal no trabalho, de 0,05 em 0,05 s (121 amostras, a última igual à primeira). */
    private static final float[] CRYSTAL_SCALE_WORK = {
        1f, 1.0062f, 1.0122f, 1.0176f, 1.0223f, 1.026f, 1.0285f, 1.0298f, 1.0298f, 1.0285f, 1.026f, 1.0223f, 1.0176f,
        1.0122f, 1.0062f, 1f, 0.9938f, 0.9878f, 0.9824f, 0.9777f, 0.974f, 0.9715f, 1.1041f, 1.2177f, 1.2948f, 1.324f,
        1.3011f, 1.2299f, 1.1217f, 0.9938f, 1f, 1.0062f, 1.0122f, 1.0099f, 0.9942f, 0.969f, 0.9385f, 0.9068f, 0.878f,
        0.8563f, 0.846f, 0.8423f, 0.8376f, 0.8322f, 0.8262f, 0.82f, 0.8138f, 0.8078f, 0.8024f, 0.7977f, 0.794f,
        0.7915f, 0.7902f, 0.7902f, 0.7915f, 0.794f, 0.7977f, 0.8024f, 0.8078f, 0.8138f, 0.82f, 0.8262f, 0.8322f,
        0.8376f, 0.8423f, 0.846f, 0.8485f, 0.8498f, 0.8498f, 0.8485f, 0.846f, 0.8423f, 0.8376f, 0.8322f, 0.8262f,
        0.82f, 0.8138f, 0.8078f, 0.8024f, 0.7977f, 0.794f, 0.7965f, 0.8089f, 0.829f, 0.8548f, 0.884f, 0.9526f,
        1.0142f, 1.0615f, 1.0887f, 1.0924f, 1.0769f, 1.0505f, 1.0176f, 1.0223f, 1.026f, 1.0285f, 1.0298f, 1.0298f,
        1.0285f, 1.026f, 1.0223f, 1.0176f, 1.0122f, 1.0062f, 1f, 0.9938f, 0.9878f, 0.9824f, 0.9777f, 0.974f,
        0.9715f, 0.9702f, 0.9702f, 0.9715f, 0.974f, 0.9777f, 0.9824f, 0.9878f, 0.9938f, 1f,
    };
    private static final double SAMPLE = 0.05;

    private DockTimeline() {
    }

    /** O balanço do anel de latão em x, em graus: ±6 em 6 s no repouso, ±10 em 3 s no trabalho, pela velocidade. */
    static double brassTilt(double s, double speed) {
        return (1 - speed) * 6 * wave(s, 6) + speed * 10 * wave(s, 3);
    }

    /** O sobe e desce do cristal, em pixels: ±0,3 no repouso e ±0,25 no trabalho, os dois em 3 s. */
    static double crystalLift(double s, double speed) {
        return ((1 - speed) * 0.3 + speed * 0.25) * wave(s, 3);
    }

    /** A escala do cristal no repouso: respira ±5 % em 1,5 s. */
    static double crystalScaleRest(double s) {
        return 1 + 0.05 * wave(s, 1.5);
    }

    /** A escala do cristal no laço de trabalho: respira, absorve o pacote, solta os fragmentos e os reabsorve. */
    static double crystalScaleWork(double s) {
        double at = Math.max(0, Math.min(LOOP, s)) / SAMPLE;
        int i = Math.min((int) at, CRYSTAL_SCALE_WORK.length - 2);
        double f = at - i;
        return CRYSTAL_SCALE_WORK[i] + (CRYSTAL_SCALE_WORK[i + 1] - CRYSTAL_SCALE_WORK[i]) * f;
    }

    static double packetOffset(double s) {
        return segments(PACKET_OFFSET, s);
    }

    static double packetScale(double s) {
        return segments(PACKET_SCALE, s);
    }

    /** A distância de cada fragmento ao centro, em pixels. */
    static double fragmentRadius(double s) {
        return FRAGMENT_ORBIT + segments(FRAGMENT_OFFSET, s);
    }

    static double fragmentScale(double s) {
        return segments(FRAGMENT_SCALE, s);
    }

    /**
     * O valor dos trechos {@code {de, até, partida, chegada}} em {@code s}: antes do primeiro, a partida dele; num
     * trecho, o {@code smoothstep}; entre dois, a chegada do anterior.
     */
    static double segments(double[] parts, double s) {
        double value = parts[2];
        for (int k = 0; k < parts.length; k += 4) {
            double from = parts[k];
            if (s < from) {
                return value;
            }
            double to = parts[k + 1];
            if (s < to) {
                return parts[k + 2] + (parts[k + 3] - parts[k + 2]) * smoothstep((s - from) / (to - from));
            }
            value = parts[k + 3];
        }
        return value;
    }

    static double smoothstep(double x) {
        return x * x * (3 - 2 * x);
    }

    private static double wave(double s, double period) {
        return Math.sin(2 * Math.PI * s / period);
    }
}
