package io.github.matheusanbs.wirelessautomate.network;

/**
 * Balde de fichas para a vazão do tier, por face e por tipo. Lógica pura, testada por JUnit.
 * Enche {@code ratePerSecond / 20} por tick de jogo e guarda no máximo um segundo de vazão.
 * Taxa 0 significa sem limite. Começa cheio.
 *
 * <p>A parte fracionária do enchimento por tick é acumulada em vigésimos, então 30/s rende 1 e 2
 * alternados (3 a cada 2 ticks). As contas são em {@code long} e saturam em vez de estourar.
 *
 * <p>Energia usa a mesma classe com a taxa em FE/s, isto é, FE/t × 20 (ver
 * {@link #perSecondFromPerTick}); assim o enchimento por tick é exatamente o FE/t do tier.
 */
public final class RateLimiter {
    static final int TICKS_PER_SECOND = 20;

    private long ratePerSecond;
    private long tokens;
    /** Fração acumulada do enchimento, em vigésimos de unidade (0 a 19). */
    private long remainder;
    private long lastGameTime;
    private boolean started;

    /** Sem limite até receber uma taxa. */
    public RateLimiter() {
    }

    public RateLimiter(long ratePerSecond) {
        setRatePerSecond(ratePerSecond);
    }

    /** Converte uma taxa por tick (como FE/t) para por segundo, saturando em {@code Long.MAX_VALUE}. */
    public static long perSecondFromPerTick(long perTick) {
        if (perTick < 0) {
            throw new IllegalArgumentException("taxa negativa: " + perTick);
        }
        return perTick > Long.MAX_VALUE / TICKS_PER_SECOND ? Long.MAX_VALUE : perTick * TICKS_PER_SECOND;
    }

    /**
     * Muda a taxa. A capacidade passa a ser a nova taxa e o excesso guardado é cortado; vindo de
     * "sem limite", o balde começa cheio.
     */
    public void setRatePerSecond(long ratePerSecond) {
        if (ratePerSecond < 0) {
            throw new IllegalArgumentException("ratePerSecond negativo: " + ratePerSecond);
        }
        if (ratePerSecond == this.ratePerSecond) {
            return;
        }
        boolean wasUnlimited = this.ratePerSecond == 0;
        this.ratePerSecond = ratePerSecond;
        tokens = wasUnlimited ? ratePerSecond : Math.min(tokens, ratePerSecond);
        remainder = 0;
    }

    public long ratePerSecond() {
        return ratePerSecond;
    }

    public boolean isUnlimited() {
        return ratePerSecond == 0;
    }

    /**
     * Quanto pode mover agora, depois de encher pelo tempo passado desde a última chamada. A
     * primeira chamada só marca o tempo. Se o tempo voltar (mundo recarregado), não enche e passa
     * a contar a partir dele.
     */
    public long available(long gameTime) {
        if (ratePerSecond == 0) {
            return Long.MAX_VALUE;
        }
        if (started && gameTime > lastGameTime) {
            long ticks = gameTime - lastGameTime;
            refill(ticks < 0 ? Long.MAX_VALUE : ticks);
        }
        started = true;
        lastGameTime = gameTime;
        return tokens;
    }

    /** Desconta o que foi movido. Nunca deixa o saldo negativo; sem limite, não faz nada. */
    public void consume(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount negativo: " + amount);
        }
        if (ratePerSecond == 0) {
            return;
        }
        tokens = amount >= tokens ? 0 : tokens - amount;
    }

    private void refill(long ticks) {
        if (tokens >= ratePerSecond) {
            return;
        }
        // Com 20 ticks ou mais o balde enche de qualquer jeito; limitar evita estouro nas contas.
        if (ticks >= TICKS_PER_SECOND) {
            tokens = ratePerSecond;
            remainder = 0;
            return;
        }
        long whole = (ratePerSecond / TICKS_PER_SECOND) * ticks;
        long fraction = (ratePerSecond % TICKS_PER_SECOND) * ticks + remainder;
        whole += fraction / TICKS_PER_SECOND;
        remainder = fraction % TICKS_PER_SECOND;
        // tokens < rate e whole <= rate, mas a soma pode passar de Long.MAX_VALUE.
        if (whole >= ratePerSecond - tokens) {
            tokens = ratePerSecond;
            remainder = 0;
        } else {
            tokens += whole;
        }
    }
}
