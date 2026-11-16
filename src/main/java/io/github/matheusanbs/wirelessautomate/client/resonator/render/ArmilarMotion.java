package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * O {@code u} suavizado e o giro de cada órbita, por tick (20 por segundo). No filme o giro é uma fórmula fechada com
 * {@code tL} fixo; no jogo o relógio é o do mundo e {@code u} pode ficar parado minutos em 3/7, então a fórmula
 * dispararia o anel ao travar. Por isso o giro é integrado e, perto da sintonia, travado na conjunção por um laço de
 * fase. Com {@code u = 1} o resultado é o do filme ({@code S·(t − TC)} módulo 2π). Lógica pura, um por cena.
 */
final class ArmilarMotion {
    /** O quanto o {@code u} anda por segundo em direção ao alvo (a spec: 1,6/s). */
    static final double RATE = 1.6;
    /** A força do laço que trava o giro na conjunção (por segundo), quando {@code u} passa de 0,5. */
    private static final double LOCK = 4.0;
    /** A puxada máxima do laço por tick, em radianos (0,6 rad/s a mais que o giro). */
    private static final double MAX_PULL = 0.03;
    private static final double TICK = 0.05;

    private final double[] spin = new double[3];
    private final double[] prevSpin = new double[3];
    private double u;
    private double prevU;
    private boolean started;

    /** Um tick: o alvo é {@code tuned ? 1 : band / 7}; {@code clockSeconds} é o relógio do mundo neste tick. */
    void advance(double targetU, double clockSeconds) {
        System.arraycopy(spin, 0, prevSpin, 0, 3);
        prevU = u;
        if (!started) {
            // nasce onde estaria: alinhado se já está sintonizado, no ritmo solto se não
            u = targetU;
            prevU = u;
            for (int i = 0; i < 3; i++) {
                spin[i] = u >= 1 ? ArmilarPose.aligned(clockSeconds) : ArmilarPose.omega(i, u) * clockSeconds;
            }
            System.arraycopy(spin, 0, prevSpin, 0, 3);
            started = true;
            return;
        }
        u += (targetU - u) * Math.min(1, RATE * TICK);
        double lock = Ease.cl((u - 0.5) * 2);
        double gain = Math.min(1, LOCK * lock * lock * TICK);
        for (int i = 0; i < 3; i++) {
            spin[i] += ArmilarPose.omega(i, u) * TICK;
            if (gain > 0) {
                double error = Math.IEEEremainder(ArmilarPose.aligned(clockSeconds) - spin[i], 2 * Math.PI);
                // a puxada por tick é limitada: o anel que ficou minutos solto não dá um chicote ao travar
                spin[i] += Math.clamp(error * gain, -MAX_PULL, MAX_PULL);
            }
        }
    }

    double u(float partialTick) {
        return prevU + (u - prevU) * partialTick;
    }

    double spin(int orbit, float partialTick) {
        return prevSpin[orbit] + (spin[orbit] - prevSpin[orbit]) * partialTick;
    }

    boolean started() {
        return started;
    }
}
