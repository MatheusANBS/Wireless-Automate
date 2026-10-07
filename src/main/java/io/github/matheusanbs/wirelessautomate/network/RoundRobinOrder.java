package io.github.matheusanbs.wirelessautomate.network;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.ToIntFunction;

/**
 * Ordem de entrega: prioridade maior primeiro e, empatando, round-robin. Lógica pura, testada por JUnit.
 *
 * <p>A parte imutável (destinos agrupados por prioridade) fica numa {@link Layout}, que pode ser
 * dividida entre várias ordens com a mesma lista de destinos; cada ordem tem só os próprios cursores.
 * A prioridade é lida uma vez, ao montar a {@code Layout}: se a lista de destinos ou alguma
 * prioridade mudar, monte outra ({@link Layout#sameAs} diz se mudou). Destinos são comparados por
 * {@code equals}/{@code hashCode} e não podem ser {@code null}; um destino repetido conta pela
 * primeira ocorrência.
 *
 * <p>{@link #delivered} acha o destino pelo índice direto quando ele é o último lido da passada (o
 * caso do laço: lê {@code pass().get(i)} e entrega nele); senão, numa tabela de índices sem caixas.
 */
public final class RoundRobinOrder<T> {
    /**
     * Destinos agrupados por prioridade, sem estado de round-robin. Dividir uma {@code Layout} entre
     * as origens que alcançam os mesmos destinos tira da montagem das rotas o custo
     * O(origens × destinos) de mapas e ordenações.
     */
    public static final class Layout<T> {
        /** A lista recebida, na ordem original, e a prioridade lida de cada posição (para {@link #sameAs}). */
        private final Object[] original;
        private final int[] originalPriority;
        /** Destinos já agrupados: prioridade decrescente, empates na ordem da lista original. */
        private final Object[] items;
        /** Início de cada grupo em {@link #items}; o último valor é {@code items.length}. */
        private final int[] groupStart;
        /** Grupo de cada posição de {@link #items}. */
        private final int[] groupOf;
        /** Posição em {@link #items} da primeira ocorrência do destino de cada posição. */
        private final int[] firstAt;
        /** Destino (por {@code equals}) → posição em {@link #items} da primeira ocorrência. */
        private final IndexTable indexOf;

        public Layout(List<T> destinations, ToIntFunction<T> priority) {
            int n = destinations.size();
            original = new Object[n];
            originalPriority = new int[n];
            // Primeiro índice original de cada destino: a prioridade é lida uma vez por destino distinto.
            IndexTable firstOriginal = new IndexTable(n);
            int[] firstOf = new int[n];
            long[] keys = new long[n];
            for (int i = 0; i < n; i++) {
                T destination = Objects.requireNonNull(destinations.get(i), "destino nulo");
                original[i] = destination;
                int first = firstOriginal.putIfAbsent(destination, i);
                firstOf[i] = first;
                int p = first == i ? priority.applyAsInt(destination) : originalPriority[first];
                originalPriority[i] = p;
                // Prioridade decrescente e, empatando, índice crescente: ordenação estável sem caixas.
                keys[i] = (((long) Integer.MAX_VALUE - p) << 31) | i;
            }
            Arrays.sort(keys);

            items = new Object[n];
            groupOf = new int[n];
            firstAt = new int[n];
            int[] sortedOf = new int[n];
            int groups = 0;
            int[] starts = new int[n + 1];
            int previous = 0;
            for (int k = 0; k < n; k++) {
                int i = (int) (keys[k] & Integer.MAX_VALUE);
                items[k] = original[i];
                sortedOf[i] = k;
                int current = originalPriority[i];
                if (k == 0 || current != previous) {
                    starts[groups++] = k;
                }
                previous = current;
                groupOf[k] = groups - 1;
            }
            indexOf = new IndexTable(n);
            for (int k = 0; k < n; k++) {
                int i = (int) (keys[k] & Integer.MAX_VALUE);
                // Repetidos empatam (mesma prioridade), então a primeira ocorrência vem antes na ordem.
                firstAt[k] = sortedOf[firstOf[i]];
                indexOf.putIfAbsent(items[k], firstAt[k]);
            }
            starts[groups] = n;
            groupStart = Arrays.copyOf(starts, groups + 1);
        }

        public int size() {
            return items.length;
        }

        int groups() {
            return groupStart.length - 1;
        }

