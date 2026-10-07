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

    @Test
    void toggleAddsAndRemoves() {
        LinkerTabs tabs = LinkerTabs.of(ITEM).toggle(FLUID);
        assertEquals(List.of(ITEM, FLUID), tabs.types());
        assertEquals(List.of(FLUID), tabs.toggle(ITEM).types());
    }

    @Test
    void chemicalsAreKeptButIgnoredWithoutMekanism() {
        LinkerTabs tabs = LinkerTabs.of(ITEM, CHEMICAL);
        assertEquals(List.of(ITEM, CHEMICAL), tabs.types());
        assertEquals(List.of(ITEM), tabs.effective(false));
        assertEquals(List.of(ITEM, CHEMICAL), tabs.effective(true));
        assertTrue(LinkerTabs.of(CHEMICAL).isEmpty(false));
        assertFalse(LinkerTabs.of(CHEMICAL).isEmpty(true));
    }

    @Test
    void allDependsOnMekanism() {
        assertTrue(LinkerTabs.ALL.isAll(false));
        assertTrue(LinkerTabs.ALL.isAll(true));
        LinkerTabs threeTabs = LinkerTabs.of(ITEM, FLUID, ENERGY);
        assertTrue(threeTabs.isAll(false));
        assertFalse(threeTabs.isAll(true));
        assertEquals(List.of(ITEM, FLUID, ENERGY), LinkerTabs.ALL.effective(false));
    }

    @Test
    void namesRoundTripAndUnknownNamesAreIgnored() {
        LinkerTabs tabs = LinkerTabs.of(FLUID, CHEMICAL);
        assertEquals(List.of("fluid", "chemical"), tabs.names());
        assertEquals(tabs, LinkerTabs.fromNames(tabs.names()));
        assertEquals(LinkerTabs.of(ENERGY), LinkerTabs.fromNames(List.of("energy", "gas", "ENERGY")));
    }

    @Test
    void maskOutsideTheTypesIsDropped() {
        assertEquals(LinkerTabs.ALL, new LinkerTabs(-1));
    }

    @Test
    void wheelForwardWithoutChemicals() {
        LinkerTabs tabs = LinkerTabs.ALL;
        tabs = tabs.next(1, false);
        assertEquals(LinkerTabs.of(ITEM), tabs);
        tabs = tabs.next(1, false);
        assertEquals(LinkerTabs.of(FLUID), tabs);
        tabs = tabs.next(1, false);
        assertEquals(LinkerTabs.of(ENERGY), tabs);
        assertEquals(LinkerTabs.ALL, tabs.next(1, false));
    }

    @Test
    void wheelWithChemicals() {
        assertEquals(LinkerTabs.of(CHEMICAL), LinkerTabs.of(ENERGY).next(1, true));
        assertEquals(LinkerTabs.ALL, LinkerTabs.of(CHEMICAL).next(1, true));
        assertEquals(LinkerTabs.of(CHEMICAL), LinkerTabs.ALL.next(-1, true));
        assertEquals(LinkerTabs.of(ENERGY), LinkerTabs.ALL.next(-1, false));
    }

    @Test
    void customCombinationGoesToAll() {
        LinkerTabs custom = LinkerTabs.of(ITEM, FLUID);
        assertEquals(LinkerTabs.ALL, custom.next(1, true));
        assertEquals(LinkerTabs.ALL, custom.next(-1, false));
    }

    @Test
    void shortcutIsFoundByEffectiveTabs() {
        // Itens + Químicos sem o Mekanism vale como Itens: o próximo é Fluidos
        assertEquals(LinkerTabs.of(FLUID), LinkerTabs.of(ITEM, CHEMICAL).next(1, false));
        // três abas sem o Mekanism são Todos
        assertEquals(LinkerTabs.of(ITEM), LinkerTabs.of(ITEM, FLUID, ENERGY).next(1, false));
    }

    @Test
    void directionZeroStays() {
        LinkerTabs custom = LinkerTabs.of(ITEM, FLUID);
        assertSame(custom, custom.next(0, true));
    }
}
