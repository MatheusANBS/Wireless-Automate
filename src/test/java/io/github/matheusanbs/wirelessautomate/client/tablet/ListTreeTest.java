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
        ListTree<String> tree = flat(Set.of("chest"));
        assertEquals(tree.lineOfNode(4), tree.next(tree.lineOfNode(2), 1));
        ListTree<String> all = flat(Set.of("furnace", "chest", "barrel"));
        assertEquals(-1, all.next(-1, 1));
        assertEquals(1, all.next(1, 1));
    }

    @Test
    void rowsOfAnUnknownNodeAreIgnored() {
        ListTree<String> tree = new ListTree<>(MACHINES, List.of(new TreeRow(9, Kind.ATTACHED, false)), Set.of());
        assertEquals(9, tree.lines().size());
        assertEquals(-1, tree.lineOfRow(0));
    }
}
