package io.github.matheusanbs.wirelessautomate.client.tablet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Kind;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Line;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.TreeRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListTreeTest {
    private static final int NODES = 6;
    /** O nó 1 aberto: a presa, um grupo com duas máquinas e o "mais N", e um grupo recolhido. */
    private static final List<TreeRow> OPEN_1 = List.of(
            new TreeRow(1, Kind.ATTACHED, false),
            new TreeRow(1, Kind.GROUP, false),
            new TreeRow(1, Kind.MACHINE, false),
            new TreeRow(1, Kind.MACHINE, false),
            new TreeRow(1, Kind.MORE, false),
            new TreeRow(1, Kind.GROUP, true));

    @Test
    void withoutTreeRowsEveryNodeIsATopLine() {
        ListTree tree = new ListTree(NODES, List.of());
        List<Line> lines = tree.lines();
        assertEquals(NODES, lines.size());
        for (int i = 0; i < NODES; i++) {
            assertEquals(Kind.NODE, lines.get(i).kind());
            assertEquals(i, lines.get(i).node());
            assertEquals(0, lines.get(i).depth());
            assertTrue(lines.get(i).collapsed());
            assertEquals(i, tree.lineOfNode(i));
        }
    }

    @Test
    void openNodeGetsItsRowsRightBelow() {
        ListTree tree = new ListTree(NODES, OPEN_1);
        List<Line> lines = tree.lines();
        assertEquals(12, lines.size());
        assertTrue(tree.open(1));
        assertFalse(tree.open(0));
        assertFalse(lines.get(1).collapsed());
        assertEquals(Kind.ATTACHED, lines.get(2).kind());
        assertEquals(1, lines.get(2).depth());
        assertEquals(Kind.GROUP, lines.get(3).kind());
        assertEquals(Kind.MACHINE, lines.get(4).kind());
        assertEquals(2, lines.get(4).depth());
        assertEquals(1, lines.get(4).node());
        assertEquals(2, lines.get(4).row());
        assertEquals(Kind.MORE, lines.get(6).kind());
        assertEquals(Kind.GROUP, lines.get(7).kind());
        assertTrue(lines.get(7).collapsed());
        assertEquals(2, lines.get(8).node());
        assertEquals(8, tree.lineOfNode(2));
        assertEquals(4, tree.lineOfRow(2));
    }

    @Test
    void parentOfTreeLines() {
        ListTree tree = new ListTree(NODES, OPEN_1);
        assertEquals(1, tree.parentLine(2));
        assertEquals(1, tree.parentLine(3));
        assertEquals(3, tree.parentLine(4));
        assertEquals(3, tree.parentLine(6));
        assertEquals(1, tree.parentLine(7));
        assertEquals(-1, tree.parentLine(1));
        assertEquals(-1, tree.parentLine(0));
    }

    @Test
    void emptyList() {
        ListTree tree = new ListTree(0, List.of());
        assertTrue(tree.lines().isEmpty());
        assertEquals(-1, tree.next(-1, 1));
        assertEquals(-1, tree.next(-1, -1));
    }

    @Test
    void arrowsWalkEveryLineAndStopAtTheEdges() {
        ListTree tree = new ListTree(NODES, OPEN_1);
        assertEquals(0, tree.next(-1, 1));
        assertEquals(11, tree.next(-1, -1));
        assertEquals(2, tree.next(1, 1));
        assertEquals(8, tree.next(7, 1));
        assertEquals(7, tree.next(8, -1));
        assertEquals(0, tree.next(0, -1));
        assertEquals(11, tree.next(11, 1));
    }

    @Test
    void leftClosesCollapsesOrGoesUp() {
        boolean[] openable = {false, true, true, false, false, false};
        ListTree tree = new ListTree(NODES, OPEN_1, openable);
        assertEquals(new ListTree.Arrow(ListTree.Step.CLOSE_NODE, 1), tree.left(1));
        assertEquals(new ListTree.Arrow(ListTree.Step.MOVE_TO, 1), tree.left(2));
        assertEquals(new ListTree.Arrow(ListTree.Step.COLLAPSE_GROUP, 3), tree.left(3));
        assertEquals(new ListTree.Arrow(ListTree.Step.MOVE_TO, 3), tree.left(4));
        assertEquals(new ListTree.Arrow(ListTree.Step.MOVE_TO, 3), tree.left(6));
        // grupo recolhido: sobe para o nó
        assertEquals(new ListTree.Arrow(ListTree.Step.MOVE_TO, 1), tree.left(7));
        // nó fechado: nada a fechar
        assertEquals(ListTree.Step.NONE, tree.left(8).step());
        assertEquals(ListTree.Step.NONE, tree.left(-1).step());
    }

    @Test
    void rightOpensExpandsOrShowsAll() {
        boolean[] openable = {false, true, true, false, false, false};
        ListTree tree = new ListTree(NODES, OPEN_1, openable);
        assertEquals(ListTree.Step.NONE, tree.right(0).step());
        assertEquals(ListTree.Step.NONE, tree.right(1).step());
        assertEquals(new ListTree.Arrow(ListTree.Step.OPEN_NODE, 8), tree.right(8));
        assertEquals(ListTree.Step.NONE, tree.right(3).step());
        assertEquals(new ListTree.Arrow(ListTree.Step.EXPAND_GROUP, 7), tree.right(7));
        assertEquals(new ListTree.Arrow(ListTree.Step.SHOW_ALL, 6), tree.right(6));
        assertEquals(ListTree.Step.NONE, tree.right(4).step());
        assertEquals(ListTree.Step.NONE, tree.right(99).step());
    }

    @Test
    void rowsOfAnUnknownNodeAreIgnored() {
        ListTree tree = new ListTree(NODES, List.of(new TreeRow(9, Kind.ATTACHED, false)));
        assertEquals(NODES, tree.lines().size());
        assertEquals(-1, tree.lineOfRow(0));
    }
}
