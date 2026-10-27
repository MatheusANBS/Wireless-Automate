package io.github.matheusanbs.wirelessautomate.storage;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Os contadores de entra e sai de um armazenamento, que só existem enquanto alguma Tela de Sinal o vigia (o
 * armazenamento guarda um campo nulo no resto do tempo e não faz conta nenhuma a mais). Soma só as inserções e
 * extrações reais, nunca as simuladas: o total e, nos Baús, o de cada item vigiado (o grupo do item, sem os
 * componentes). A taxa por segundo é a de uma janela de 5 s ({@link RateWindow}), amostrada por quem lê.
 */
public final class StorageWatch {
    /** Quantos itens um Baú vigia ao mesmo tempo; o resto não é contado. */
    public static final int MAX_ITEMS = 256;

    /** Os contadores de um item vigiado. */
    private static final class ItemCount {
        long in;
        long out;
        final RateWindow window = new RateWindow();
    }

    private long in;
    private long out;
    private final RateWindow total = new RateWindow();
    private @Nullable Reference2ObjectOpenHashMap<Object, ItemCount> items;

    /** Soma uma inserção real; {@code group} é o grupo do item (ou {@code null} sem grupo). */
    void inserted(@Nullable Object group, long amount) {
        in += amount;
        if (items != null && group != null) {
            ItemCount count = items.get(group);
            if (count != null) {
                count.in += amount;
            }
        }
    }

    /** Soma uma extração real. */
    void extracted(@Nullable Object group, long amount) {
        out += amount;
        if (items != null && group != null) {
            ItemCount count = items.get(group);
            if (count != null) {
                count.out += amount;
            }
        }
    }

    /** Passa a contar o item (o grupo); sem efeito se já conta ou se passou do teto. */
    public void watchItem(Object group) {
        if (items == null) {
            items = new Reference2ObjectOpenHashMap<>();
        }
        if (items.size() < MAX_ITEMS && !items.containsKey(group)) {
            items.put(group, new ItemCount());
        }
    }

    /** Guarda o instantâneo de cada contador se passou um segundo desde o último. */
    public void sample(long tick) {
        total.sample(tick, in, out);
        if (items != null) {
            for (ItemCount count : items.values()) {
                count.window.sample(tick, count.in, count.out);
            }
        }
    }

    /** O total que entrou por segundo; do item se {@code group} não é nulo e está vigiado. */
    public double inPerSecond(@Nullable Object group) {
        ItemCount count = itemOf(group);
        return count != null ? count.window.inPerSecond() : group != null ? 0 : total.inPerSecond();
    }

    /** O total que saiu por segundo; do item se {@code group} não é nulo e está vigiado. */
    public double outPerSecond(@Nullable Object group) {
        ItemCount count = itemOf(group);
        return count != null ? count.window.outPerSecond() : group != null ? 0 : total.outPerSecond();
    }

    private @Nullable ItemCount itemOf(@Nullable Object group) {
        return group == null || items == null ? null : items.get(group);
    }

    /** O total somado desde que a vigia começou (para os testes). */
    public long totalIn() {
        return in;
    }

    public long totalOut() {
        return out;
    }

    public long itemIn(Object group) {
        ItemCount count = itemOf(group);
        return count == null ? 0 : count.in;
    }

    public long itemOut(Object group) {
        ItemCount count = itemOf(group);
        return count == null ? 0 : count.out;
    }
}
