package io.github.matheusanbs.wirelessautomate.network;

/**
 * Destino dormindo: depois de recusar, só é tentado de novo após um intervalo que dobra a cada
 * recusa, de 1 tick até {@code maxTicks}. Uma mudança no vizinho o acorda na hora. Lógica pura,
 * testada por JUnit. Começa acordado.
 */
public final class Backoff {
    private final int maxTicks;
    private int nextInterval = 1;
    private long wakeAt = Long.MIN_VALUE;

    /** {@code maxTicks} menor que 1 vira 1. */
    public Backoff(int maxTicks) {
        this.maxTicks = Math.max(1, maxTicks);
    }

    public boolean isAwake(long gameTime) {
        return gameTime >= wakeAt;
    }

    /** O destino recusou: dorme pelo intervalo atual e dobra o próximo. */
    public void sleep(long gameTime) {
        wakeAt = gameTime + nextInterval;
        nextInterval = (int) Math.min((long) nextInterval * 2, maxTicks);
    }

    /**
     * Dorme até o tick {@code wakeAt} (pelo menos até o seguinte a {@code gameTime}), sem mexer no
     * intervalo. Para uma origem cujos destinos estão todos dormindo: acorda junto com o primeiro
     * deles, em vez de pelo próprio intervalo (ver {@link #earliestWake}).
     */
    public void sleepUntil(long gameTime, long wakeAt) {
        this.wakeAt = Math.max(wakeAt, gameTime + 1);
    }

    /** Tick em que acorda ({@link Long#MIN_VALUE} se acordado desde sempre). */
    public long wakeAt() {
        return wakeAt;
    }

    /**
     * O menor {@code wakeAt} entre {@code current} e o deste: quando o primeiro de vários acorda.
     * Comece com {@link Long#MAX_VALUE}.
     */
    public long earliestWake(long current) {
        return Math.min(current, wakeAt);
    }

    /** O destino aceitou ou o vizinho mudou: acorda e volta ao intervalo mínimo. */
    public void wake() {
        wakeAt = Long.MIN_VALUE;
        nextInterval = 1;
    }

    public boolean isSleeping(long gameTime) {
        return !isAwake(gameTime);
    }

    /** Intervalo, em ticks, da próxima vez que dormir. */
    public int nextIntervalTicks() {
        return nextInterval;
    }

    public int maxTicks() {
        return maxTicks;
    }
}
