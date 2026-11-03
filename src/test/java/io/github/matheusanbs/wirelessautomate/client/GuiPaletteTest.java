package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GuiPaletteTest {
    @Test
    void lightIsTodaysPalette() {
        GuiPalette p = GuiPalette.of(GuiTheme.LIGHT);
        assertEquals(0xFFF2EBDD, p.panel());
        assertEquals(0xFFFFFBF3, p.bevelLight());
        assertEquals(0xFFDDD3BF, p.inset());
        assertEquals(0xFFBDB19A, p.bevelDark());
        assertEquals(0xFF2A2730, p.fg());
        assertEquals(0xFF7A7366, p.muted());
        assertEquals(0xFFF0603E, p.accent());
    }

    @Test
    void darkIsGraphite() {
        GuiPalette p = GuiPalette.of(GuiTheme.DARK);
        assertEquals(0xFF26232B, p.panel());
        assertEquals(0xFF312D37, p.bevelLight());
        assertEquals(0xFFECE5D6, p.fg());
        assertEquals(0xFFF26A48, p.accent());
    }

    @Test
    void otherAlternates() {
        assertSame(GuiTheme.DARK, GuiTheme.LIGHT.other());
        assertSame(GuiTheme.LIGHT, GuiTheme.DARK.other());
    }

    @Test
    void contrastOfBlackOnWhiteIs21() {
        assertEquals(21.0, GuiPalette.contrast(0xFF000000, 0xFFFFFFFF), 0.01);
        assertEquals(1.0, GuiPalette.contrast(0xFF777777, 0xFF777777), 0.01);
    }

    @ParameterizedTest
    @EnumSource(GuiTheme.class)
    void everyColorIsOpaque(GuiTheme theme) {
        GuiPalette p = GuiPalette.of(theme);
        int[] all = {p.panel(), p.bevelLight(), p.inset(), p.bevelDark(), p.rowHover(), p.fg(), p.muted(),
                p.disabled(), p.tabIdle(), p.accent(), p.accentDeep(), p.ok(), p.warn(), p.danger(), p.paused(),
                p.brass(), p.ruleText(), p.tooltipMuted(), p.tooltipAccent(), p.darkText(), p.ring()};
        for (int color : all) {
            assertEquals(0xFF, color >>> 24, Integer.toHexString(color));
        }
    }

    @ParameterizedTest
    @EnumSource(GuiTheme.class)
    void textReadsOnEveryBackground(GuiTheme theme) {
        GuiPalette p = GuiPalette.of(theme);
        assertTrue(GuiPalette.contrast(p.fg(), p.panel()) >= 7, "texto no painel");
        assertTrue(GuiPalette.contrast(p.fg(), p.bevelLight()) >= 7, "texto no botão");
        assertTrue(GuiPalette.contrast(p.fg(), p.inset()) >= 7, "texto na área afundada");
        assertTrue(GuiPalette.contrast(p.muted(), p.panel()) >= 3.5, "texto secundário");
        assertTrue(GuiPalette.contrast(p.tabIdle(), p.panel()) >= 4.5, "aba inativa");
        assertTrue(GuiPalette.contrast(p.ruleText(), p.panel()) >= 4.5, "texto da regra");
        for (int state : new int[] {p.accent(), p.ok(), p.warn(), p.danger()}) {
            assertTrue(GuiPalette.contrast(state, p.panel()) >= 2.2, Integer.toHexString(state));
        }
    }

    @ParameterizedTest
    @EnumSource(GuiTheme.class)
    void textLevelsStayInOrder(GuiTheme theme) {
        GuiPalette p = GuiPalette.of(theme);
        double fg = GuiPalette.contrast(p.fg(), p.panel());
        double muted = GuiPalette.contrast(p.muted(), p.panel());
        double disabled = GuiPalette.contrast(p.disabled(), p.panel());
        assertTrue(fg > muted && muted > disabled && disabled > 1.5, fg + " > " + muted + " > " + disabled);
    }
}
