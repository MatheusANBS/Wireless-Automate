package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvItemSearchTest {
    @Test
    void emptyMatchesEverything() {
        assertTrue(TvItemSearch.parse("  ").isEmpty());
        assertTrue(TvItemSearch.EMPTY.matches("Diamond", "minecraft", "diamond"));
    }

    @Test
    void foldsCaseAndAccents() {
        assertEquals("pao", TvItemSearch.fold("PÃO"));
        assertTrue(TvItemSearch.parse("pao").matches("Pão", "mod", "bread"));
    }

    @Test
    void allWordsMustMatchNameOrId() {
        TvItemSearch search = TvItemSearch.parse("iron ingot");
        assertTrue(search.matches("Iron Ingot", "minecraft", "iron_ingot"));
        assertFalse(search.matches("Iron Block", "minecraft", "iron_block"));
        assertTrue(TvItemSearch.parse("iron_ingot").words().size() == 1);
        assertTrue(TvItemSearch.parse("raw iron").matches("Ferro", "minecraft", "raw_iron"));
    }

    @Test
    void modFilterUsesTheNamespace() {
        TvItemSearch search = TvItemSearch.parse("@mekanism steel");
        assertTrue(search.matches("Steel Ingot", "mekanism", "ingot_steel"));
        assertFalse(search.matches("Steel Ingot", "minecraft", "ingot_steel"));
        assertTrue(TvItemSearch.parse("@mek").matches("X", "mekanism", "x"));
    }
}
