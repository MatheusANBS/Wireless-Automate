package io.github.matheusanbs.wirelessautomate.client.tablet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * As linhas da Lista do Tablet em árvore (lógica pura): os nós chegam do servidor já ordenados pela máquina, e
 * cada sequência de nós da mesma máquina vira um grupo com um cabeçalho e a contagem (um grupo recolhido fica só
 * com o cabeçalho). Abaixo de um nó aberto vêm as linhas da árvore que o servidor mandou para ele, na ordem dele:
 * a máquina presa, os grupos de alcance, as máquinas de cada grupo aberto e o "mais N". O nó está aberto quando
 * tem linhas; quem abre e fecha é o servidor.
 *
 * <p>As setas andam pelas linhas que não são cabeçalho, pulando os grupos recolhidos e parando nas bordas.
 *
 * @param <K> o id da máquina de cada nó
 */
public final class ListTree<K> {
    /** O tipo de uma linha. */
    public enum Kind {
        /** O cabeçalho de um grupo de nós da mesma máquina. */
        HEADER,
        NODE,
        ATTACHED,
        /** Um grupo de alcance de um nó aberto. */
        GROUP,
        MACHINE,
        /** "… mais N" no fim de um grupo de alcance cortado. */
        MORE;

        /** Uma linha da árvore, abaixo de um nó. */
        public boolean tree() {
            return ordinal() >= ATTACHED.ordinal();
        }
    }

    /**
     * Uma linha da árvore como o servidor a manda, só com o que a navegação precisa.
     *
     * @param parent    a posição do nó de cima na lista de nós
     * @param kind      {@link Kind#ATTACHED}, {@link Kind#GROUP}, {@link Kind#MACHINE} ou {@link Kind#MORE}
     * @param collapsed num grupo, se ele está recolhido
     */
    public record TreeRow(int parent, Kind kind, boolean collapsed) {
    }

    /**
     * Uma linha a desenhar.
     *
     * @param group     a máquina do grupo de nós em que a linha está
     * @param count     num cabeçalho, quantos nós o grupo tem
     * @param collapsed num cabeçalho ou num grupo de alcance, se está recolhido; num nó, se ele não está aberto
     * @param node      a posição do nó (o da linha, ou o de cima numa linha da árvore); -1 num cabeçalho
     * @param row       numa linha da árvore, a posição dela nas linhas do servidor; senão -1
     * @param depth     o recuo: 0 no cabeçalho e no nó, 1 na presa e no grupo, 2 na máquina e no "mais N"
     */
    public record Line<K>(Kind kind, K group, int count, boolean collapsed, int node, int row, int depth) {
    }

    private final List<K> machines;
    private final List<Line<K>> lines = new ArrayList<>();
    /** A linha de cada nó, -1 se ele está num grupo recolhido. */
    private final int[] nodeLines;
    /** A linha de cada linha da árvore do servidor, -1 se ela está escondida. */
    private final int[] rowLines;
    private final boolean[] open;

    /**
     * @param machines  a máquina de cada nó, na ordem do servidor
     * @param rows      as linhas da árvore, na ordem do servidor: as de um nó juntas, logo depois dele
     * @param collapsed os grupos de nós recolhidos
     */
    public ListTree(List<K> machines, List<TreeRow> rows, Set<K> collapsed) {
        this.machines = machines;
        this.nodeLines = new int[machines.size()];
        this.rowLines = new int[rows.size()];
        this.open = new boolean[machines.size()];
        Arrays.fill(nodeLines, -1);
        Arrays.fill(rowLines, -1);
        // onde começam as linhas de cada nó (as do servidor vêm em ordem de nó)
        int[] firstRow = new int[machines.size()];
        int[] rowCount = new int[machines.size()];
        Arrays.fill(firstRow, -1);
        for (int r = 0; r < rows.size(); r++) {
            int parent = rows.get(r).parent();
            if (parent >= 0 && parent < machines.size()) {
                if (firstRow[parent] < 0) {
                    firstRow[parent] = r;
                }
                rowCount[parent]++;
                open[parent] = true;
            }
        }
        int i = 0;
        while (i < machines.size()) {
            K group = machines.get(i);
            int end = i;
            while (end < machines.size() && Objects.equals(machines.get(end), group)) {
                end++;
            }
            boolean closed = collapsed.contains(group);
            lines.add(new Line<>(Kind.HEADER, group, end - i, closed, -1, -1, 0));
            if (!closed) {
                for (int node = i; node < end; node++) {
                    nodeLines[node] = lines.size();
                    lines.add(new Line<>(Kind.NODE, group, 0, !open[node], node, -1, 0));
                    addTree(rows, group, node, firstRow[node], rowCount[node]);
                }
            }
            i = end;
        }
    }

