package io.github.matheusanbs.wirelessautomate.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * A lista agrupada da Lista do Tablet (lógica pura): os nós chegam do servidor já ordenados pela máquina, e
 * cada sequência de nós da mesma máquina vira um grupo com um cabeçalho e a contagem. Um grupo recolhido
 * fica só com o cabeçalho. As setas andam pelas linhas de nó, pulando os grupos recolhidos e parando nas
 * bordas.
 *
 * @param <K> o id da máquina de cada nó
 */
public final class ListGroups<K> {
    /**
     * Uma linha: o cabeçalho de um grupo ({@code count} nós, {@code collapsed} se recolhido; {@code node} é -1)
     * ou um nó ({@code node} é o índice dele na lista do servidor).
     */
    public record Row<K>(boolean header, K group, int count, boolean collapsed, int node) {
    }

    private final List<K> machines;
    private final List<Row<K>> rows = new ArrayList<>();

    /** @param machines a máquina de cada nó, na ordem do servidor; {@code collapsed} os grupos recolhidos */
    public ListGroups(List<K> machines, Set<K> collapsed) {
        this.machines = machines;
        int i = 0;
        while (i < machines.size()) {
            K group = machines.get(i);
            int end = i;
            while (end < machines.size() && Objects.equals(machines.get(end), group)) {
                end++;
            }
            boolean closed = collapsed.contains(group);
            rows.add(new Row<>(true, group, end - i, closed, -1));
            if (!closed) {
                for (int node = i; node < end; node++) {
                    rows.add(new Row<>(false, group, 0, false, node));
                }
            }
            i = end;
        }
    }

    /** As linhas a desenhar, de cima para baixo. */
    public List<Row<K>> rows() {
        return rows;
    }

    /**
     * O nó da linha de nó seguinte ({@code direction > 0}) ou anterior a partir do nó {@code node}; sem nó
     * ({@code -1}), o primeiro ou o último. Na borda, ou sem linha de nó naquele sentido, fica no mesmo
     * ({@code -1} se não havia nenhum). Um nó escondido num grupo recolhido anda a partir do cabeçalho dele.
     */
    public int next(int node, int direction) {
        int step = direction > 0 ? 1 : -1;
        int from;
        if (node < 0 || node >= machines.size()) {
            from = step > 0 ? -1 : rows.size();
        } else {
            from = rowOfNode(node);
            if (from < 0) {
                from = headerRow(machines.get(node));
            }
        }
        for (int row = from + step; row >= 0 && row < rows.size(); row += step) {
            if (!rows.get(row).header()) {
                return rows.get(row).node();
            }
        }
        return node < 0 || node >= machines.size() ? -1 : node;
    }

    /** A máquina do nó, ou {@code null} fora da lista. */
    public @Nullable K group(int node) {
        return node >= 0 && node < machines.size() ? machines.get(node) : null;
    }

    /** A linha do nó, ou -1 se ele está num grupo recolhido ou fora da lista. */
    public int rowOfNode(int node) {
        for (int row = 0; row < rows.size(); row++) {
            if (!rows.get(row).header() && rows.get(row).node() == node) {
                return row;
            }
        }
        return -1;
    }

    /** A linha do cabeçalho do grupo, ou -1. */
    public int headerRow(K group) {
        for (int row = 0; row < rows.size(); row++) {
            if (rows.get(row).header() && Objects.equals(rows.get(row).group(), group)) {
                return row;
            }
        }
        return -1;
    }
}
