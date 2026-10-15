package io.github.matheusanbs.wirelessautomate.resonator;

/** As contas do Núcleo de Ressonância, em {@code long} e saturando em {@code Long.MAX_VALUE}. Lógica pura. */
public final class CoreMath {
    /** O buffer guarda 10 s (200 ticks) da geração máxima. */
    static final long BUFFER_TICKS = 200;

    private CoreMath() {
    }

    /** Câmaras de cristal: Básico 1 … Ultimate 8. */
    public static int chambers(int tierOrdinal) {
        return tierOrdinal + 1;
    }

    /** Geração máxima por tick: todas as câmaras com lapidados (rendimento 2) a 100%. */
    public static long maxOutput(int chambers, long fePerCrystal) {
        return mulSat(mulSat(chambers, fePerCrystal), 2);
    }

    public static long buffer(int chambers, long fePerCrystal) {
        return mulSat(maxOutput(chambers, fePerCrystal), BUFFER_TICKS);
    }

    /** FE por tick de cristais que somam {@code yieldSum} de rendimento, na eficiência dada; arredonda para baixo. */
    public static long perTick(long fePerCrystal, int yieldSum, double efficiency) {
        if (yieldSum <= 0 || efficiency <= 0) {
            return 0;
        }
        return (long) Math.floor(mulSat(fePerCrystal, yieldSum) * Math.min(1.0, efficiency));
    }

    public static long mulSat(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        if ((high == 0 && low >= 0) || (high == -1 && low < 0)) {
            return low;
        }
        return (a < 0) == (b < 0) ? Long.MAX_VALUE : Long.MIN_VALUE;
    }
}
