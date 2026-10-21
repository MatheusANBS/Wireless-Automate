package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * A conta da interferência entre Núcleos de Ressonância, sem classes do jogo: cada outro Núcleo montado a até o raio
 * (distância de Chebyshev) tira a penalidade da eficiência, até o teto. A perda vai em milésimos.
 */
public final class ResonatorInterference {
    /** A distância do vizinho mais perto quando nenhum conta. */
    public static final int NONE = -1;
    public static final int PERMILLE = 1000;

    private ResonatorInterference() {
    }

    /** A distância de Chebyshev entre dois pontos (o maior dos três desvios). */
    public static int distance(int ax, int ay, int az, int bx, int by, int bz) {
        return Math.max(Math.abs(ax - bx), Math.max(Math.abs(ay - by), Math.abs(az - bz)));
    }

    /** Se um vizinho a {@code distance} conta no raio {@code radius}; raio 0 (ou menor) desliga. */
    public static boolean counts(int distance, int radius) {
        return radius > 0 && distance <= radius;
    }

    /** A perda em milésimos com {@code neighbours} vizinhos: {@code min(max, n × penalty)}, entre 0 e 1000. */
    public static int lossPermille(int neighbours, double penalty, double max) {
        if (neighbours <= 0) {
            return 0;
        }
        double loss = Math.min(Math.max(0, max), neighbours * Math.max(0, penalty));
        return (int) Math.max(0, Math.min(PERMILLE, Math.round(loss * PERMILLE)));
    }

    /** O fator que multiplica a eficiência: 1 sem perda, 0 com perda total. */
    public static double factor(int lossPermille) {
        return (PERMILLE - Math.max(0, Math.min(PERMILLE, lossPermille))) / (double) PERMILLE;
    }

    /** A soma dos vizinhos de um Núcleo: quantos contam e a distância do mais perto deles. */
    public static final class Tally {
        private final int radius;
        private int count;
        private int nearest = NONE;

        public Tally(int radius) {
            this.radius = radius;
        }

        /** Soma o vizinho a {@code distance}, se ele conta no raio. */
        public void add(int distance) {
            if (counts(distance, radius)) {
                count++;
                if (nearest == NONE || distance < nearest) {
                    nearest = distance;
                }
            }
        }

        public int count() {
            return count;
        }

        public int nearest() {
            return nearest;
        }
    }
}
