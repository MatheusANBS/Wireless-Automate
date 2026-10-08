package io.github.matheusanbs.wirelessautomate.preset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class PasteTypesTest {
    private static final List<ResourceType> BASE = List.of(ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY);
    private static final List<ResourceType> WITH_CHEMICALS = List.of(ResourceType.values());

    @Test
    void forwardSkipsUnavailable() {
        assertEquals(ResourceType.ITEM, PasteTypes.next(null, 1, BASE));
        assertEquals(ResourceType.FLUID, PasteTypes.next(ResourceType.ITEM, 1, BASE));
        assertEquals(ResourceType.ENERGY, PasteTypes.next(ResourceType.FLUID, 1, BASE));
        assertNull(PasteTypes.next(ResourceType.ENERGY, 1, BASE));
    }

    @Test
    void forwardWithChemicals() {
        assertEquals(ResourceType.CHEMICAL, PasteTypes.next(ResourceType.ENERGY, 1, WITH_CHEMICALS));
        assertNull(PasteTypes.next(ResourceType.CHEMICAL, 1, WITH_CHEMICALS));
    }

    @Test
    void backwardWrapsAround() {
        assertEquals(ResourceType.ENERGY, PasteTypes.next(null, -1, BASE));
        assertEquals(ResourceType.CHEMICAL, PasteTypes.next(null, -1, WITH_CHEMICALS));
        assertNull(PasteTypes.next(ResourceType.ITEM, -5, WITH_CHEMICALS));
    }

    @Test
    void unavailableCountsAsAll() {
        assertEquals(ResourceType.ITEM, PasteTypes.next(ResourceType.CHEMICAL, 1, BASE));
        assertEquals(ResourceType.ENERGY, PasteTypes.next(ResourceType.CHEMICAL, -1, BASE));
    }

    @Test
    void zeroDirectionStays() {
        assertEquals(ResourceType.FLUID, PasteTypes.next(ResourceType.FLUID, 0, WITH_CHEMICALS));
    }

    @Test
    void cycleOrder() {
        assertArrayEquals(new ResourceType[] {null, ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY},
                PasteTypes.cycle(BASE));
        assertEquals(5, PasteTypes.cycle(WITH_CHEMICALS).length);
    }
}
