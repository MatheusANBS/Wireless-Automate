package io.github.matheusanbs.wirelessautomate.preset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import org.junit.jupiter.api.Test;

class PasteTypesTest {
    @Test
    void forwardWithoutChemicalsSkipsThem() {
        assertEquals(ResourceType.ITEM, PasteTypes.next(null, 1, false));
        assertEquals(ResourceType.FLUID, PasteTypes.next(ResourceType.ITEM, 1, false));
        assertEquals(ResourceType.ENERGY, PasteTypes.next(ResourceType.FLUID, 1, false));
        assertNull(PasteTypes.next(ResourceType.ENERGY, 1, false));
    }

    @Test
    void forwardWithChemicals() {
        assertEquals(ResourceType.CHEMICAL, PasteTypes.next(ResourceType.ENERGY, 1, true));
        assertNull(PasteTypes.next(ResourceType.CHEMICAL, 1, true));
    }

    @Test
    void backwardWrapsAround() {
        assertEquals(ResourceType.ENERGY, PasteTypes.next(null, -1, false));
        assertEquals(ResourceType.CHEMICAL, PasteTypes.next(null, -1, true));
        assertNull(PasteTypes.next(ResourceType.ITEM, -5, true));
    }

    @Test
    void chemicalsWithoutMekanismCountAsAll() {
        assertEquals(ResourceType.ITEM, PasteTypes.next(ResourceType.CHEMICAL, 1, false));
        assertEquals(ResourceType.ENERGY, PasteTypes.next(ResourceType.CHEMICAL, -1, false));
    }

    @Test
    void zeroDirectionStays() {
        assertEquals(ResourceType.FLUID, PasteTypes.next(ResourceType.FLUID, 0, true));
    }

    @Test
    void cycleOrder() {
        assertArrayEquals(new ResourceType[] {null, ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY},
                PasteTypes.cycle(false));
        assertEquals(5, PasteTypes.cycle(true).length);
    }
}
