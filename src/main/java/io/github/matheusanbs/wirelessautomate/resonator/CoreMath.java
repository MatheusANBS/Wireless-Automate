package io.github.matheusanbs.wirelessautomate.resonator;

/** As contas do Núcleo de Ressonância, em {@code long} e saturando em {@code Long.MAX_VALUE}. Lógica pura. */
public final class CoreMath {
    /** O buffer guarda 10 s (200 ticks) da geração máxima. */
    static final long BUFFER_TICKS = 200;

    private CoreMath() {
    }

    public static final int KIND_SIGNAL = 0;
    public static final int KIND_CUT = 1;
    public static final int KIND_TUNED = 2;
    /** A partir deste tier o cristal comum rende 1/4 e o Afinado de tier menor rende como o Lapidado. */
    public static final int ELITE_ORDINAL = 2;

    /** FE por cristal padrão, pela posição do tier: acima do melhor gerador de cada tier no pack; ver a spec de 20/10/2026. */
    private static final long[] DEFAULT_FE_PER_CRYSTAL = {
        216, 6_912, 10_000, 160_000, 6_400_000, 6_700_000, 184_000_000L, 268_435_456L
    };

    /** FE por cristal padrão no tier de posição {@code tierOrdinal}; um tier além da tabela repete o último. */
    public static long defaultFePerCrystal(int tierOrdinal) {
        return DEFAULT_FE_PER_CRYSTAL[Math.max(0, Math.min(tierOrdinal, DEFAULT_FE_PER_CRYSTAL.length - 1))];
    }

    /** Ticks de um minuto de jogo. */
    public static final int TICKS_PER_MINUTE = 1_200;
    /**
     * A vida de referência, 20 min: num Núcleo com essa vida, o cristal dura o {@code crystalLifeTicks} (ou o
     * {@code cutCrystalLifeTicks}) da config como está. É a vida padrão do Básico e do Avançado.
     */
    public static final long REFERENCE_LIFE_TICKS = 20L * TICKS_PER_MINUTE;
    /** Minutos que a carga dura a 100% de geração, pela posição do tier do Núcleo; ver a spec de 21/10/2026. */
    private static final int[] DEFAULT_LIFE_MINUTES = {20, 20, 15, 10, 5, 5, 4, 3};

    /** A vida padrão, em minutos, no Núcleo de posição {@code tierOrdinal}; um tier além da tabela repete o último. */
    public static int defaultLifeMinutes(int tierOrdinal) {
        return DEFAULT_LIFE_MINUTES[Math.max(0, Math.min(tierOrdinal, DEFAULT_LIFE_MINUTES.length - 1))];
    }

    /**
     * A vida de um cristal num Núcleo: a vida dele na config ({@code baseLifeTicks}) na escala da vida do tier
     * ({@code tierLifeTicks}) sobre a de referência, {@code base × tier / 20 min}. Arredonda para baixo, satura em
     * {@code Long.MAX_VALUE} e nunca dá menos de 1 tick; sem vida, 0 (o {@link CrystalCharge#drain} não gasta).
     */
    public static long crystalLife(long baseLifeTicks, long tierLifeTicks) {
        if (baseLifeTicks <= 0 || tierLifeTicks <= 0) {
            return 0;
        }
        long product = mulSat(baseLifeTicks, tierLifeTicks);
        long life = product == Long.MAX_VALUE
                ? mulSat(baseLifeTicks / REFERENCE_LIFE_TICKS, tierLifeTicks)
                : product / REFERENCE_LIFE_TICKS;
        return Math.max(1, life);
    }

    /** Câmaras de cristal: Básico 1 … Ultimate 8. */
    public static int chambers(int tierOrdinal) {
        return tierOrdinal + 1;
    }

    /** Rendimento em quartos de um cristal numa câmara: 4 é o rendimento 1 do Cristal de Sinal. */
    public static int yieldQuarters(int kind, int crystalTier, int coreTier) {
        int full = kind == KIND_SIGNAL ? 4 : 8;
        if (coreTier < ELITE_ORDINAL) {
            return full;
        }
        if (kind == KIND_TUNED && crystalTier >= coreTier) {
            return 8;
        }
        return full / 4;
    }

    /** Geração máxima por tick: todas as câmaras com rendimento 2 (8 quartos) a 100%. */
    public static long maxOutput(int chambers, long fePerCrystal) {
        return mulSat(mulSat(chambers, fePerCrystal), 2);
    }

    /**
     * A faixa de vazão de uma Porta no lote: 0 sem nada saindo, e de 1 a 7 pela fração da geração máxima que saiu
     * ({@code f = extraído / (ticks × máxima)}, saturada em 1), em escala de raiz: {@code min(7, 1 + floor(7 × √f))}.
     */
    public static int flowBand(long extracted, int ticks, long maxOutput) {
        if (extracted <= 0 || maxOutput <= 0 || ticks <= 0) {
            return 0;
        }
        double fraction = Math.min(1.0, (double) extracted / ((double) ticks * (double) maxOutput));
        return Math.min(7, 1 + (int) Math.floor(7 * Math.sqrt(fraction)));
    }

    public static long buffer(int chambers, long fePerCrystal) {
        return mulSat(maxOutput(chambers, fePerCrystal), BUFFER_TICKS);
    }

    /** FE por tick de cristais que somam {@code quarterSum} quartos de rendimento, na eficiência dada; arredonda para baixo. */
    public static long perTick(long fePerCrystal, int quarterSum, double efficiency) {
        if (quarterSum <= 0 || efficiency <= 0) {
            return 0;
        }
        double perQuarter = fePerCrystal / 4.0;
        return (long) Math.floor(Math.min((double) Long.MAX_VALUE, perQuarter * quarterSum * Math.min(1.0, efficiency)));
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
