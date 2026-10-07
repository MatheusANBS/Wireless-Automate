package io.github.matheusanbs.wirelessautomate.network;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.ToIntFunction;

/**
 * Ordem de entrega: prioridade maior primeiro e, empatando, round-robin. Lógica pura, testada por JUnit.
 *
 * <p>A parte imutável (destinos agrupados por prioridade) fica numa {@link Layout}, que pode ser
 * dividida entre várias ordens com a mesma lista de destinos; cada ordem tem só os próprios cursores.
 * A prioridade é lida uma vez, ao montar a {@code Layout}: se a lista de destinos ou alguma
 * prioridade mudar, monte outra. Destinos são comparados por {@code equals}/{@code hashCode} e não
 * podem ser {@code null}; um destino repetido conta pela primeira ocorrência.
 */
public final class RoundRobinOrder<T> {
    /**
     * Destinos agrupados por prioridade, sem estado de round-robin. Dividir uma {@code Layout} entre
     * as origens que alcançam os mesmos destinos tira da montagem das rotas o custo
     * O(origens × destinos) de mapas e ordenações.
     */
    public static final class Layout<T> {
        /** Destinos já agrupados: prioridade decrescente, empates na ordem da lista original. */
        private final Object[] items;
        /** Início de cada grupo em {@link #items}; o último valor é {@code items.length}. */
        private final int[] groupStart;
        /** Grupo de cada posição de {@link #items}. */
        private final int[] groupOf;
        private final Map<T, Integer> indexOf;

        public Layout(List<T> destinations, ToIntFunction<T> priority) {
            int n = destinations.size();
            List<T> sorted = new ArrayList<>(n);
            Map<T, Integer> priorityOf = new HashMap<>(Math.max(16, n * 2));
            for (T destination : destinations) {
                Objects.requireNonNull(destination, "destino nulo");
                priorityOf.computeIfAbsent(destination, priority::applyAsInt);
                sorted.add(destination);
            }
            // List.sort é estável: empates mantêm a ordem original.
            sorted.sort(Comparator.comparingInt((T t) -> priorityOf.get(t)).reversed());

            items = sorted.toArray();
            groupOf = new int[n];
            indexOf = new HashMap<>(Math.max(16, n * 2));
            int groups = 0;
            int[] starts = new int[n + 1];
            int previous = 0;
            for (int i = 0; i < n; i++) {
                int current = priorityOf.get(sorted.get(i));
                if (i == 0 || current != previous) {
                    starts[groups++] = i;
                }
                previous = current;
                groupOf[i] = groups - 1;
                indexOf.putIfAbsent(sorted.get(i), i);
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
    }

    private final Layout<T> layout;
    /** Deslocamento do round-robin de cada grupo. */
    private final int[] cursor;
    /** Os cursores no último {@link #pass()}: a passada não muda com entregas no meio dela. */
    private final int[] passCursor;
    private final List<T> passView = new PassView();

    public RoundRobinOrder(List<T> destinations, ToIntFunction<T> priority) {
        this(new Layout<>(destinations, priority));
    }

    /** Ordem com cursores próprios sobre uma {@link Layout} que pode ser dividida. */
    public RoundRobinOrder(Layout<T> layout) {
        this.layout = layout;
        cursor = new int[layout.groups()];
        passCursor = new int[cursor.length];
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
        Integer index = layout.indexOf.get(destination);
        if (index == null) {
            return;
        }
        int g = layout.groupOf[index];
        int start = layout.groupStart[g];
        int length = layout.groupStart[g + 1] - start;
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
            return (T) l.items[start + (k >= length ? k - length : k)];
        }

        @Override
        public int size() {
            return layout.items.length;
        }
    }
}
