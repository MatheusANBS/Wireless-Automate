package io.github.matheusanbs.wirelessautomate.range;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RangeTiersTest {
    @Test
    void defaultRadiusPerTier() {
        int[] expected = {0, 6, 12, 16, 24, 32, 48, 64};
        for (int i = 0; i < 8; i++) {
            assertEquals(expected[i], RangeTiers.defaultRadius(i));
        }
    }

    @Test
    void defaultMachinesPerTier() {
        int[] expected = {0, 8, 16, 32, 48, 64, 96, 128};
        for (int i = 0; i < 8; i++) {
            assertEquals(expected[i], RangeTiers.defaultMaxMachines(i));
        }
    }

    @Test
    void defaultsFitTheConfigRanges() {
        for (int i = 0; i < 8; i++) {
            assertTrue(RangeTiers.defaultRadius(i) <= RangeTiers.MAX_RADIUS);
            assertTrue(RangeTiers.defaultMaxMachines(i) <= RangeTiers.MAX_MACHINES);
        }
    }
}
