package io.github.matheusanbs.wirelessautomate.network;

/**
 * Contas da vazão mostrada nas telas, sem estourar o {@code long}. Os contadores de quanto um nó já
 * moveu podem dar a volta (a diferença entre duas amostras continua certa); o que não pode virar
 * negativo é o que vem depois: passar de por tick para por segundo e somar nós e redes. Com
 * quantidades absurdas (armazenamentos sem limite, tiers altos), tudo satura em
 * {@link Long#MAX_VALUE}. Lógica pura, sem classes do Minecraft.
 */
public final class RateMath {
    private RateMath() {
    }

    /** Soma que satura nos extremos do {@code long} em vez de dar a volta. */
    public static long add(long a, long b) {
        long sum = a + b;
        if (((a ^ sum) & (b ^ sum)) < 0) {
            return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return sum;
    }

    /** {@code value × 20} (de por tick para por segundo), saturado e com o sinal mantido. */
    public static long perSecond(long value) {
        long high = Math.multiplyHigh(value, 20);
        long low = value * 20;
        if (high != (low >> 63)) {
            return value < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return low;
    }

    /**
     * Vazão de {@code delta} movido em {@code elapsed} ticks, arredondada: por tick ou por segundo.
     * {@code delta} negativo (contador que voltou) conta como zero.
     */
    public static long rate(long delta, long elapsed, boolean perTick) {
        if (delta <= 0 || elapsed <= 0) {
            return 0;
        }
        long scaled = perTick ? delta : perSecond(delta);
        long whole = scaled / elapsed;
        long rest = scaled % elapsed;
        return rest >= elapsed - rest ? add(whole, 1) : whole;
    }
}
