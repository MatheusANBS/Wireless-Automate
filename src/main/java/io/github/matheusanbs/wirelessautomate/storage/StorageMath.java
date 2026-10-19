package io.github.matheusanbs.wirelessautomate.storage;

/**
 * Contas do armazenamento do mod, sem classes do Minecraft (testadas por JUnit). Capacidade
 * {@code <= 0} é sem limite, e aí o teto é {@link Long#MAX_VALUE}: as somas saturam, nunca estouram.
 */
public final class StorageMath {
    /** Quanto de {@code amount} cabe com {@code total} guardado. */
    public static long accept(long total, long capacity, long amount) {
        if (amount <= 0) {
            return 0;
        }
        long limit = capacity <= 0 ? Long.MAX_VALUE : capacity;
        long room = total >= limit ? 0 : limit - total;
        return Math.min(amount, room);
    }

    /** Soma que satura em {@link Long#MAX_VALUE} (para totais lidos de um save adulterado). */
    public static long add(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    /**
     * Sinal do comparador, como no baú vanilla: 0 vazio, 1 com qualquer coisa e 15 cheio. Sem
     * limite, é 1 com qualquer coisa.
     */
    public static int signal(long total, long capacity) {
        if (total <= 0) {
            return 0;
        }
        if (capacity <= 0) {
            return 1;
        }
        if (total >= capacity) {
            return 15;
        }
        return 1 + (int) (14.0 * total / capacity);
    }

    /**
     * Nível mostrado pelo Tanque de Source, de 0 (vazio) a 10 (cheio): com qualquer conteúdo, pelo
     * menos 1, arredondado para cima. Sem limite, 10 com qualquer coisa.
     */
    public static int fillLevel(long stored, long capacity) {
        if (stored <= 0) {
            return 0;
        }
        if (capacity <= 0 || stored >= capacity) {
            return 10;
        }
        return Math.max(1, (int) Math.ceil(10.0 * stored / capacity));
    }

    /** Ticks de um dia de servidor ligado, a 20 ticks por segundo. */
    public static final long TICKS_PER_DAY = 24L * 60 * 60 * 20;

    /**
     * O conteúdo guardado em {@code storedAt} (game time do overworld) já venceu em {@code now}, com o
     * prazo de {@code days} dias de servidor ligado; {@code days <= 0} é sem prazo. Um relógio que voltou
     * (mundo trocado, {@code storedAt} no futuro) não vence nada.
     */
    public static boolean stashExpired(long storedAt, long now, int days) {
        return days > 0 && now - storedAt > days * TICKS_PER_DAY;
    }

    private StorageMath() {
    }

    /**
     * Quanto o botão direito pega de um tipo da lista: meia pilha cheia quando há pelo menos uma
     * pilha guardada, ou a metade do que há (arredondada para cima, como no vanilla) quando há menos.
     */
    public static int halfTake(long present, int maxStackSize) {
        long base = Math.min(present, maxStackSize);
        return (int) Math.max(0, (base + 1) / 2);
    }
}
