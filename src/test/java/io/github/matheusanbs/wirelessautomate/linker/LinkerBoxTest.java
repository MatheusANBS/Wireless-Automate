package io.github.matheusanbs.wirelessautomate.linker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LinkerBoxTest {
    @Test
    void cornersInAnyOrder() {
        LinkerBox box = LinkerBox.of(5, 70, -3, -2, 64, 4);
        assertEquals(new LinkerBox(-2, 64, -3, 5, 70, 4), box);
        assertEquals(8, box.sizeX());
        assertEquals(7, box.sizeY());
        assertEquals(8, box.sizeZ());
        assertEquals(8L * 7 * 8, box.volume());
    }

    @Test
    void singleBlockHasVolumeOne() {
        assertEquals(1L, LinkerBox.of(3, 3, 3, 3, 3, 3).volume());
    }

    @Test
    void volumeDoesNotOverflow() {
        LinkerBox box = LinkerBox.of(-30_000_000, -64, -30_000_000, 30_000_000, 319, 30_000_000);
        assertTrue(box.volume() > Integer.MAX_VALUE);
    }

    @Test
    void containsIsInclusive() {
        LinkerBox box = LinkerBox.of(0, 0, 0, 2, 2, 2);
        assertTrue(box.contains(0, 0, 0));
        assertTrue(box.contains(2, 2, 2));
        assertTrue(box.contains(1, 2, 0));
        assertFalse(box.contains(3, 0, 0));
        assertFalse(box.contains(0, -1, 0));
        assertFalse(box.contains(0, 0, 3));
    }

    @Test
    void chunksCoveredIncludingNegatives() {
        LinkerBox box = LinkerBox.of(-1, 0, 0, 16, 0, 15);
        assertEquals(-1, box.minChunkX());
        assertEquals(1, box.maxChunkX());
        assertEquals(0, box.minChunkZ());
        assertEquals(0, box.maxChunkZ());
        assertEquals(3L, box.chunkCount());
    }

    @Test
    void distanceIsZeroInsideAndGrowsOutside() {
        LinkerBox box = LinkerBox.of(0, 0, 0, 9, 9, 9);
        assertEquals(0.0, box.distanceSqTo(5.5, 5, 5.5));
        assertEquals(0.0, box.distanceSqTo(10, 10, 10));
        assertEquals(4.0, box.distanceSqTo(12, 5, 5));
        assertEquals(9.0 + 16.0, box.distanceSqTo(-3, 5, 14));
    }

    @Test
    void rejectsInvertedBox() {
        assertThrows(IllegalArgumentException.class, () -> new LinkerBox(1, 0, 0, 0, 0, 0));
    }
}
