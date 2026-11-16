package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/** O quanto a vazão pesa na luz e no sol. Lógica pura. */
final class ArmilarFlow {
    /**
     * Abaixo disso a força vale 0: a faixa suavizada só chega a 0 por aproximação (12% por tick), e uma força de 1e-9
     * ainda desenharia a cabeça do arco e a fumaça do impacto no alfa cheio. É o alfa mínimo do {@code FxDraw}.
     */
    static final double MIN_STRENGTH = 0.002;

    private ArmilarFlow() {
    }

    /**
     * A força dos filamentos pela faixa de vazão suavizada (0 a 7): vazão 0, nada (e nada também a faixa que ainda não chegou a 0 e dá menos que {@link #MIN_STRENGTH}); de 1 a 7, o {@code k} da spec. O
     * {@code ss(0, 1, ·)} é a rampa que evita o pulo de 0 para 0,45 perto de 0.
     */
    static double strength(double band) {
        double k = Ease.ss(0, 1, band) * (0.45 + 0.55 * band / 7);
        return k < MIN_STRENGTH ? 0 : k;
    }

    /**
     * Um valor que anda {@code rate} da distância ao alvo por tick, depois de {@code ticks} ticks, em forma fechada
     * ({@code alvo + (agora − alvo) × (1 − rate)^ticks}). Acima do teto de {@link ArmilarMotion#MAX_CATCH_UP} ticks
     * vale o alvo.
     */
    static double approach(double now, double goal, double rate, long ticks) {
        if (ticks <= 0) {
            return now;
        }
        if (ticks >= ArmilarMotion.MAX_CATCH_UP) {
            return goal;
        }
        return goal + (now - goal) * Math.pow(1 - rate, ticks);
    }

    static double sunMul(double u) {
        return 0.55 + 0.45 * u;
    }
}
