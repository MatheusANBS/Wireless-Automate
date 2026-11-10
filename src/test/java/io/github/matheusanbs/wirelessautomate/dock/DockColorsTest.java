package io.github.matheusanbs.wirelessautomate.dock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DockColorsTest {
    @Test
    void colorsByDirectionOrdinal() {
        assertEquals(0xFFC2486B, DockColors.side(0));
        assertEquals(0xFF8E4FC9, DockColors.side(1));
        assertEquals(0xFFB57A3A, DockColors.side(2));
        assertEquals(0xFF2F6FD6, DockColors.side(3));
        assertEquals(0xFF5E9A2B, DockColors.side(4));
        assertEquals(0xFFD9931A, DockColors.side(5));
    }

    @Test
    void screenOrderIsNorthSouthEastWestUpDown() {
        assertArrayEquals(new int[] {2, 3, 5, 4, 1, 0}, DockColors.ORDER);
    }
}
