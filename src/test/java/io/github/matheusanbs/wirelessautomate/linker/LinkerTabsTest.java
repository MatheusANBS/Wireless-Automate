package io.github.matheusanbs.wirelessautomate.linker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkerTabsTest {
    private static final ResourceType ITEM = ResourceType.ITEM;
    private static final ResourceType FLUID = ResourceType.FLUID;
    private static final ResourceType ENERGY = ResourceType.ENERGY;
    private static final ResourceType CHEMICAL = ResourceType.CHEMICAL;
    private static final List<ResourceType> BASE = List.of(ITEM, FLUID, ENERGY);
    private static final List<ResourceType> WITH_CHEMICALS = List.of(ITEM, FLUID, ENERGY, CHEMICAL);

    @Test
    void toggleAddsAndRemoves() {
        LinkerTabs tabs = LinkerTabs.of(ITEM).toggle(FLUID);
        assertEquals(List.of(ITEM, FLUID), tabs.types());
        assertEquals(List.of(FLUID), tabs.toggle(ITEM).types());
    }

    @Test
    void unavailableTypesAreKeptButIgnored() {
        LinkerTabs tabs = LinkerTabs.of(ITEM, CHEMICAL);
        assertEquals(List.of(ITEM, CHEMICAL), tabs.types());
        assertEquals(List.of(ITEM), tabs.effective(BASE));
        assertEquals(List.of(ITEM, CHEMICAL), tabs.effective(WITH_CHEMICALS));
        assertTrue(LinkerTabs.of(CHEMICAL).isEmpty(BASE));
        assertFalse(LinkerTabs.of(CHEMICAL).isEmpty(WITH_CHEMICALS));
    }

    @Test
    void allDependsOnWhatIsAvailable() {
        assertTrue(LinkerTabs.ALL.isAll(BASE));
        assertTrue(LinkerTabs.ALL.isAll(WITH_CHEMICALS));
        LinkerTabs three = LinkerTabs.of(ITEM, FLUID, ENERGY);
        assertTrue(three.isAll(BASE));
        assertFalse(three.isAll(WITH_CHEMICALS));
        assertEquals(LinkerTabs.of(WITH_CHEMICALS), LinkerTabs.available(WITH_CHEMICALS));
    }

    @Test
    void wheelCyclesAllThenEachAvailableType() {
        assertEquals(LinkerTabs.of(ITEM), LinkerTabs.ALL.next(1, BASE));
        assertEquals(LinkerTabs.of(ENERGY), LinkerTabs.of(FLUID).next(1, BASE));
        assertSame(LinkerTabs.ALL, LinkerTabs.of(ENERGY).next(1, BASE));
        assertEquals(LinkerTabs.of(CHEMICAL), LinkerTabs.of(ENERGY).next(1, WITH_CHEMICALS));
        assertEquals(LinkerTabs.of(CHEMICAL), LinkerTabs.ALL.next(-1, WITH_CHEMICALS));
        assertSame(LinkerTabs.ALL, LinkerTabs.of(ITEM, FLUID).next(1, BASE));
    }

    @Test
    void namesRoundTripAndIgnoreUnknown() {
        LinkerTabs tabs = LinkerTabs.of(FLUID, CHEMICAL);
        assertEquals(List.of("fluid", "chemical"), tabs.names());
        assertEquals(tabs, LinkerTabs.fromNames(List.of("fluid", "chemical", "plasma")));
    }
}
