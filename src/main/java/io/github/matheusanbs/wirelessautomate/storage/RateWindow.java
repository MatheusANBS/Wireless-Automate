package io.github.matheusanbs.wirelessautomate.storage;

/**
 * A taxa por segundo de dois contadores que só crescem (entra e sai), numa janela de 5 s: guarda os últimos 6
 * instantâneos, um por segundo no máximo, e a taxa é a diferença entre o mais novo e o mais velho sobre o tempo
 * entre eles. Sem alocar depois de criada; pura, com JUnit.
 */
public final class RateWindow {
    public static final int SECOND_TICKS = 20;
    private static final int SLOTS = 6;

    private final long[] ticks = new long[SLOTS];
    private final long[] ins = new long[SLOTS];
    private final long[] outs = new long[SLOTS];
    private int count;
    private int head;

    /** Guarda o instantâneo se passou um segundo desde o último; devolve se guardou. */
    public boolean sample(long tick, long in, long out) {
        if (count > 0 && tick - ticks[head] < SECOND_TICKS) {
            return false;
        }
        head = count == 0 ? 0 : (head + 1) % SLOTS;
        ticks[head] = tick;
        ins[head] = in;
        outs[head] = out;
        if (count < SLOTS) {
            count++;
        }
        return true;
    }

    private int oldest() {
        return count < SLOTS ? 0 : (head + 1) % SLOTS;
    }

    private double seconds() {
        return (ticks[head] - ticks[oldest()]) / (double) SECOND_TICKS;
    }

    /** O que entrou por segundo na janela; 0 com menos de dois instantâneos. */
    public double inPerSecond() {
        return count < 2 ? 0 : (ins[head] - ins[oldest()]) / seconds();
    }

    /** O que saiu por segundo na janela; 0 com menos de dois instantâneos. */
    public double outPerSecond() {
        return count < 2 ? 0 : (outs[head] - outs[oldest()]) / seconds();
    }
}