        /**
         * A {@code Layout} foi montada com estes mesmos destinos (por identidade), na mesma ordem e
         * com as mesmas prioridades: uma ordem sobre ela continua valendo, com os cursores. O(destinos).
         */
        public boolean sameAs(List<T> destinations, ToIntFunction<T> priority) {
            int n = destinations.size();
            if (n != original.length) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                T destination = destinations.get(i);
                if (destination != original[i] || priority.applyAsInt(destination) != originalPriority[i]) {
                    return false;
                }
            }
            return true;
        }

        /** O destino na posição {@code i} da lista original. */
        @SuppressWarnings("unchecked")
        public T destination(int i) {
            return (T) original[i];
        }
    }

    /** Tabela aberta objeto → índice (por {@code equals}), sem caixas; preenchida só na montagem. */
    private static final class IndexTable {
        private final Object[] keys;
        private final int[] values;
        private final int mask;

        IndexTable(int expected) {
            int capacity = Integer.highestOneBit(Math.max(4, expected * 2 - 1)) << 1;
            keys = new Object[capacity];
            values = new int[capacity];
            mask = capacity - 1;
        }

        private static int hash(Object key) {
            int h = key.hashCode() * 0x9E3779B9;
            return h ^ (h >>> 16);
        }

        /** Guarda {@code value} se a chave não existe; devolve o valor que fica. */
        int putIfAbsent(Object key, int value) {
            int slot = hash(key) & mask;
            while (true) {
                Object present = keys[slot];
                if (present == null) {
                    keys[slot] = key;
                    values[slot] = value;
                    return value;
                }
                if (present == key || present.equals(key)) {
                    return values[slot];
                }
                slot = (slot + 1) & mask;
            }
        }

        /** O valor da chave, ou -1. */
        int get(Object key) {
            int slot = hash(key) & mask;
            while (true) {
                Object present = keys[slot];
                if (present == null) {
                    return -1;
                }
                if (present == key || present.equals(key)) {
                    return values[slot];
                }
                slot = (slot + 1) & mask;
            }
        }
    }

    private final Layout<T> layout;
    /** Deslocamento do round-robin de cada grupo. */
    private final int[] cursor;
    /** Os cursores no último {@link #pass()}: a passada não muda com entregas no meio dela. */
    private final int[] passCursor;
    private final List<T> passView = new PassView();
    /** O último destino lido da passada e a posição dele em {@code items}, para {@link #delivered} sem tabela. */
    private Object lastRead;
    private int lastReadAt;

    public RoundRobinOrder(List<T> destinations, ToIntFunction<T> priority) {
        this(new Layout<>(destinations, priority));
    }

    /** Ordem com cursores próprios sobre uma {@link Layout} que pode ser dividida. */
    public RoundRobinOrder(Layout<T> layout) {
        this.layout = layout;
        cursor = new int[layout.groups()];
        passCursor = new int[cursor.length];
    }

    public Layout<T> layout() {
        return layout;
    }

    /**
     * A ordem desta passada: grupos de prioridade decrescente; dentro de cada grupo começa pelo
     * cursor do grupo e dá a volta. Empates mantêm a ordem da lista original.
     *
     * <p>Não aloca nem copia os destinos (O(grupos)): devolve sempre a mesma visão imutável, válida
     * só até a próxima chamada de {@code pass()}. Chamar {@link #delivered} durante a iteração é seguro.
     */
    public List<T> pass() {
        System.arraycopy(cursor, 0, passCursor, 0, cursor.length);
        return passView;
    }

    /**
     * Registra uma entrega: a próxima passada do grupo de {@code destination} começa depois dele.
     * Destino que não está na ordem é ignorado.
     */
    public void delivered(T destination) {
        Layout<T> l = layout;
        int index;
        if (destination != null && destination == lastRead) {
            index = l.firstAt[lastReadAt];
        } else {
            index = l.indexOf.get(destination);
            if (index < 0) {
                return;
            }
        }
        int g = l.groupOf[index];
        int start = l.groupStart[g];
        int length = l.groupStart[g + 1] - start;
        int next = index - start + 1;
        cursor[g] = next == length ? 0 : next;
    }

    public int size() {
        return layout.items.length;
    }

    private final class PassView extends AbstractList<T> implements RandomAccess {
        @Override
        @SuppressWarnings("unchecked")
        public T get(int index) {
            Layout<T> l = layout;
            Objects.checkIndex(index, l.items.length);
            int g = l.groupOf[index];
            int start = l.groupStart[g];
            int length = l.groupStart[g + 1] - start;
            int k = passCursor[g] + index - start;
            int at = start + (k >= length ? k - length : k);
            Object item = l.items[at];
            lastRead = item;
            lastReadAt = at;
            return (T) item;
        }

        @Override
        public int size() {
            return layout.items.length;
        }
    }
}
