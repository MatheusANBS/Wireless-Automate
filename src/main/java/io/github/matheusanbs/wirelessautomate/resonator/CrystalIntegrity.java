package io.github.matheusanbs.wirelessautomate.resonator;

/** A integridade de um cristal do Ressonador: cada recarga gasta 1, e com 0 ele racha. Lógica pura. */
public final class CrystalIntegrity {
    private CrystalIntegrity() {
    }

    public static int clamp(int integrity, int max) {
        return Math.max(0, Math.min(max, integrity));
    }

    /** A integridade depois de uma recarga, nunca abaixo de 0. */
    public static int spend(int integrity) {
        return Math.max(0, integrity - 1);
    }

    public static boolean isCracked(int integrity) {
        return integrity <= 0;
    }
}
