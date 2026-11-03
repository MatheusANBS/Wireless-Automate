package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NetworkTreeTest {
    private static final UUID BASE = new UUID(1, 1);
    private static final UUID MEK = new UUID(1, 2);
    private static final UUID FUND = new UUID(2, 1);
    private static final UUID EST = new UUID(2, 2);
    private static final UUID FORJA = new UUID(2, 3);
    private static final UUID QUIM = new UUID(2, 4);
    private static final UUID ARC = new UUID(2, 5);

    @Test
    void mockupPositions() {
        List<NetworkTree.Row> rows = NetworkTree.build(
                List.of(new NetworkTree.GroupIn(BASE, List.of(FUND, EST, FORJA)), new NetworkTree.GroupIn(MEK, List.of(QUIM))),
                List.of(FUND, EST, FORJA, QUIM, ARC));
        int[] tops = rows.stream().mapToInt(NetworkTree.Row::top).toArray();
        // gerar.py, lista_redes: Base 28; Fundição, Estoque, Forja com o realce em 48, 62, 76; +4; Mekanism 96;
        // Química 116; +4; Sem grupo 136; Arcano 156
        assertEquals(List.of(28, 48, 62, 76, 96, 116, 136, 156), java.util.Arrays.stream(tops).boxed().toList());
        assertEquals(NetworkTree.Kind.LOOSE, rows.get(6).kind());
        assertEquals(ARC, rows.get(7).id());
        assertEquals(170, NetworkTree.height(rows));
    }

    @Test
    void aNetworkInTwoGroupsShowsInTheFirst() {
        List<NetworkTree.Row> rows = NetworkTree.build(
                List.of(new NetworkTree.GroupIn(BASE, List.of(FUND)), new NetworkTree.GroupIn(MEK, List.of(FUND))),
                List.of(FUND));
        assertEquals(1, rows.stream().filter(r -> FUND.equals(r.id())).count());
        assertEquals(BASE, rows.stream().filter(r -> FUND.equals(r.id())).findFirst().orElseThrow().group());
    }

    @Test
    void noLooseLabelWithoutLooseNetworks() {
        List<NetworkTree.Row> rows = NetworkTree.build(List.of(new NetworkTree.GroupIn(BASE, List.of(FUND))), List.of(FUND));
        assertEquals(2, rows.size());
    }

    @Test
    void looseNetworkFirstInTheInputStillGoesLast() {
        List<NetworkTree.Row> rows = NetworkTree.build(List.of(new NetworkTree.GroupIn(BASE, List.of(FUND, EST))),
                List.of(ARC, FUND, EST));
        assertEquals(List.of(NetworkTree.Kind.GROUP, NetworkTree.Kind.NETWORK, NetworkTree.Kind.NETWORK,
                NetworkTree.Kind.LOOSE, NetworkTree.Kind.NETWORK), rows.stream().map(NetworkTree.Row::kind).toList());
        assertEquals(FUND, rows.get(1).id());
        assertEquals(EST, rows.get(2).id());
        assertEquals(ARC, rows.get(4).id());
        assertEquals(null, rows.get(4).group());
        assertEquals(28 + 22 + 14 + 14 + 4, rows.get(3).top());
    }

    @Test
    void unknownNetworksInAGroupAreSkipped() {
        List<NetworkTree.Row> rows = NetworkTree.build(List.of(new NetworkTree.GroupIn(BASE, List.of(FUND, EST))),
                List.of(EST));
        assertEquals(EST, rows.get(1).id());
        assertEquals(2, rows.size());
    }
}
