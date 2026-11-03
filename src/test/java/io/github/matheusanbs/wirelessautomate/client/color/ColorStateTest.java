package io.github.matheusanbs.wirelessautomate.client.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColorStateTest {
    private static final int[] NO_THEME = new int[6];

    @Test
    void readsTheStoredColor() {
        ColorState state = new ColorState(0xFF6B6FD0, false, NO_THEME);
        assertEquals(0xFF6B6FD0, state.value());
        assertEquals(0x6B6FD0, ColorHsv.toRgb(state.h(), state.s(), state.v()));
        assertEquals(255, state.a());
    }

    @Test
    void squareSetsSaturationAndBrightness() {
        ColorState state = new ColorState(0xFFFF0000, false, NO_THEME);
        state.square(0, 0);
        assertEquals(0xFFFFFFFF, state.value());
        state.square(1, 1);
        assertEquals(0xFF000000, state.value());
        state.square(2, -1);
        assertEquals(0xFFFF0000, state.value());
    }

    @Test
    void hueStopsBeforeWrapping() {
        ColorState state = new ColorState(0xFFFF0000, false, NO_THEME);
        state.hue(1.5);
        assertTrue(state.h() < 1f);
        state.hue(-1);
        assertEquals(0f, state.h());
    }

    @Test
    void alphaOnlyWhenAllowed() {
        ColorState solid = new ColorState(0x80FF0000, false, NO_THEME);
        assertEquals(255, solid.a());
        ColorState clear = new ColorState(0x80FF0000, true, NO_THEME);
        assertEquals(0x80, clear.a());
        clear.alpha(1);
        assertEquals(1, clear.a());
        clear.alpha(0);
        assertEquals(255, clear.a());
    }

    @Test
    void hexAcceptsSixOrEightDigits() {
        ColorState state = new ColorState(0xFF000000, false, NO_THEME);
        assertFalse(state.typed("#12345", false));
        assertTrue(state.typed("123456", false));
        assertEquals(0xFF123456, state.value());
        assertFalse(state.typed("#12345600", false));
        assertTrue(state.typed("#12345600", true));
        assertEquals(0, state.value());
    }

    @Test
    void themeRoleReadsTheThemeColor() {
        int[] theme = {0xFF101010, 0xFFFF0000, 0, 0, 0, 0};
        ColorState state = new ColorState(2, false, theme);
        assertEquals(2, state.value());
        assertEquals(0xFF0000, ColorHsv.toRgb(state.h(), state.s(), state.v()));
    }

    @Test
    void pickedColorNeedsAlpha() {
        ColorState state = new ColorState(0xFF000000, false, NO_THEME);
        assertFalse(state.picked(0x00FFFFFF));
        assertTrue(state.picked(0x40FF0000));
        assertEquals(0xFFFF0000, state.value());
    }
}
