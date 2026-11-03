package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SegmentLayoutTest {
    @Test
    void partsOverlapByOnePixel() {
        int[] widths = SegmentLayout.widths(new int[] {27, 59}, false);
        assertArrayEquals(new int[] {39, 71}, widths);
        assertArrayEquals(new int[] {216, 254}, SegmentLayout.lefts(216, widths));
    }

    @Test
    void iconsAddSeventeen() {
        assertArrayEquals(new int[] {39 + 17}, SegmentLayout.widths(new int[] {27}, true));
    }

    @Test
    void clickPicksThePart() {
        int[] widths = {39, 71};
        assertEquals(0, SegmentLayout.at(216, widths, 216));
        assertEquals(1, SegmentLayout.at(216, widths, 254));
        assertEquals(1, SegmentLayout.at(216, widths, 324));
        assertEquals(-1, SegmentLayout.at(216, widths, 325));
        assertEquals(-1, SegmentLayout.at(216, widths, 215));
    }
}
