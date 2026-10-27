package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvPickerMathTest {
    @Test
    void hsvPrimaries() {
        assertEquals(0xFF0000, TvHsv.toRgb(0, 1, 1));
        assertEquals(0x00FF00, TvHsv.toRgb(1f / 3, 1, 1));
        assertEquals(0x0000FF, TvHsv.toRgb(2f / 3, 1, 1));
        assertEquals(0xFFFFFF, TvHsv.toRgb(0.4f, 0, 1));
        assertEquals(0, TvHsv.toRgb(0.4f, 1, 0));
    }

    @Test
    void hsvRoundTrip() {
        for (int rgb : new int[] {0x4FD1C5, 0xE39A2E, 0x336699, 0x8A8EE8, 0x101010, 0xFFFFFF}) {
            float[] hsv = TvHsv.fromRgb(rgb);
            assertEquals(rgb, TvHsv.toRgb(hsv[0], hsv[1], hsv[2]));
        }
    }

    @Test
    void recentColorsKeepEightNewestFirst() {
        TvRecentColors.clear();
        for (int i = 1; i <= 10; i++) {
            TvRecentColors.push(0xFF000000 | i);
        }
        assertEquals(TvRecentColors.MAX, TvRecentColors.size());
        assertEquals(0xFF00000A, TvRecentColors.get(0));
        assertEquals(0xFF000003, TvRecentColors.get(7));
        TvRecentColors.push(0xFF000005);
        assertEquals(0xFF000005, TvRecentColors.get(0));
        assertEquals(0xFF00000A, TvRecentColors.get(1));
        assertEquals(TvRecentColors.MAX, TvRecentColors.size());
        TvRecentColors.push(0);
        TvRecentColors.push(3);
        assertEquals(0xFF000005, TvRecentColors.get(0));
        TvRecentColors.clear();
    }

    @Test
    void numberDrag() {
        assertFalse(TvNumberDrag.started(2));
        assertTrue(TvNumberDrag.started(-3));
        assertEquals(15, TvNumberDrag.value(10, 20, 1, 0, 100, false));
        assertEquals(30, TvNumberDrag.value(10, 20, 1, 0, 100, true));
        assertEquals(0, TvNumberDrag.value(10, -200, 1, 0, 100, false));
        assertEquals(100, TvNumberDrag.value(10, 9999, 5, 0, 100, false));
        assertEquals(10, TvNumberDrag.value(10, 3, 1, 0, 100, false));
    }
}
