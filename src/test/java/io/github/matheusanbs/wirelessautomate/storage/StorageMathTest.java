package io.github.matheusanbs.wirelessautomate.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StorageMathTest {
    @Test
    void acceptsUpToTheCapacity() {
        assertEquals(64, StorageMath.accept(0, 1_000, 64));
        assertEquals(10, StorageMath.accept(990, 1_000, 64));
        assertEquals(0, StorageMath.accept(1_000, 1_000, 64));
        // Acima da capacidade (config reduzida depois de encher): nada entra, sem conta negativa.
        assertEquals(0, StorageMath.accept(2_000, 1_000, 64));
        assertEquals(0, StorageMath.accept(0, 1_000, 0));
        assertEquals(0, StorageMath.accept(0, 1_000, -5));
    }

    @Test
    void unlimitedSaturatesAtLongMax() {
        assertEquals(12_000_000L, StorageMath.accept(0, 0, 12_000_000L));
        assertEquals(1, StorageMath.accept(Long.MAX_VALUE - 1, 0, 64));
        assertEquals(0, StorageMath.accept(Long.MAX_VALUE, -1, 64));
    }

    @Test
    void addSaturates() {
        assertEquals(5, StorageMath.add(2, 3));
        assertEquals(Long.MAX_VALUE, StorageMath.add(Long.MAX_VALUE, 1));
        assertEquals(Long.MAX_VALUE, StorageMath.add(Long.MAX_VALUE - 10, 100));
    }

    @Test
    void signalLikeAVanillaChest() {
        assertEquals(0, StorageMath.signal(0, 1_000));
        assertEquals(1, StorageMath.signal(1, 1_000));
        assertEquals(8, StorageMath.signal(500, 1_000));
        assertEquals(14, StorageMath.signal(999, 1_000));
        assertEquals(15, StorageMath.signal(1_000, 1_000));
        assertEquals(15, StorageMath.signal(5_000, 1_000));
        assertEquals(1, StorageMath.signal(12_000_000L, 0));
        assertEquals(0, StorageMath.signal(0, 0));
    }

    @Test
    void fillLevelGoesFromEmptyToFull() {
        assertEquals(0, StorageMath.fillLevel(0, 160_000));
        assertEquals(1, StorageMath.fillLevel(1, 160_000));
        assertEquals(1, StorageMath.fillLevel(16_000, 160_000));
        assertEquals(2, StorageMath.fillLevel(16_001, 160_000));
        assertEquals(5, StorageMath.fillLevel(80_000, 160_000));
        assertEquals(10, StorageMath.fillLevel(160_000, 160_000));
        assertEquals(10, StorageMath.fillLevel(999_999, 160_000));
    }

    @Test
    void fillLevelWithoutLimitIsFullWithAnything() {
        assertEquals(0, StorageMath.fillLevel(0, 0));
        assertEquals(10, StorageMath.fillLevel(1, 0));
        assertEquals(10, StorageMath.fillLevel(Long.MAX_VALUE, 0));
    }
}
