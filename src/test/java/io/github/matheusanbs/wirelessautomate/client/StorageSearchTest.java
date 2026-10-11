package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StorageSearchTest {
    @Test
    void emptyAndBlank() {
        assertTrue(StorageSearch.parse("").isEmpty());
        assertTrue(StorageSearch.parse("   ").isEmpty());
        assertTrue(StorageSearch.parse("# @").isEmpty());
    }

    @Test
    void wordsModsAndTags() {
        StorageSearch search = StorageSearch.parse("Iron @Mekanism #ingots");
        assertEquals(List.of("iron"), search.words());
        assertEquals(List.of("mekanism"), search.mods());
        assertEquals(List.of("ingots"), search.tags());
    }

    @Test
    void tagRunsUntilTheNextMarker() {
        StorageSearch search = StorageSearch.parse("#not yet eaten @farmersdelight");
        assertEquals(List.of("not yet eaten"), search.tags());
        assertEquals(List.of("farmersdelight"), search.mods());
        assertTrue(search.words().isEmpty());

        StorageSearch two = StorageSearch.parse("pie #c:foods #not  yet");
        assertEquals(List.of("pie"), two.words());
        assertEquals(List.of("c:foods", "not yet"), two.tags());
    }

    @Test
    void loneHashStartsAPhrase() {
        assertEquals(List.of("not eaten"), StorageSearch.parse("# not eaten").tags());
    }
}
