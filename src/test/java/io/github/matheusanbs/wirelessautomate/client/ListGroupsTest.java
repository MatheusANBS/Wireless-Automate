package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.ListGroups.Row;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ListGroupsTest {
    // nós 0-2 fornalhas, 3 baú, 4-5 barris (na ordem do servidor)
    private static final List<String> MACHINES = List.of("furnace", "furnace", "furnace", "chest", "barrel", "barrel");

    @Test
    void rowsHaveAHeaderPerGroupWithTheCount() {
        List<Row<String>> rows = new ListGroups<>(MACHINES, Set.of()).rows();
        assertEquals(9, rows.size());
        assertTrue(rows.get(0).header());
        assertEquals("furnace", rows.get(0).group());
        assertEquals(3, rows.get(0).count());
        assertEquals(0, rows.get(1).node());
        assertEquals(2, rows.get(3).node());
        assertTrue(rows.get(4).header());
        assertEquals(1, rows.get(4).count());
        assertEquals(3, rows.get(5).node());
        assertEquals("barrel", rows.get(6).group());
        assertEquals(5, rows.get(8).node());
        assertFalse(rows.get(8).header());
    }

    @Test
    void collapsedGroupKeepsOnlyItsHeader() {
        ListGroups<String> groups = new ListGroups<>(MACHINES, Set.of("furnace"));
        List<Row<String>> rows = groups.rows();
        assertEquals(6, rows.size());
        assertTrue(rows.get(0).header());
        assertEquals(3, rows.get(0).count());
        assertTrue(rows.get(0).collapsed());
        assertTrue(rows.get(1).header());
        assertEquals(3, rows.get(2).node());
        assertEquals(-1, groups.rowOfNode(1));
        assertEquals(0, groups.headerRow("furnace"));
        assertEquals(1, groups.headerRow("chest"));
    }

    @Test
    void emptyList() {
        ListGroups<String> groups = new ListGroups<>(List.of(), Set.of());
        assertTrue(groups.rows().isEmpty());
        assertEquals(-1, groups.next(-1, 1));
        assertEquals(-1, groups.next(-1, -1));
    }

    @Test
    void arrowsWalkNodeRowsAndStopAtTheEdges() {
        ListGroups<String> groups = new ListGroups<>(MACHINES, Set.of());
        assertEquals(0, groups.next(-1, 1));
        assertEquals(5, groups.next(-1, -1));
        assertEquals(1, groups.next(0, 1));
        assertEquals(3, groups.next(2, 1));
        assertEquals(2, groups.next(3, -1));
        assertEquals(0, groups.next(0, -1));
        assertEquals(5, groups.next(5, 1));
    }

    @Test
    void arrowsSkipCollapsedGroups() {
        ListGroups<String> groups = new ListGroups<>(MACHINES, Set.of("chest"));
        assertEquals(4, groups.next(2, 1));
        assertEquals(2, groups.next(4, -1));
        ListGroups<String> first = new ListGroups<>(MACHINES, Set.of("furnace"));
        assertEquals(3, first.next(-1, 1));
        assertEquals(3, first.next(3, -1));
        // nó escondido num grupo recolhido: anda a partir do lugar do grupo
        assertEquals(3, first.next(1, 1));
        ListGroups<String> last = new ListGroups<>(MACHINES, Set.of("barrel"));
        assertEquals(3, last.next(4, -1));
        assertEquals(5, last.next(5, 1));
        ListGroups<String> all = new ListGroups<>(MACHINES, Set.of("furnace", "chest", "barrel"));
        assertEquals(-1, all.next(-1, 1));
        assertEquals(2, all.next(2, 1));
    }

    @Test
    void groupOfANode() {
        ListGroups<String> groups = new ListGroups<>(MACHINES, Set.of());
        assertEquals("furnace", groups.group(2));
        assertEquals("chest", groups.group(3));
        assertEquals(null, groups.group(9));
        assertEquals(5, groups.rowOfNode(3));
    }
}
