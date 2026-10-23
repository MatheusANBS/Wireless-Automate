package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * O parâmetro de vértice do cristal (UV2.y, um {@code short}, então tudo fica abaixo de 32768): a semente (o índice da
 * câmara, 4 bits), o estado (10 bits) e, no bit 14, se o cristal está longe da câmera. O estado é 0 vivo, 1 Opaco,
 * 2 a 1002 a recarga de 0 a 100%, e 1003 o rachado. O máximo é 16384 + (1003 << 4) + 15 = 32447.
 */
final class CrystalParam {
    static final int LIVE = 0;
    static final int DULL = 1;
    static final int RECHARGE = 2;
    static final int STEPS = 1000;
    /** Depois do fim da recarga (2 + 1000 = 1002), para não conflitar com ela. */
    static final int CRACKED = RECHARGE + STEPS + 1;
    private static final int SEED_MASK = 15;
    private static final int STATE_SHIFT = 4;
    private static final int STATE_MASK = 1023;
    private static final int FAR_BIT = 1 << 14;

    private CrystalParam() {
    }

    static int pack(int seed, int state, boolean far) {
        return (seed & SEED_MASK) | (state << STATE_SHIFT) | (far ? FAR_BIT : 0);
    }

    static int seed(int param) {
        return param & SEED_MASK;
    }

    static int state(int param) {
        return (param >> STATE_SHIFT) & STATE_MASK;
    }

    static boolean far(int param) {
        return (param & FAR_BIT) != 0;
    }

    /** O estado da recarga em {@code progress} (0 a 1). */
    static int recharging(double progress) {
        return RECHARGE + (int) Math.round(Math.clamp(progress, 0.0, 1.0) * STEPS);
    }
}