    private void addTree(List<TreeRow> rows, K group, int node, int first, int count) {
        if (first < 0) {
            return;
        }
        for (int r = first, seen = 0; r < rows.size() && seen < count; r++) {
            TreeRow row = rows.get(r);
            if (row.parent() != node) {
                continue;
            }
            seen++;
            int depth = row.kind() == Kind.MACHINE || row.kind() == Kind.MORE ? 2 : 1;
            rowLines[r] = lines.size();
            lines.add(new Line<>(row.kind(), group, 0, row.collapsed(), node, r, depth));
        }
    }

    /** As linhas a desenhar, de cima para baixo. */
    public List<Line<K>> lines() {
        return Collections.unmodifiableList(lines);
    }

    /** O nó tem linhas da árvore abaixo dele. */
    public boolean open(int node) {
        return node >= 0 && node < open.length && open[node];
    }

    /** A máquina do nó, ou {@code null} fora da lista. */
    public @Nullable K group(int node) {
        return node >= 0 && node < machines.size() ? machines.get(node) : null;
    }

    /** A linha do nó, ou -1 se ele está num grupo recolhido ou fora da lista. */
    public int lineOfNode(int node) {
        return node >= 0 && node < nodeLines.length ? nodeLines[node] : -1;
    }

    /** A linha da linha {@code row} da árvore do servidor, ou -1. */
    public int lineOfRow(int row) {
        return row >= 0 && row < rowLines.length ? rowLines[row] : -1;
    }

    /** A linha do cabeçalho do grupo, ou -1. */
    public int headerLine(K group) {
        for (int line = 0; line < lines.size(); line++) {
            if (lines.get(line).kind() == Kind.HEADER && Objects.equals(lines.get(line).group(), group)) {
                return line;
            }
        }
        return -1;
    }

    /**
     * A linha de cima de uma linha da árvore: o grupo de alcance de uma máquina ou de um "mais N", o nó de uma presa
     * ou de um grupo. -1 num nó, num cabeçalho ou fora da lista.
     */
    public int parentLine(int line) {
        if (line < 0 || line >= lines.size() || !lines.get(line).kind().tree()) {
            return -1;
        }
        Line<K> child = lines.get(line);
        for (int up = line - 1; up >= 0; up--) {
            Line<K> above = lines.get(up);
            if (above.depth() < child.depth()) {
                return above.node() == child.node() ? up : -1;
            }
        }
        return -1;
    }

    /**
     * A linha seguinte ({@code direction > 0}) ou anterior que não é cabeçalho, a partir da linha {@code line};
     * sem linha ({@code -1}), a primeira ou a última. Na borda, fica na mesma ({@code -1} se não havia nenhuma).
     */
    public int next(int line, int direction) {
        int step = direction > 0 ? 1 : -1;
        boolean none = line < 0 || line >= lines.size();
        int from = none ? (step > 0 ? -1 : lines.size()) : line;
        for (int at = from + step; at >= 0 && at < lines.size(); at += step) {
            if (lines.get(at).kind() != Kind.HEADER) {
                return at;
            }
        }
        return none ? -1 : line;
    }

    /**
     * A linha de onde as setas partem para um nó escondido num grupo recolhido: o cabeçalho dele. -1 fora da
     * lista.
     */
    public int anchorOfNode(int node) {
        int line = lineOfNode(node);
        if (line >= 0) {
            return line;
        }
        K group = group(node);
        return group == null ? -1 : headerLine(group);
    }
}
