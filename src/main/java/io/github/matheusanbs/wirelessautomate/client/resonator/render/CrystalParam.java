package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * O parâmetro de vértice do cristal (UV2.y, um {@code short}, então tudo fica abaixo de 32768): a semente (o índice da
 * câmara, 4 bits), o estado (10 bits) e, no bit 14, qual malha é: 0 o aglomerado e 1 o losango. O estado é 0 vivo, 1 Opaco,
 * 2 a 1002 a recarga de 0 a 100%, e 1003 o rachado. O máximo é 16384 + (1003 << 4) + 15 = 32447.
 */
public final class CrystalParam {
    public static final int LIVE = 0;
    static final int DULL = 1;
    static final int RECHARGE = 2;
    static final int STEPS = 1000;
    /** Depois do fim da recarga (2 + 1000 = 1002), para não conflitar com ela. */
    public static final int CRACKED = RECHARGE + STEPS + 1;
    private static final int SEED_MASK = 15;
    private static final int STATE_SHIFT = 4;
    private static final int STATE_MASK = 1023;
    private static final int DIAMOND_BIT = 1 << 14;

    private CrystalParam() {
    }

    public static int pack(int seed, int state, boolean diamond) {
        return (seed & SEED_MASK) | (state << STATE_SHIFT) | (diamond ? DIAMOND_BIT : 0);
    }

    static int seed(int param) {
        return param & SEED_MASK;
    }

    static int state(int param) {
        return (param >> STATE_SHIFT) & STATE_MASK;
    }

    static boolean diamond(int param) {
        return (param & DIAMOND_BIT) != 0;
    }

    /**
     * O canal de efeito do vértice (UV0.y, {@code float}): 0 sem efeito, 1 a 2 o rachado em reparo com energia
     * (progresso = fx − 1), 3 a 4 o rachado do Reparador sem energia, 5 a 6 a dissolução (fração sumida = fx − 5).
     * O shader decodifica pelos mesmos limites de {@link #fxKind(float)}.
     */
    public static float repairing(double progress, boolean lit) {
        return (lit ? 1 : 3) + (float) Math.clamp(progress, 0.0, 1.0);
    }

    public static float dissolving(double amount) {
        return 5 + (float) Math.clamp(amount, 0.0, 1.0);
    }

    /** O tipo de efeito de {@code fx}: 0 nenhum, 1 reparo, 2 rachado sem energia, 3 dissolução. */
    public static int fxKind(float fx) {
        return fx < 0.5f ? 0 : fx < 2.5f ? 1 : fx < 4.5f ? 2 : 3;
    }

    /** A fração (0 a 1) dentro do tipo de efeito de {@code fx}. */
    public static double fxValue(float fx) {
        return switch (fxKind(fx)) {
            case 1 -> fx - 1;
            case 2 -> fx - 3;
            case 3 -> fx - 5;
            default -> 0;
        };
    }

    /** O estado da recarga em {@code progress} (0 a 1). */
    static int recharging(double progress) {
        return RECHARGE + (int) Math.round(Math.clamp(progress, 0.0, 1.0) * STEPS);
    }
}
