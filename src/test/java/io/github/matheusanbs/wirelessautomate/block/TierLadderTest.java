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

    @Test
    void cardWithOriginNeedsExactTier() {
        assertTrue(TierLadder.cardApplies(MODS, 0, 2, 0, WITH));
        assertFalse(TierLadder.cardApplies(MODS, 0, 2, 1, WITH));
        assertFalse(TierLadder.cardApplies(MODS, 0, 2, 2, WITH));
        assertFalse(TierLadder.cardApplies(MODS, 0, 2, 7, WITH));
    }

    @Test
    void cardWithOriginNeverGoesDown() {
        assertFalse(TierLadder.cardApplies(MODS, 2, 1, 2, WITH));
    }

    @Test
    void legacyCardKeepsAnyLowerTier() {
        assertTrue(TierLadder.cardApplies(MODS, -1, 7, 0, WITH));
        assertFalse(TierLadder.cardApplies(MODS, -1, 1, 2, WITH));
        assertFalse(TierLadder.cardApplies(MODS, -1, 2, 2, WITH));
    }

    @Test
    void cardToAtmTierNeedsTheMod() {
        assertTrue(TierLadder.cardApplies(MODS, 3, 4, 3, WITH));
        assertFalse(TierLadder.cardApplies(MODS, 3, 4, 3, WITHOUT));
    }

    @Test
    void oneStepFollowsTheLadder() {
        assertTrue(TierLadder.oneStep(MODS, 3, 7, WITHOUT));
        assertFalse(TierLadder.oneStep(MODS, 3, 4, WITHOUT));
        assertTrue(TierLadder.oneStep(MODS, 3, 4, WITH));
        assertFalse(TierLadder.oneStep(MODS, 3, 7, WITH));
        assertFalse(TierLadder.oneStep(MODS, 0, 2, WITH));
        for (int to = 0; to < MODS.size(); to++) {
            assertFalse(TierLadder.oneStep(MODS, 7, to, WITH));
            assertFalse(TierLadder.oneStep(MODS, 7, to, WITHOUT));
        }
    }

    @Test
    void oneStepFromAnOrphanAtmTier() {
        assertTrue(TierLadder.oneStep(MODS, 5, 7, WITHOUT));
    }

    @Test
    void stepCardOrigin() {
        assertEquals(3, TierLadder.previous(MODS, 7, WITHOUT));
        assertEquals(6, TierLadder.previous(MODS, 7, WITH));
        assertEquals(0, TierLadder.previous(MODS, 1, WITH));
        assertEquals(0, TierLadder.previous(MODS, 1, WITHOUT));
    }
}
