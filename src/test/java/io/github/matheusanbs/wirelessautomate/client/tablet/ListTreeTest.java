package io.github.matheusanbs.wirelessautomate.client.tablet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Kind;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Line;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.TreeRow;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ListTreeTest {
    // nós 0-2 fornalhas, 3 baú, 4-5 barris (na ordem do servidor)
    private static final List<String> MACHINES = List.of("furnace", "furnace", "furnace", "chest", "barrel", "barrel");
    /** O nó 1 aberto: a presa, um grupo com duas máquinas e o "mais N", e um grupo recolhido. */
    private static final List<TreeRow> OPEN_1 = List.of(
            new TreeRow(1, Kind.ATTACHED, false),
            new TreeRow(1, Kind.GROUP, false),
            new TreeRow(1, Kind.MACHINE, false),
            new TreeRow(1, Kind.MACHINE, false),
            new TreeRow(1, Kind.MORE, false),
            new TreeRow(1, Kind.GROUP, true));

    private static ListTree<String> flat(Set<String> collapsed) {
        return new ListTree<>(MACHINES, List.of(), collapsed);
    }

    @Test
    void withoutTreeRowsHeadersAndNodes() {
        List<Line<String>> lines = flat(Set.of()).lines();
        assertEquals(9, lines.size());
        assertEquals(Kind.HEADER, lines.get(0).kind());
        assertEquals("furnace", lines.get(0).group());
        assertEquals(3, lines.get(0).count());
        assertEquals(0, lines.get(1).node());
        assertEquals(Kind.NODE, lines.get(1).kind());
        assertTrue(lines.get(1).collapsed());
        assertEquals(2, lines.get(3).node());
        assertEquals(Kind.HEADER, lines.get(4).kind());
        assertEquals(1, lines.get(4).count());
        assertEquals(3, lines.get(5).node());
        assertEquals("barrel", lines.get(6).group());
        assertEquals(5, lines.get(8).node());
    }

    @Test
    void collapsedGroupKeepsOnlyItsHeader() {
        ListTree<String> tree = flat(Set.of("furnace"));
        List<Line<String>> lines = tree.lines();
        assertEquals(6, lines.size());
        assertTrue(lines.get(0).collapsed());
        assertEquals(3, lines.get(0).count());
        assertEquals(Kind.HEADER, lines.get(1).kind());
        assertEquals(3, lines.get(2).node());
        assertEquals(-1, tree.lineOfNode(1));
        assertEquals(0, tree.headerLine("furnace"));
        assertEquals(1, tree.headerLine("chest"));
        assertEquals(0, tree.anchorOfNode(1));
    }

    @Test
    void openNodeGetsItsRowsRightBelow() {
        ListTree<String> tree = new ListTree<>(MACHINES, OPEN_1, Set.of());
        List<Line<String>> lines = tree.lines();
        assertEquals(15, lines.size());
        assertTrue(tree.open(1));
        assertFalse(tree.open(0));
        assertFalse(lines.get(2).collapsed());
        assertEquals(Kind.ATTACHED, lines.get(3).kind());
        assertEquals(1, lines.get(3).depth());
        assertEquals(Kind.GROUP, lines.get(4).kind());
        assertEquals(Kind.MACHINE, lines.get(5).kind());
        assertEquals(2, lines.get(5).depth());
        assertEquals(1, lines.get(5).node());
        assertEquals(2, lines.get(5).row());
        assertEquals(Kind.MORE, lines.get(7).kind());
        assertEquals(Kind.GROUP, lines.get(8).kind());
        assertTrue(lines.get(8).collapsed());
        assertEquals(2, lines.get(9).node());
        assertEquals(9, tree.lineOfNode(2));
        assertEquals(5, tree.lineOfRow(2));
    }

    @Test
    void rowsOfACollapsedNodeGroupAreHidden() {
        ListTree<String> tree = new ListTree<>(MACHINES, OPEN_1, Set.of("furnace"));
        assertEquals(6, tree.lines().size());
        assertEquals(-1, tree.lineOfRow(2));
        assertTrue(tree.open(1));
    }

    @Test
    void parentOfTreeLines() {
        ListTree<String> tree = new ListTree<>(MACHINES, OPEN_1, Set.of());
        assertEquals(2, tree.parentLine(3));
        assertEquals(2, tree.parentLine(4));
        assertEquals(4, tree.parentLine(5));
        assertEquals(4, tree.parentLine(7));
        assertEquals(2, tree.parentLine(8));
        assertEquals(-1, tree.parentLine(2));
        assertEquals(-1, tree.parentLine(0));
    }

    @Test
    void emptyList() {
        ListTree<String> tree = new ListTree<>(List.of(), List.of(), Set.of());
        assertTrue(tree.lines().isEmpty());
        assertEquals(-1, tree.next(-1, 1));
        assertEquals(-1, tree.next(-1, -1));
        assertNull(tree.group(0));
    }

    @Test
    void arrowsWalkLinesSkippingHeadersAndStopAtTheEdges() {
        ListTree<String> tree = new ListTree<>(MACHINES, OPEN_1, Set.of());
        assertEquals(1, tree.next(-1, 1));
        assertEquals(14, tree.next(-1, -1));
        assertEquals(3, tree.next(2, 1));
        assertEquals(9, tree.next(8, 1));
        assertEquals(11, tree.next(9, 1));
        assertEquals(9, tree.next(11, -1));
        assertEquals(1, tree.next(1, -1));
        assertEquals(14, tree.next(14, 1));
    }

    @Test
    void arrowsSkipCollapsedGroups() {
        // de dentro do nó 1 aberto: do "mais N" ao grupo recolhido (sem máquinas abaixo dele) e dali ao nó 2
        ListTree<String> tree = new ListTree<>(MACHINES, OPEN_1, Set.of("chest"));
        assertEquals(8, tree.next(7, 1));
        assertEquals(tree.lineOfNode(2), tree.next(8, 1));
        // do nó 2, o grupo de nós recolhido (o baú) fica de fora: o próximo é o primeiro barril
        assertEquals(tree.lineOfNode(4), tree.next(tree.lineOfNode(2), 1));
        assertEquals(tree.lineOfNode(2), tree.next(tree.lineOfNode(4), -1));
        ListTree<String> all = flat(Set.of("furnace", "chest", "barrel"));
        assertEquals(-1, all.next(-1, 1));
    }

    @Test
    void leftClosesCollapsesOrGoesUp() {
        boolean[] openable = {false, true, true, false, false, false};
        ListTree<String> tree = new ListTree<>(MACHINES, OPEN_1, Set.of(), openable);
        assertEquals(new ListTree.Arrow(ListTree.Step.CLOSE_NODE, 2), tree.left(2));
        assertEquals(new ListTree.Arrow(ListTree.Step.MOVE_TO, 2), tree.left(3));
        assertEquals(new ListTree.Arrow(ListTree.Step.COLLAPSE_GROUP, 4), tree.left(4));
        assertEquals(new ListTree.Arrow(ListTree.Step.MOVE_TO, 4), tree.left(5));
        assertEquals(new ListTree.Arrow(ListTree.Step.MOVE_TO, 4), tree.left(7));
        // grupo recolhido: sobe para o nó
        assertEquals(new ListTree.Arrow(ListTree.Step.MOVE_TO, 2), tree.left(8));
        // nó fechado: recolhe o grupo de nós dele
        assertEquals(new ListTree.Arrow(ListTree.Step.COLLAPSE_HEADER, 0), tree.left(9));
        assertEquals(new ListTree.Arrow(ListTree.Step.COLLAPSE_HEADER, 10), tree.left(10));
        assertEquals(ListTree.Step.NONE, tree.left(-1).step());
    }

    @Test
    void rightOpensExpandsOrShowsAll() {
        boolean[] openable = {false, true, true, false, false, false};
        ListTree<String> tree = new ListTree<>(MACHINES, OPEN_1, Set.of(), openable);
        assertEquals(ListTree.Step.NONE, tree.right(1).step());
        assertEquals(ListTree.Step.NONE, tree.right(2).step());
        assertEquals(new ListTree.Arrow(ListTree.Step.OPEN_NODE, 9), tree.right(9));
        assertEquals(ListTree.Step.NONE, tree.right(4).step());
        assertEquals(new ListTree.Arrow(ListTree.Step.EXPAND_GROUP, 8), tree.right(8));
        assertEquals(new ListTree.Arrow(ListTree.Step.SHOW_ALL, 7), tree.right(7));
        assertEquals(ListTree.Step.NONE, tree.right(5).step());
        assertEquals(new ListTree.Arrow(ListTree.Step.EXPAND_HEADER, 0), tree.right(0));
        assertEquals(ListTree.Step.NONE, tree.right(99).step());
    }

    @Test
    void rowsOfAnUnknownNodeAreIgnored() {
        ListTree<String> tree = new ListTree<>(MACHINES, List.of(new TreeRow(9, Kind.ATTACHED, false)), Set.of());
        assertEquals(9, tree.lines().size());
        assertEquals(-1, tree.lineOfRow(0));
    }
}
