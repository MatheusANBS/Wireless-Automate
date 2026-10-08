package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TabLayoutTest {
    private static final int[] FULL = {50, 52, 50, 66, 48};

    @Test
    void everyNameWhenItFits() {
        TabLayout.Result r = TabLayout.choose(FULL, 0, 400);
        assertEquals(TabLayout.Mode.FULL, r.mode());
        assertArrayEquals(FULL, r.widths());
    }

    @Test
    void onlyTheActiveNameWhenTight() {
        TabLayout.Result r = TabLayout.choose(FULL, 3, 180);
        assertEquals(TabLayout.Mode.ACTIVE_NAME, r.mode());
        assertArrayEquals(new int[] {24, 24, 24, 66, 24}, r.widths());
    }

    @Test
    void iconsWhenEvenThatDoesNotFit() {
        TabLayout.Result r = TabLayout.choose(FULL, 3, 110);
        assertEquals(TabLayout.Mode.ICONS, r.mode());
        assertArrayEquals(new int[] {24, 24, 24, 24, 24}, r.widths());
    }

    @Test
    void exactFitCountsAsFitting() {
        int total = 50 + 52 + 50 + 66 + 48 + 4 * TabLayout.GAP;
        assertEquals(TabLayout.Mode.FULL, TabLayout.choose(FULL, 0, total).mode());
        assertEquals(TabLayout.Mode.ACTIVE_NAME, TabLayout.choose(FULL, 0, total - 1).mode());
    }
}
