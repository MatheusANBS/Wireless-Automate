package io.github.matheusanbs.wirelessautomate.client.tablet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.tablet.NetworkSections.Group;
import io.github.matheusanbs.wirelessautomate.client.tablet.NetworkSections.Row;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NetworkSectionsTest {
    private static final UUID BASE = UUID.randomUUID();
    private static final UUID FORJA = UUID.randomUUID();
    private static final UUID MINA = UUID.randomUUID();
    private static final UUID NETHER = UUID.randomUUID();
    private static final UUID ALHEIA = UUID.randomUUID();
    private static final List<UUID> NETWORKS = List.of(BASE, FORJA, MINA, NETHER);
    private static final UUID PRODUCAO = UUID.randomUUID();
    private static final UUID EXPANSAO = UUID.randomUUID();
    /** A Mina está nos dois grupos; a rede de outro jogador, que não está na lista, fica de fora. */
    private static final List<Group> GROUPS = List.of(
            new Group(PRODUCAO, List.of(MINA, FORJA, ALHEIA)),
            new Group(EXPANSAO, List.of(NETHER, MINA)));

    @Test
    void withoutGroupsOnlyNetworks() {
        List<Row> rows = NetworkSections.build(NETWORKS, List.of(), Set.of());
        assertEquals(4, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            assertFalse(rows.get(i).header());
            assertEquals(-1, rows.get(i).group());
            assertEquals(i, rows.get(i).network());
        }
    }

    @Test
    void groupsThenNoGroupInNetworkOrder() {
        List<Row> rows = NetworkSections.build(NETWORKS, GROUPS, Set.of());
        // Produção: Forja, Mina; Expansão: Mina, Nether; Sem grupo: Base
        assertEquals(List.of(
                new Row(0, -1, List.of(), false),
                new Row(0, 1, List.of(), false),
                new Row(0, 2, List.of(1), false),
                new Row(1, -1, List.of(), false),
                new Row(1, 2, List.of(0), false),
                new Row(1, 3, List.of(), false),
                new Row(-1, -1, List.of(), false),
                new Row(-1, 0, List.of(), false)), rows);
        assertTrue(rows.get(6).header());
    }

    @Test
    void collapsedSectionKeepsOnlyTheHeader() {
        List<Row> rows = NetworkSections.build(NETWORKS, GROUPS, Set.of(PRODUCAO, NetworkSections.NO_GROUP));
        assertEquals(List.of(
                new Row(0, -1, List.of(), true),
                new Row(1, -1, List.of(), false),
                new Row(1, 2, List.of(0), false),
                new Row(1, 3, List.of(), false),
                new Row(-1, -1, List.of(), true)), rows);
    }

    @Test
    void noGroupSectionOnlyWithLooseNetworks() {
        List<Row> rows = NetworkSections.build(List.of(FORJA, MINA), GROUPS, Set.of());
        assertTrue(rows.stream().noneMatch(r -> r.header() && r.group() < 0));
        // um grupo vazio continua com o cabeçalho
        rows = NetworkSections.build(List.of(BASE), List.of(new Group(PRODUCAO, List.of())), Set.of());
        assertEquals(List.of(
                new Row(0, -1, List.of(), false),
                new Row(-1, -1, List.of(), false),
                new Row(-1, 0, List.of(), false)), rows);
    }
}
