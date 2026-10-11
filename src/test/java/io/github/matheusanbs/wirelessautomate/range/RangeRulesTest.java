package io.github.matheusanbs.wirelessautomate.range;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import org.junit.jupiter.api.Test;

class RangeRulesTest {
    @Test
    void chebyshevIsTheLargestAxis() {
        assertEquals(0, RangeRules.chebyshev(1, 2, 3, 1, 2, 3));
        assertEquals(7, RangeRules.chebyshev(0, 0, 0, 3, -7, 5));
        assertEquals(4, RangeRules.chebyshev(-2, 0, 0, 2, 1, 1));
    }

    @Test
    void withinRadiusIncludesTheEdge() {
        assertTrue(RangeRules.withinRadius(0, 0, 0, 6, 6, -6, 6));
        assertFalse(RangeRules.withinRadius(0, 0, 0, 7, 0, 0, 6));
    }

    @Test
    void okWhenEverythingFits() {
        assertEquals(LinkRefusal.NONE, RangeRules.check(true, 6, true, 6, 7, 8));
    }

    @Test
    void refusesWithoutUpgradeFirst() {
        assertEquals(LinkRefusal.NO_UPGRADE, RangeRules.check(false, 0, false, 99, 99, 0));
    }

    @Test
    void refusesWhenTierHasNoRange() {
        assertEquals(LinkRefusal.NO_RANGE, RangeRules.check(true, 0, false, 99, 99, 0));
    }

    @Test
    void refusesOtherDimension() {
        assertEquals(LinkRefusal.OTHER_DIMENSION, RangeRules.check(true, 6, false, 99, 99, 8));
    }

    @Test
    void refusesOutOfRange() {
        assertEquals(LinkRefusal.OUT_OF_RANGE, RangeRules.check(true, 6, true, 7, 99, 8));
    }

    @Test
    void refusesAtExactLimit() {
        assertEquals(LinkRefusal.LIMIT, RangeRules.check(true, 6, true, 3, 8, 8));
        assertEquals(LinkRefusal.NONE, RangeRules.check(true, 6, true, 3, 7, 8));
    }

    @Test
    void refusalKeys() {
        assertEquals("range.refusal.no_upgrade", LinkRefusal.NO_UPGRADE.key());
        assertEquals("range.refusal.out_of_range", LinkRefusal.OUT_OF_RANGE.key());
        assertEquals("range.refusal.none", LinkRefusal.NONE.key());
    }

    @Test
    void reachSkipsOutOfRangeWithoutSpendingSlots() {
        RangeRules.Reach[] reach = RangeRules.reach(new int[] {9, 1, 2, 3, 0}, 6, 2);
        assertEquals(RangeRules.Reach.OUT_OF_RANGE, reach[0]);
        assertEquals(RangeRules.Reach.ACTIVE, reach[1]);
        assertEquals(RangeRules.Reach.ACTIVE, reach[2]);
        assertEquals(RangeRules.Reach.OVER_LIMIT, reach[3]);
        assertEquals(RangeRules.Reach.OVER_LIMIT, reach[4]);
    }

    @Test
    void reachWithZeroLimitOrRadius() {
        assertEquals(RangeRules.Reach.OVER_LIMIT, RangeRules.reach(new int[] {1}, 6, 0)[0]);
        assertEquals(RangeRules.Reach.OUT_OF_RANGE, RangeRules.reach(new int[] {1}, 0, 8)[0]);
        assertEquals(RangeRules.Reach.ACTIVE, RangeRules.reach(new int[] {6}, 6, 1)[0]);
    }
}
