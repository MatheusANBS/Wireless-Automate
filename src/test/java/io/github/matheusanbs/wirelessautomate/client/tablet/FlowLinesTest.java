package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.network.ResourceType.ENERGY;
import static io.github.matheusanbs.wirelessautomate.network.ResourceType.FLUID;
import static io.github.matheusanbs.wirelessautomate.network.ResourceType.ITEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.Net;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.NetLine;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.StateKind;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.StatePart;
import io.github.matheusanbs.wirelessautomate.client.tablet.FlowLines.TypeLine;
import io.github.matheusanbs.wirelessautomate.network.FlowStats;
import io.github.matheusanbs.wirelessautomate.network.FlowStats.States;
import io.github.matheusanbs.wirelessautomate.network.FlowWindow;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlowLinesTest {
    private static final List<ResourceType> TYPES = List.of(ITEM, FLUID, ENERGY);
    private static final List<Integer> NO_FACES = List.of(0, 0, 0, 0, 0);

    private static FlowStats stats(UUID net, ResourceType type, long produced, long consumed, long stored,
            long released, int seconds, States states) {
        return new FlowStats(net, type, produced, consumed, stored, released, seconds, states);
    }

    private static Net net(String name, boolean paused, FlowStats... types) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes());
        List<FlowStats> list = List.of(types).stream()
                .map(f -> new FlowStats(id, f.type(), f.produced(), f.consumed(), f.stored(), f.released(), f.seconds(),
                        f.states()))
                .toList();
        return new Net(id, name, 0xFF000000, paused, 0, list);
    }

    private static FlowStats full(ResourceType type, long produced, long consumed) {
        return stats(null, type, produced, consumed, 0, 0, FlowWindow.SECONDS, States.NONE);
    }

    @Test
    void byTypeSumsRunningNetworksAndSkipsPaused() {
        Net base = net("Base", false, full(ITEM, 50, 40), full(ENERGY, 100, 100));
        Net forge = net("Forja", false, full(ITEM, 500, 500));
        Net nether = net("Nether", true, full(ITEM, 1_000, 1_000));
        List<TypeLine> lines = FlowLines.byType(List.of(base, forge, nether), TYPES, NO_FACES);

        assertEquals(List.of(ITEM, ENERGY), lines.stream().map(TypeLine::type).toList(), "só os tipos em uso, na ordem");
        TypeLine items = lines.getFirst();
        assertEquals(110, items.totals().produced(), 1e-9);
        assertEquals(108, items.totals().consumed(), 1e-9);
        assertEquals(List.of("Forja", "Base"), items.networks().stream().map(p -> p.net().name()).toList(),
                "da maior vazão para a menor, sem a pausada");
        assertEquals(List.of(nether), FlowLines.paused(List.of(base, forge, nether)));
    }

    @Test
    void typeWithOnlyFacesWithoutNetworkAppears() {
        List<TypeLine> lines = FlowLines.byType(List.of(), TYPES, List.of(0, 3, 0, 0, 0));
        assertEquals(1, lines.size());
        assertEquals(FLUID, lines.getFirst().type());
        assertEquals(3, lines.getFirst().withoutNetwork());
        assertTrue(lines.getFirst().totals().filled(), "sem rede nada a medir");
    }

    @Test
    void sumUsesPerSecondAndTheShortestWindow() {
        // 100 em 5 s (20/s) mais 30 em 2 s (15/s): 35/s, ainda medindo
        Net a = net("A", false, stats(null, ITEM, 100, 0, 0, 0, 5, States.NONE));
        Net b = net("B", false, stats(null, ITEM, 30, 0, 0, 0, 2, States.NONE));
        TypeLine items = FlowLines.byType(List.of(a, b), TYPES, NO_FACES).getFirst();
        assertEquals(35, items.totals().produced(), 1e-9);
        assertEquals(2, items.totals().seconds());
        assertFalse(items.totals().filled());
        assertFalse(items.totals().measuring());
    }

    @Test
    void statesAddUpAndBalanceIsStorageInMinusOut() {
        Net a = net("A", false, stats(null, ITEM, 0, 0, 50, 10, 5, new States(1, 2, 3, 4, 5, 6)));
        Net b = net("B", false, stats(null, ITEM, 0, 0, 0, 20, 5, new States(1, 1, 1, 1, 1, 1)));
        TypeLine items = FlowLines.byType(List.of(a, b), TYPES, NO_FACES).getFirst();
        assertEquals(new States(2, 3, 4, 5, 6, 7), items.totals().states());
        assertEquals(4, items.totals().balance(), 1e-9, "(50 − 30) / 5");
    }

    @Test
    void byNetworkSortsRunningThenPausedByName() {
        Net zeta = net("zeta", false, full(ENERGY, 1, 1), full(ITEM, 1, 1));
        Net alpha = net("Alpha", false, full(FLUID, 1, 1));
        Net paused = net("Beta", true);
        List<NetLine> lines = FlowLines.byNetwork(List.of(zeta, paused, alpha), TYPES);
        assertEquals(List.of("Alpha", "zeta", "Beta"), lines.stream().map(l -> l.net().name()).toList());
        assertEquals(List.of(ITEM, ENERGY), lines.get(1).types().stream().map(FlowLines.Part::type).toList(),
                "tipos na ordem do registro");
    }

    @Test
    void shortStatesOnlyWhatNeedsAttention() {
        assertEquals(List.of(), FlowLines.shortStates(new States(5, 4, 0, 3, 0, 0), 0), "movendo e vazia ficam na dica");
        assertEquals(List.of(new StatePart(StateKind.WAITING, 3), new StatePart(StateKind.FULL, 1),
                new StatePart(StateKind.REDSTONE, 2), new StatePart(StateKind.NO_NETWORK, 4)),
                FlowLines.shortStates(new States(0, 0, 3, 0, 1, 2), 4));
    }

    @Test
    void numbers() {
        assertEquals("0", FlowLines.amount(0));
        assertEquals("0", FlowLines.amount(0.01));
        assertEquals("2.5", FlowLines.amount(2.5));
        assertEquals("3", FlowLines.amount(2.98));
        assertEquals("370", FlowLines.amount(370.4));
        assertEquals("16.5k", FlowLines.amount(16_500));
        assertEquals("-4", FlowLines.amount(-4));
        assertEquals("+16.5k", FlowLines.signed(16_500));
        assertEquals("-0.4", FlowLines.signed(-0.4));
        assertEquals("0", FlowLines.signed(0.01));
    }

    @Test
    void energyGoesToTheScreenPerTick() {
        assertEquals(1_000, FlowLines.screenUnit(ENERGY, 20_000), 1e-9);
        assertEquals(20_000, FlowLines.screenUnit(ITEM, 20_000), 1e-9);
        assertTrue(FlowLines.zero(0.04));
        assertFalse(FlowLines.zero(0.06));
    }
}
