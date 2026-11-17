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

    /** A força na faixa 1 (a menor vazão que existe): um fio. Na faixa 7 vale 1. */
    static final double STRENGTH_MIN_BAND = 0.15;
    /** A maior faixa de vazão. */
    static final double BAND_TOP = 7;

    /**
     * A força do fluxo pela faixa de vazão suavizada (0 a 7), de 0 a 1: a rampa {@code ss(0, 1, faixa)} acende o fio
     * sem pulo perto de 0; de 1 a 7 sobe em linha de {@link #STRENGTH_MIN_BAND} (0,15) a 1. Abaixo de
     * {@link #MIN_STRENGTH} vale 0 (a faixa suavizada só chega a 0 por aproximação).
     */
    static double strength(double band) {
        double top = STRENGTH_MIN_BAND + (1 - STRENGTH_MIN_BAND) * Math.min(1, Math.max(0, (band - 1) / (BAND_TOP - 1)));
        double k = Ease.ss(0, 1, band) * top;
        return k < MIN_STRENGTH ? 0 : k;
    }

    /** O quanto o fluxo base está aceso, de 0 a 1: sobe com a força até a faixa 1 e fica cheio daí em diante. */
    static double presence(double k) {
        return Ease.ss(0, STRENGTH_MIN_BAND, k);
    }

    /**
     * O alfa das fitas do fluxo base pela força {@code k}: nunca 0 com {@code k > 0} (só some junto com a força), de
     * 0,39 na faixa 1 a 0,9 na 7; o pulso da conjunção completa o que falta até 1 por cima.
     */
    static double baseAlpha(double k) {
        return presence(k) * (0.3 + 0.6 * k);
    }

    /** A largura do fluxo base como fração da do filme: um fio na faixa 1 (0,4), a cheia na 7. */
    static double baseWidth(double k) {
        return 0.3 + 0.7 * k;
    }

    /** A velocidade do fluxo na textura, em múltiplos da do filme: lento com pouca energia, a do filme com a máxima. */
    static double speed(double k) {
        return 0.3 + 0.7 * k;
    }

    /** A soma da base com o pulso, {@code 1 − (1 − base)(1 − pulso)}: nunca passa de 1 e nunca é menor que cada parte. */
    static double combine(double base, double pulse) {
        return 1 - (1 - base) * (1 - pulse);
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
