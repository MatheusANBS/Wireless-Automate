package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvColorTest {
    @Test
    void asSeisIdaEVolta() {
        for (int role = 1; role <= 6; role++) {
            int stored = TvColor.theme(role);
            assertEquals(role, stored);
            assertTrue(TvColor.isTheme(stored));
            assertEquals(role, TvColor.role(stored));
        }
        assertEquals(0xFF0F1117, TvColor.resolve(TvColor.theme(TvColor.BG), TvTheme.GRAPHITE));
        assertEquals(0xFFF26A48, TvColor.resolve(TvColor.theme(TvColor.LOW), TvTheme.GRAPHITE));
        assertEquals(0xFF3DB56E, TvColor.resolve(TvColor.HIGH, TvTheme.GRAPHITE));
        assertEquals(0xFFE4DAC6, TvColor.resolve(TvColor.CARD, TvTheme.PORCELAIN));
    }

    @Test
    void corFixaPassaIgual() {
        assertFalse(TvColor.isTheme(0xFFECE5D6));
        assertEquals(0xFFECE5D6, TvColor.resolve(0xFFECE5D6, TvTheme.AMBER));
        assertEquals(0x80445566, TvColor.resolve(0x80445566, TvTheme.AMBER));
        assertEquals(0, TvColor.role(0xFFECE5D6));
    }

    @Test
    void zeroETransparenteENaoTema() {
        assertFalse(TvColor.isTheme(0));
        assertFalse(TvColor.isTheme(7));
        assertEquals(0, TvColor.resolve(0, TvTheme.PHOSPHOR));
    }

    @Test
    void nomeDoTemaEhCortadoEm24() {
        assertEquals(24, new TvTheme("x".repeat(40), 0, 0, 0, 0, 0, 0).name().length());
    }

    @Test
    void campoHexasIdaEVolta() {
        assertEquals("#4FD1C5", TvColor.hex(0xFF4FD1C5));
        assertEquals("#4FD1C580", TvColor.hex(0x804FD1C5));
        assertEquals("", TvColor.hex(TvColor.theme(TvColor.HIGH)), "o papel do tema não tem texto");
        assertEquals("", TvColor.hex(0));
        assertEquals(0xFF4FD1C5, TvColor.fromHex("#4FD1C5"));
        assertEquals(0xFF4FD1C5, TvColor.fromHex("4fd1c5"), "o # é opcional e as letras podem ser minúsculas");
        assertEquals(0x804FD1C5, TvColor.fromHex("#4FD1C580"));
    }

    @Test
    void textoQueNaoEhCorNaoLe() {
        assertEquals(null, TvColor.fromHex(""));
        assertEquals(null, TvColor.fromHex("#4FD1C"));
        assertEquals(null, TvColor.fromHex("#GGGGGG"));
        assertEquals(null, TvColor.fromHex("#4FD1C5800"));
        assertEquals(0, TvColor.fromHex("#4FD1C500"), "alfa zero é a cor transparente");
    }
}
