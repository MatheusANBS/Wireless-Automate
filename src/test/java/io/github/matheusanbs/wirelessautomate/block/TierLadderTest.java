package io.github.matheusanbs.wirelessautomate.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class TierLadderTest {
    /** Básico, Avançado, Elite, Esmeralda, Allthemodium, Vibranium, Unobtainium, Ultimate. */
    private static final List<String> MODS = Arrays.asList(null, null, null, null,
            "allthemodium", "allthemodium", "allthemodium", null);
    private static final Predicate<String> WITHOUT = mod -> false;
    private static final Predicate<String> WITH = "allthemodium"::equals;

    @Test
    void withoutTheModEmeraldGoesStraightToUltimate() {
        assertEquals(1, TierLadder.next(MODS, 0, WITHOUT));
        assertEquals(3, TierLadder.next(MODS, 2, WITHOUT));
        assertEquals(7, TierLadder.next(MODS, 3, WITHOUT));
        assertEquals(-1, TierLadder.next(MODS, 7, WITHOUT));
        assertEquals(3, TierLadder.previous(MODS, 7, WITHOUT));
        assertEquals(-1, TierLadder.previous(MODS, 0, WITHOUT));
    }

    @Test
    void withTheModEveryStepCounts() {
        for (int i = 0; i < 7; i++) {
            assertEquals(i + 1, TierLadder.next(MODS, i, WITH));
            assertEquals(i, TierLadder.previous(MODS, i + 1, WITH));
        }
    }

    @Test
    void aBlockLeftOnAnAtmTierStillGoesUp() {
        assertEquals(7, TierLadder.next(MODS, 5, WITHOUT));
        assertEquals(3, TierLadder.previous(MODS, 5, WITHOUT));
    }

    @Test
    void loaded() {
        assertTrue(TierLadder.loaded(null, WITHOUT));
        assertFalse(TierLadder.loaded("allthemodium", WITHOUT));
        assertTrue(TierLadder.loaded("allthemodium", WITH));
    }

    @Test
    void canUpgradeJumpsAnyNumberOfStepsUp() {
        assertTrue(TierLadder.canUpgrade(MODS, 0, 1, WITH));
        assertTrue(TierLadder.canUpgrade(MODS, 0, 7, WITH));
        assertTrue(TierLadder.canUpgrade(MODS, 2, 5, WITH));
    }

    @Test
    void canUpgradeNeverGoesDownOrStays() {
        assertFalse(TierLadder.canUpgrade(MODS, 2, 1, WITH));
        assertFalse(TierLadder.canUpgrade(MODS, 7, 0, WITH));
        assertFalse(TierLadder.canUpgrade(MODS, 2, 2, WITH));
    }

    @Test
    void canUpgradeRefusesATargetWhoseModIsMissing() {
        assertFalse(TierLadder.canUpgrade(MODS, 0, 5, WITHOUT));
        assertTrue(TierLadder.canUpgrade(MODS, 0, 3, WITHOUT));
    }

    @Test
    void canUpgradeFromATierWhoseModLeft() {
        assertTrue(TierLadder.canUpgrade(MODS, 5, 7, WITHOUT));
    }
}
