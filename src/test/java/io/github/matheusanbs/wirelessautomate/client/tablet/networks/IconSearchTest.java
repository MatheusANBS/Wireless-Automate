package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IconSearchTest {
    @Test
    void matchesIdOrNameIgnoringCaseAndAccents() {
        assertTrue(IconSearch.matches("pick", "minecraft:iron_pickaxe", "Picareta de Ferro"));
        assertTrue(IconSearch.matches("PICARETA", "minecraft:iron_pickaxe", "Picareta de Ferro"));
        assertTrue(IconSearch.matches("fundicao", "x:y", "Fundição"));
        assertTrue(IconSearch.matches("mekanism:", "mekanism:steel_ingot", "Steel Ingot"));
        assertFalse(IconSearch.matches("diamante", "minecraft:iron_pickaxe", "Picareta de Ferro"));
    }

    @Test
    void shortQueriesDoNotSearch() {
        assertFalse(IconSearch.searches("a"));
        assertFalse(IconSearch.searches(" a "));
        assertTrue(IconSearch.searches("ab"));
    }

    @Test
    void foldKeepsAsciiAndDropsAccents() {
        assertEquals("fundicao", IconSearch.fold("FUNDIÇÃO"));
        assertEquals("minecraft:diamond", IconSearch.fold("minecraft:Diamond"));
    }

    @Test
    void exactThenPrefixThenContainsInRegistryOrder() {
        String[] ids = {"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:diamond",
                "minecraft:stone", "minecraft:diamond_block"};
        String[] names = {"minério de diamante", "minério de diamante de ardósia", "diamante", "pedra",
                "bloco de diamante"};
        assertArrayEquals(new int[] {2, 0, 4, 1}, IconSearch.order("Diamond", ids, names, 256));
        // o nome conta igual ao id: "diamante" é o nome exato do diamante e o começo de nenhum outro
        assertArrayEquals(new int[] {2, 0, 1, 4}, IconSearch.order("diamante", ids, names, 256));
    }

    @Test
    void orderStopsAtTheLimit() {
        String[] ids = {"a:xx1", "a:xx2", "a:xx3", "a:xx"};
        String[] names = {"", "", "", ""};
        assertArrayEquals(new int[] {3, 0}, IconSearch.order("xx", ids, names, 2));
        assertArrayEquals(new int[0], IconSearch.order("zz", ids, names, 2));
    }
}
