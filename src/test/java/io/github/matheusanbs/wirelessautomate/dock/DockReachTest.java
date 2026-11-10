package io.github.matheusanbs.wirelessautomate.dock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matheusanbs.wirelessautomate.range.RangeTiers;
import org.junit.jupiter.api.Test;

class DockReachTest {
    @Test
    void readsTheRowOfTheNextTier() {
        assertEquals(24, DockReach.radius(4, RangeTiers::defaultRadius, 0));
        assertEquals(48, DockReach.max(4, RangeTiers::defaultMaxMachines, 0));
    }

    @Test
    void emeraldReadsAllthemodiumRowEvenWithoutTheMod() {
        // Esmeralda é o ordinal 3: o chamador passa 3 + 1, e a linha 4 existe nos padrões sempre.
        int next = 3 + 1;
        assertEquals(24, DockReach.radius(next, RangeTiers::defaultRadius, DockReach.ULTIMATE_RADIUS));
        assertEquals(48, DockReach.max(next, RangeTiers::defaultMaxMachines, DockReach.ULTIMATE_MACHINES));
    }

    @Test
    void ultimateUsesItsOwnValues() {
        assertEquals(96, DockReach.radius(-1, RangeTiers::defaultRadius, DockReach.ULTIMATE_RADIUS));
        assertEquals(256, DockReach.max(-1, RangeTiers::defaultMaxMachines, DockReach.ULTIMATE_MACHINES));
    }

    @Test
    void ultimateRadiusIsCappedAt128() {
        assertEquals(DockReach.MAX_ULTIMATE_RADIUS, DockReach.radius(-1, RangeTiers::defaultRadius, 500));
        assertEquals(128, DockReach.radius(-1, RangeTiers::defaultRadius, 128));
    }
}
