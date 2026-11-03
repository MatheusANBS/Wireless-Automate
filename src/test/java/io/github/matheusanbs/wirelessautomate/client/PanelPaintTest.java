package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** O PanelPaint carrega classes do Minecraft, ausentes no JUnit: a conta do {@code shade} fica no GuiPalette. */
class PanelPaintTest {
    @Test
    void shadeKeepsAlphaAndScalesChannels() {
        assertEquals(0xFF7F7F7F, GuiPalette.shade(0xFFFFFFFF, 0.5f));
        assertEquals(0xFFA8432B, GuiPalette.shade(0xFFF0603E, 0.7f));
        assertEquals(0x80000000, GuiPalette.shade(0x80000000, 0.3f));
    }
}
