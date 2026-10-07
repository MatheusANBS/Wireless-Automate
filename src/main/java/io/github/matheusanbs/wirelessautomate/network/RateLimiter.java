package io.github.matheusanbs.wirelessautomate.network;

/**
 * Balde de fichas para a vazão do tier, por face e por tipo. Lógica pura, testada por JUnit.
 * Enche {@code ratePerSecond / 20} por tick de jogo e guarda no máximo um segundo de vazão.
 * Taxa 0 significa sem limite.
 * TODO(contrato): implementar.
 */
public final class RateLimiter {
    public void setRatePerSecond(long ratePerSecond) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Quanto pode mover agora, depois de encher pelo tempo passado desde a última chamada. */
    public long available(long gameTime) {
        throw new UnsupportedOperationException("TODO");
    }

    public void consume(long amount) {
        throw new UnsupportedOperationException("TODO");
    }
}
