package io.github.matheusanbs.wirelessautomate.client.color;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RecentColorsTest {
    @Test
    void recentColorsKeepEightNewestFirst() {
        RecentColors.clear();
        for (int i = 1; i <= 10; i++) {
            RecentColors.push(0xFF000000 | i);
        }
        assertEquals(RecentColors.MAX, RecentColors.size());
        assertEquals(0xFF00000A, RecentColors.get(0));
        assertEquals(0xFF000003, RecentColors.get(7));
        RecentColors.push(0xFF000005);
        assertEquals(0xFF000005, RecentColors.get(0));
        assertEquals(0xFF00000A, RecentColors.get(1));
        assertEquals(RecentColors.MAX, RecentColors.size());
        RecentColors.push(0);
        RecentColors.push(3);
        assertEquals(0xFF000005, RecentColors.get(0));
        RecentColors.clear();
    }
}
