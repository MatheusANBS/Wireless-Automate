package io.github.matheusanbs.wirelessautomate.network;

/**
 * Janela deslizante de {@link #SECONDS} baldes de um segundo para um número dos Fluxos (o que entrou,
 * saiu, o Armazém recebeu ou deu, numa rede e num tipo). {@link #add} soma no balde aberto e
 * {@link #roll} o fecha; a média é a dos baldes fechados, então a janela dá um número desde o primeiro
 * segundo e só fica {@link #filled() cheia} (o fim do "medindo…") depois de {@link #SECONDS}.
 * Lógica pura, sem classes do Minecraft.
 */
public final class FlowWindow {
    /** Baldes da janela, um por segundo. */
    public static final int SECONDS = 5;

    private final long[] buckets = new long[SECONDS];
    /** Próximo balde a ser escrito pelo {@link #roll} (o mais antigo quando a janela está cheia). */
    private int next;
    /** Baldes fechados, até {@link #SECONDS}. */
    private int closed;
    /** Soma dos baldes fechados. */
    private long total;
    /** O balde aberto. */
    private long current;

    /** Soma {@code amount} ao balde aberto. */
    public void add(long amount) {
        current += amount;
    }

    /** Fecha o balde aberto; com a janela cheia, o mais antigo sai. */
    public void roll() {
        if (closed == SECONDS) {
            total -= buckets[next];
        } else {
            closed++;
        }
        buckets[next] = current;
        total += current;
        next = next + 1 == SECONDS ? 0 : next + 1;
        current = 0;
    }

    /** Esvazia a janela e o balde aberto. */
    public void reset() {
        for (int i = 0; i < SECONDS; i++) {
            buckets[i] = 0;
        }
        next = 0;
        closed = 0;
        total = 0;
        current = 0;
    }

    /** Soma dos baldes fechados. */
    public long total() {
        return total;
    }

    /** Baldes fechados, de 0 a {@link #SECONDS}: os segundos que a média cobre. */
    public int seconds() {
        return closed;
    }

    /** Média por segundo dos baldes fechados; 0 sem nenhum. */
    public double perSecond() {
        return closed == 0 ? 0 : (double) total / closed;
    }

    /** Todos os baldes fechados: a média já cobre a janela inteira. */
    public boolean filled() {
        return closed == SECONDS;
    }
}
