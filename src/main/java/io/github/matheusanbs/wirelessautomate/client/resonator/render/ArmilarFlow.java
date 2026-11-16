package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/** O quanto a vazão pesa na luz e no sol. Lógica pura. */
final class ArmilarFlow {
    private ArmilarFlow() {
    }

    /**
     * A força dos filamentos pela faixa de vazão suavizada (0 a 7): vazão 0, nada; de 1 a 7, o {@code k} da spec. O
     * {@code ss(0, 1, ·)} é a rampa que evita o pulo de 0 para 0,45 perto de 0.
     */
    static double strength(double band) {
        return Ease.ss(0, 1, band) * (0.45 + 0.55 * band / 7);
    }

    static double sunMul(double u) {
        return 0.55 + 0.45 * u;
    }
}
