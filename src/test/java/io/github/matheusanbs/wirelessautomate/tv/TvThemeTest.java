package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvThemeTest {
    @Test
    void trocarUmaCorMudaSoEla() {
        for (int role = 1; role <= 6; role++) {
            TvTheme changed = TvTheme.GRAPHITE.withColor(role, 0xFF123456);
            assertEquals(0xFF123456, changed.color(role));
            for (int other = 1; other <= 6; other++) {
                if (other != role) {
                    assertEquals(TvTheme.GRAPHITE.color(other), changed.color(other));
                }
            }
            assertEquals(TvTheme.GRAPHITE.name(), changed.name());
        }
    }

    @Test
    void amesmaPaletaIgnoraONome() {
        TvTheme renamed = new TvTheme("outro", TvTheme.AMBER.bg(), TvTheme.AMBER.card(), TvTheme.AMBER.text(),
                TvTheme.AMBER.muted(), TvTheme.AMBER.high(), TvTheme.AMBER.low());
        assertTrue(renamed.sameColors(TvTheme.AMBER));
        assertFalse(TvTheme.AMBER.sameColors(TvTheme.PHOSPHOR));
        assertFalse(TvTheme.AMBER.withColor(TvColor.TEXT, 0xFF000000).sameColors(TvTheme.AMBER));
    }

    @Test
    void osQuatroProntosTemPaletasDiferentes() {
        for (int i = 0; i < TvTheme.PRESETS.size(); i++) {
            for (int j = i + 1; j < TvTheme.PRESETS.size(); j++) {
                assertFalse(TvTheme.PRESETS.get(i).sameColors(TvTheme.PRESETS.get(j)));
            }
        }
    }
}
