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
 * <p>A prioridade é lida uma vez, no construtor: se a lista de destinos ou alguma prioridade mudar,
 * crie outra instância. Destinos são comparados por {@code equals}/{@code hashCode} e não podem ser
 * {@code null}; um destino repetido conta pela primeira ocorrência.
 */
public final class RoundRobinOrder<T> {
    /** Destinos já agrupados: prioridade decrescente, empates na ordem da lista original. */
    private final Object[] items;
    /** Início de cada grupo em {@link #items}; o último valor é {@code items.length}. */
    private final int[] groupStart;
    /** Grupo de cada posição de {@link #items}. */
    private final int[] groupOf;
    /** Deslocamento do round-robin de cada grupo. */
    private final int[] cursor;
    private final Map<T, Integer> indexOf;
    private final Object[] passBuffer;
    private final List<T> passView = new PassView();

    public RoundRobinOrder(List<T> destinations, ToIntFunction<T> priority) {
        int n = destinations.size();
        List<T> sorted = new ArrayList<>(n);
        Map<T, Integer> priorityOf = new HashMap<>();
        for (T destination : destinations) {
            Objects.requireNonNull(destination, "destino nulo");
            priorityOf.computeIfAbsent(destination, priority::applyAsInt);
            sorted.add(destination);
        }
        // List.sort é estável: empates mantêm a ordem original.
        sorted.sort(Comparator.comparingInt((T t) -> priorityOf.get(t)).reversed());

        items = sorted.toArray();
        passBuffer = new Object[n];
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
        cursor = new int[groups];
    }

    /**
     * A ordem desta passada: grupos de prioridade decrescente; dentro de cada grupo começa pelo
     * cursor do grupo e dá a volta. Empates mantêm a ordem da lista original.
     *
     * <p>Não aloca: devolve sempre a mesma visão imutável, válida só até a próxima chamada de
     * {@code pass()}. Chamar {@link #delivered} durante a iteração é seguro.
     */
    public List<T> pass() {
        int out = 0;
        for (int g = 0; g < cursor.length; g++) {
            int start = groupStart[g];
            int length = groupStart[g + 1] - start;
            int offset = cursor[g];
            for (int i = 0; i < length; i++) {
                int k = offset + i;
                passBuffer[out++] = items[start + (k >= length ? k - length : k)];
            }
        }
        return passView;
    }

    /**
     * Registra uma entrega: a próxima passada do grupo de {@code destination} começa depois dele.
     * Destino que não está na ordem é ignorado.
     */
    public void delivered(T destination) {
        Integer index = indexOf.get(destination);
        if (index == null) {
            return;
        }
        int g = groupOf[index];
        int start = groupStart[g];
        int length = groupStart[g + 1] - start;
        int next = index - start + 1;
        cursor[g] = next == length ? 0 : next;
    }

    public int size() {
        return items.length;
    }

    private final class PassView extends AbstractList<T> implements RandomAccess {
        @Override
        @SuppressWarnings("unchecked")
        public T get(int index) {
            Objects.checkIndex(index, passBuffer.length);
            return (T) passBuffer[index];
        }

        @Override
        public int size() {
            return passBuffer.length;
        }
    }
}
