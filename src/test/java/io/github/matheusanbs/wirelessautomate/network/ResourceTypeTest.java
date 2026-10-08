package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceTypeTest {
    @Test
    void keysAreTheSavedNamesAndUnique() {
        assertEquals("item", ResourceType.ITEM.key());
        assertEquals("fluid", ResourceType.FLUID.key());
        assertEquals("energy", ResourceType.ENERGY.key());
        assertEquals("chemical", ResourceType.CHEMICAL.key());
        Set<String> keys = new HashSet<>();
        for (ResourceType type : ResourceType.values()) {
            assertTrue(keys.add(type.key()), "chave repetida: " + type.key());
        }
    }

    @Test
    void byKeyFindsAndRejects() {
        assertSame(ResourceType.FLUID, ResourceType.byKey("fluid"));
        assertNull(ResourceType.byKey("FLUID"));
        assertNull(ResourceType.byKey("source"));
    }

    @Test
    void filterAndCards() {
        assertTrue(ResourceType.ITEM.filtered() && ResourceType.ITEM.cards());
        assertTrue(ResourceType.FLUID.filtered() && ResourceType.FLUID.cards());
        assertFalse(ResourceType.ENERGY.filtered() || ResourceType.ENERGY.cards());
        assertTrue(ResourceType.CHEMICAL.filtered());
        assertFalse(ResourceType.CHEMICAL.cards());
    }

    @Test
    void defaultRatesMatchTheTierTable() {
        assertEquals(512L, ResourceType.ITEM.defaultRate(0));
        assertEquals(131_072L, ResourceType.ITEM.defaultRate(2));
        assertEquals(512_000L, ResourceType.FLUID.defaultRate(1));
        assertEquals(4_000_000L, ResourceType.ENERGY.defaultRate(2));
        assertEquals(0L, ResourceType.ENERGY.defaultRate(3));
        assertEquals(ResourceType.FLUID.defaultRate(1), ResourceType.CHEMICAL.defaultRate(1));
    }

    @Test
    void rateKeysKeepTheConfigNames() {
        assertEquals("itemsPerSecond", ResourceType.ITEM.rateKey());
        assertEquals("fluidPerSecond", ResourceType.FLUID.rateKey());
        assertEquals("fluidPerSecond", ResourceType.CHEMICAL.rateKey());
        assertEquals("energyPerTick", ResourceType.ENERGY.rateKey());
        assertTrue(ResourceType.ENERGY.ratePerTick());
        assertFalse(ResourceType.ITEM.ratePerTick());
    }

    @Test
    void availableDependsOnTheMods() {
        assertEquals(List.of(ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY),
                ResourceType.available(mod -> false));
        assertEquals(List.of(ResourceType.values()), ResourceType.available(mod -> mod.equals("mekanism")));
    }
}
