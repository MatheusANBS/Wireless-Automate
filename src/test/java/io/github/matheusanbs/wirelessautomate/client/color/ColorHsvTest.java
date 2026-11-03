package io.github.matheusanbs.wirelessautomate.client.color;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ColorHsvTest {
    @Test
    void hsvPrimaries() {
        assertEquals(0xFF0000, ColorHsv.toRgb(0, 1, 1));
        assertEquals(0x00FF00, ColorHsv.toRgb(1f / 3, 1, 1));
        assertEquals(0x0000FF, ColorHsv.toRgb(2f / 3, 1, 1));
        assertEquals(0xFFFFFF, ColorHsv.toRgb(0.4f, 0, 1));
        assertEquals(0, ColorHsv.toRgb(0.4f, 1, 0));
    }

    @Test
    void hsvRoundTrip() {
        for (int rgb : new int[] {0x4FD1C5, 0xE39A2E, 0x336699, 0x8A8EE8, 0x101010, 0xFFFFFF}) {
            float[] hsv = ColorHsv.fromRgb(rgb);
            assertEquals(rgb, ColorHsv.toRgb(hsv[0], hsv[1], hsv[2]));
        }
    }
}
