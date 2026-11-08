package io.github.matheusanbs.wirelessautomate.storage;

/**
 * A regra que liga a capacidade do Baú, dos Tanques e do Tanque de Source à vazão de uma face do mesmo tier
 * (balanceamento 6, de 08/11/2026), como a Bateria já é ligada ao Ressonador ({@code EnergyBalance}): cada um
 * guarda <b>uma hora</b> do que uma face do tier move. Antes, o Baú levava 17 minutos para encher pela face do
 * tier, o Tanque 2 e o Tanque de Source menos de 2, e a Bateria uma hora. Lógica pura, com JUnit.
 */
public final class StorageBalance {
    /** Uma hora de jogo, em segundos: as vazões são por segundo. */
    public static final long HOUR_SECONDS = 3_600L;

    private StorageBalance() {
    }

    /** Uma hora da vazão por segundo {@code ratePerSecond}; 0 (sem limite) continua 0. Satura em {@code Long.MAX_VALUE}. */
    public static long hourOf(long ratePerSecond) {
        if (ratePerSecond <= 0) {
            return 0;
        }
        return ratePerSecond > Long.MAX_VALUE / HOUR_SECONDS ? Long.MAX_VALUE : ratePerSecond * HOUR_SECONDS;
    }

    /** A tabela por tier: uma hora de cada vazão de {@code ratesPerSecond}. */
    public static long[] hourOf(long[] ratesPerSecond) {
        long[] table = new long[ratesPerSecond.length];
        for (int i = 0; i < table.length; i++) {
            table[i] = hourOf(ratesPerSecond[i]);
        }
        return table;
    }
}
