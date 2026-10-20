package io.github.matheusanbs.wirelessautomate.client.tablet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * As linhas da Lista do Tablet em árvore (lógica pura): cada nó (roteador) é uma linha de topo, na ordem do
 * servidor. Abaixo de um nó aberto vêm as linhas da árvore que o servidor mandou para ele, na ordem dele: a máquina
 * presa, os grupos de alcance, as máquinas de cada grupo aberto e o "mais N". O nó está aberto quando tem linhas;
 * quem abre e fecha é o servidor.
 *
 * <p>As setas andam por todas as linhas e param nas bordas.
 */
public final class ListTree {
    /** O tipo de uma linha. */
    public enum Kind {
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
     * @param collapsed num grupo de alcance, se está recolhido; num nó, se ele não está aberto
     * @param node      a posição do nó (o da linha, ou o de cima numa linha da árvore)
     * @param row       numa linha da árvore, a posição dela nas linhas do servidor; senão -1
     * @param depth     o recuo: 0 no nó, 1 na presa e no grupo, 2 na máquina e no "mais N"
     */
    public record Line(Kind kind, boolean collapsed, int node, int row, int depth) {
    }

    private final List<Line> lines = new ArrayList<>();
    /** A linha de cada nó. */
    private final int[] nodeLines;
    /** A linha de cada linha da árvore do servidor. */
    private final int[] rowLines;
    private final boolean[] open;
    /** O nó tem máquinas vinculadas (o → o abre). */
    private final boolean[] openable;

    public ListTree(int nodes, List<TreeRow> rows) {
        this(nodes, rows, new boolean[nodes]);
    }

    /**
     * @param nodes    quantos nós a página tem, na ordem do servidor
     * @param rows     as linhas da árvore, na ordem do servidor: as de um nó juntas, logo depois dele
     * @param openable por nó, se ele tem máquinas vinculadas (o → o abre)
     */
    public ListTree(int nodes, List<TreeRow> rows, boolean[] openable) {
        this.openable = openable;
        this.nodeLines = new int[nodes];
        this.rowLines = new int[rows.size()];
        this.open = new boolean[nodes];
        Arrays.fill(rowLines, -1);
        // onde começam as linhas de cada nó (as do servidor vêm em ordem de nó)
        int[] firstRow = new int[nodes];
        int[] rowCount = new int[nodes];
        Arrays.fill(firstRow, -1);
        for (int r = 0; r < rows.size(); r++) {
            int parent = rows.get(r).parent();
            if (parent >= 0 && parent < nodes) {
                if (firstRow[parent] < 0) {
                    firstRow[parent] = r;
                }
                rowCount[parent]++;
                open[parent] = true;
            }
        }
        for (int node = 0; node < nodes; node++) {
            nodeLines[node] = lines.size();
            lines.add(new Line(Kind.NODE, !open[node], node, -1, 0));
            addTree(rows, node, firstRow[node], rowCount[node]);
        }
    }

    private void addTree(List<TreeRow> rows, int node, int first, int count) {
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
            lines.add(new Line(row.kind(), row.collapsed(), node, r, depth));
        }
    }

    /** As linhas a desenhar, de cima para baixo. */
    public List<Line> lines() {
        return Collections.unmodifiableList(lines);
    }

    /** O nó tem linhas da árvore abaixo dele. */
    public boolean open(int node) {
        return node >= 0 && node < open.length && open[node];
    }

    /** A linha do nó, ou -1 fora da lista. */
    public int lineOfNode(int node) {
        return node >= 0 && node < nodeLines.length ? nodeLines[node] : -1;
    }

    /** A linha da linha {@code row} da árvore do servidor, ou -1. */
    public int lineOfRow(int row) {
        return row >= 0 && row < rowLines.length ? rowLines[row] : -1;
    }

    /**
     * A linha de cima de uma linha da árvore: o grupo de alcance de uma máquina ou de um "mais N", o nó de uma presa
     * ou de um grupo. -1 num nó ou fora da lista.
     */
    public int parentLine(int line) {
        if (line < 0 || line >= lines.size() || !lines.get(line).kind().tree()) {
            return -1;
        }
        Line child = lines.get(line);
        for (int up = line - 1; up >= 0; up--) {
            Line above = lines.get(up);
            if (above.depth() < child.depth()) {
                return above.node() == child.node() ? up : -1;
            }
        }
        return -1;
    }

    /**
     * A linha seguinte ({@code direction > 0}) ou anterior, a partir da linha {@code line}; sem linha ({@code -1}),
     * a primeira ou a última. Na borda, fica na mesma ({@code -1} se não havia nenhuma).
     */
    public int next(int line, int direction) {
        int step = direction > 0 ? 1 : -1;
        boolean none = line < 0 || line >= lines.size();
        int from = none ? (step > 0 ? -1 : lines.size()) : line;
        int at = from + step;
        if (at >= 0 && at < lines.size()) {
            return at;
        }
        return none ? -1 : line;
    }

    // ------------------------------------------------------------------ ← e →

    /** O que uma seta faz. */
    public enum Step {
        NONE,
        /** Fecha o nó da linha na árvore (no servidor). */
        CLOSE_NODE,
        OPEN_NODE,
        COLLAPSE_GROUP,
        EXPAND_GROUP,
        /** Mostra todas as máquinas do grupo do "mais N" da linha. */
        SHOW_ALL,
        /** Passa as setas para a linha. */
        MOVE_TO
    }

    /** O que a seta faz e em que linha (a do nó, do grupo ou a de destino). */
    public record Arrow(Step step, int line) {
        static final Arrow NONE = new Arrow(Step.NONE, -1);
    }

    /**
     * ← na linha {@code line}: num nó aberto, fecha; num grupo aberto, recolhe; num grupo recolhido, na presa, na
     * máquina e no "mais N", sobe para a linha de cima.
     */
    public Arrow left(int line) {
        if (line < 0 || line >= lines.size()) {
            return Arrow.NONE;
        }
        Line at = lines.get(line);
        return switch (at.kind()) {
            case NODE -> open(at.node()) ? new Arrow(Step.CLOSE_NODE, line) : Arrow.NONE;
            case GROUP -> at.collapsed() ? up(line) : new Arrow(Step.COLLAPSE_GROUP, line);
            case ATTACHED, MACHINE, MORE -> up(line);
        };
    }

    /**
     * → na linha {@code line}: num nó fechado com máquinas, abre; num grupo recolhido, abre; no "mais N", mostra
     * todas. No resto, nada.
     */
    public Arrow right(int line) {
        if (line < 0 || line >= lines.size()) {
            return Arrow.NONE;
        }
        Line at = lines.get(line);
        return switch (at.kind()) {
            case NODE -> openable(at.node()) && !open(at.node()) ? new Arrow(Step.OPEN_NODE, line) : Arrow.NONE;
            case GROUP -> at.collapsed() ? new Arrow(Step.EXPAND_GROUP, line) : Arrow.NONE;
            case MORE -> new Arrow(Step.SHOW_ALL, line);
            case ATTACHED, MACHINE -> Arrow.NONE;
        };
    }

    private Arrow up(int line) {
        int parent = parentLine(line);
        return parent < 0 ? Arrow.NONE : new Arrow(Step.MOVE_TO, parent);
    }

    private boolean openable(int node) {
        return node >= 0 && node < openable.length && openable[node];
    }
}
