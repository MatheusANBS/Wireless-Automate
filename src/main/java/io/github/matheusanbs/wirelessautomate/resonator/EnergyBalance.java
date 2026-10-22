package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * A regra que liga a energia ao Ressonador de Sinal do mesmo tier (balanceamento de 22/10/2026), para a vazão de
 * energia do roteador e a capacidade da Bateria nunca se separarem do que o Ressonador gera. Lógica pura, com JUnit.
 *
 * <ul>
 *   <li><b>Roteador:</b> a energia por tick de um roteador (por face) é a geração máxima do Ressonador do tier mais
 *       5%, arredondada para cima em três algarismos significativos. Assim um roteador dá conta de um Ressonador
 *       sem sobrar muito.</li>
 *   <li><b>Bateria:</b> a capacidade é uma hora de jogo (72.000 ticks) da geração máxima do Ressonador do tier.</li>
 *   <li><b>Ultimate:</b> o último tier não tem limite em nenhum dos dois (0 na config).</li>
 * </ul>
 *
 * A geração máxima é {@link CoreMath#maxOutput}: todas as câmaras do tier com rendimento 2 a 100%, com o FE por
 * cristal padrão ({@link CoreMath#defaultFePerCrystal}).
 */
public final class EnergyBalance {
    /** Quantos tiers existem; o último é o Ultimate. */
    public static final int TIERS = 8;
    /** Uma hora de jogo a 20 ticks por segundo. */
    public static final long BATTERY_TICKS = 72_000L;
    /** O roteador passa a geração máxima em 5%: numerador e denominador de {@code ×1,05}. */
    private static final long MARGIN_NUMERATOR = 21;
    private static final long MARGIN_DENOMINATOR = 20;

    private EnergyBalance() {
    }

    /** O último tier, sem limite de roteador nem de bateria. */
    private static boolean unlimited(int tierOrdinal) {
        return tierOrdinal >= TIERS - 1;
    }

    /** A geração máxima do Ressonador do tier, em FE por tick, com o FE por cristal padrão. */
    public static long resonatorOutput(int tierOrdinal) {
        return CoreMath.maxOutput(CoreMath.chambers(tierOrdinal), CoreMath.defaultFePerCrystal(tierOrdinal));
    }

    /** A energia por tick do roteador do tier (padrão da config); 0 é sem limite (o Ultimate). */
    public static long routerEnergy(int tierOrdinal) {
        if (unlimited(tierOrdinal)) {
            return 0;
        }
        long max = resonatorOutput(tierOrdinal);
        long withMargin = (max * MARGIN_NUMERATOR + MARGIN_DENOMINATOR - 1) / MARGIN_DENOMINATOR;
        return roundUpToThreeDigits(withMargin);
    }

    /** A capacidade da Bateria do tier, em FE (padrão da config); 0 é sem limite (o Ultimate). */
    public static long batteryCapacity(int tierOrdinal) {
        return unlimited(tierOrdinal) ? 0 : CoreMath.mulSat(resonatorOutput(tierOrdinal), BATTERY_TICKS);
    }

    /** A tabela do roteador, pela posição do tier. */
    public static long[] routerEnergyTable() {
        long[] table = new long[TIERS];
        for (int i = 0; i < TIERS; i++) {
            table[i] = routerEnergy(i);
        }
        return table;
    }

    /** A tabela da Bateria, pela posição do tier. */
    public static long[] batteryTable() {
        long[] table = new long[TIERS];
        for (int i = 0; i < TIERS; i++) {
            table[i] = batteryCapacity(i);
        }
        return table;
    }

    /** Arredonda para cima mantendo os três primeiros algarismos: 29.031 vira 29.100 e 1.344.000 vira 1.350.000. */
    static long roundUpToThreeDigits(long value) {
        long step = 1;
        while (value / step >= 1_000) {
            step *= 10;
        }
        return (value + step - 1) / step * step;
    }
}
