package io.github.matheusanbs.wirelessautomate.network;

import org.jetbrains.annotations.Nullable;

/**
 * Destino dormindo: depois de recusar, só é tentado de novo após um intervalo que dobra a cada
 * recusa, de 1 tick até {@code maxTicks}. Uma mudança no vizinho o acorda na hora. Lógica pura,
 * testada por JUnit. Começa acordado.
 */
public final class Backoff {
    /**
     * Quantas vezes algum {@code Backoff} acordou: quem guardou "todos dormindo" no mesmo tick sabe, por ele, que
     * nada acordou desde então (dormir mais não desfaz um "todos dormindo"). Só a thread do servidor mexe.
     */
    private static long wakes;
    private final int maxTicks;
    private int nextInterval = 1;
    private long wakeAt = Long.MIN_VALUE;
    /**
     * Uma cópia do {@link #wakeAt} numa posição de um array de quem percorre muitos {@code Backoff} por tick (o laço
     * das origens do gerenciador): ele lê só o array e não toca nos que dormem. {@code null} sem cópia.
     */
    private long @Nullable [] mirror;
    private int mirrorSlot;
    /**
     * Congelado ({@link #freeze}): dorme sem prazo e ignora {@link #wake}, {@link #sleep} e {@link #sleepUntil} até
     * {@link #thaw}. A porta parada pelo redstone usa: nenhuma entrega nem aviso a acorda no meio da visita.
     */
    private boolean frozen;

    /** {@code maxTicks} menor que 1 vira 1. */
    public Backoff(int maxTicks) {
        this.maxTicks = Math.max(1, maxTicks);
    }

    public boolean isAwake(long gameTime) {
        return gameTime >= wakeAt;
    }

    /** O destino recusou: dorme pelo intervalo atual e dobra o próximo. */
    public void sleep(long gameTime) {
        if (frozen) {
            return;
        }
        setWakeAt(gameTime + nextInterval);
        nextInterval = (int) Math.min((long) nextInterval * 2, maxTicks);
    }

    /**
     * Dorme até o tick {@code wakeAt} (pelo menos até o seguinte a {@code gameTime}), sem mexer no
     * intervalo. Para uma origem cujos destinos estão todos dormindo: acorda junto com o primeiro
     * deles, em vez de pelo próprio intervalo (ver {@link #earliestWake}).
     */
    public void sleepUntil(long gameTime, long wakeAt) {
        if (frozen) {
            return;
        }
        setWakeAt(Math.max(wakeAt, gameTime + 1));
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
        if (frozen) {
            return;
        }
        setWakeAt(Long.MIN_VALUE);
        nextInterval = 1;
        wakes++;
    }

    /** Dorme sem prazo e ignora tudo até {@link #thaw} (ver {@link #frozen}). */
    public void freeze() {
        frozen = true;
        setWakeAt(Long.MAX_VALUE);
    }

    /** Volta a aceitar {@link #wake} e os sonos; continua dormindo até alguém acordá-lo. */
    public void thaw() {
        frozen = false;
    }

    public boolean isFrozen() {
        return frozen;
    }

    /** Passa a copiar o {@link #wakeAt} em {@code array[slot]} (ver {@link #mirror}); {@code null} para de copiar. */
    void mirrorTo(long @Nullable [] array, int slot) {
        mirror = array;
        mirrorSlot = slot;
        if (array != null) {
            array[slot] = wakeAt;
        }
    }

    private void setWakeAt(long value) {
        wakeAt = value;
        if (mirror != null) {
            mirror[mirrorSlot] = value;
        }
    }

    /** Ver {@link #wakes}. */
    static long wakes() {
        return wakes;
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
