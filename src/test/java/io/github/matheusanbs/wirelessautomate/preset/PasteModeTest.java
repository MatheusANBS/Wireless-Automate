package io.github.matheusanbs.wirelessautomate.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * O JUnit roda sem as classes do Minecraft: o teste fica no núcleo por booleanos e não toca no
 * {@code LinkerMode} (que carrega o {@code StringRepresentable}); a tradução para ele é coberta pelos
 * GameTests do Configurador.
 */
class PasteModeTest {
    @Test
    void ofIgnoraQualquerMaquinaNoPincel() {
        assertEquals(PasteMode.BRUSH, PasteMode.of(false, false));
        assertEquals(PasteMode.BRUSH, PasteMode.of(false, true));
    }

    @Test
    void ofNaArea() {
        assertEquals(PasteMode.AREA_SAME, PasteMode.of(true, false));
        assertEquals(PasteMode.AREA_ANY, PasteMode.of(true, true));
    }

    @Test
    void nextDaAVolta() {
        assertEquals(PasteMode.AREA_SAME, PasteMode.BRUSH.next());
        assertEquals(PasteMode.AREA_ANY, PasteMode.AREA_SAME.next());
        assertEquals(PasteMode.BRUSH, PasteMode.AREA_ANY.next());
        for (PasteMode mode : PasteMode.values()) {
            assertEquals(mode, mode.next().next().next());
        }
    }

    @Test
    void idaEVolta() {
        for (PasteMode mode : PasteMode.values()) {
            assertEquals(mode, PasteMode.of(mode.area(), mode.anyMachine()));
        }
    }

    @Test
    void camposDeCadaModo() {
        assertFalse(PasteMode.BRUSH.area());
        assertTrue(PasteMode.AREA_SAME.area());
        assertTrue(PasteMode.AREA_ANY.area());
        assertFalse(PasteMode.BRUSH.anyMachine());
        assertFalse(PasteMode.AREA_SAME.anyMachine());
        assertTrue(PasteMode.AREA_ANY.anyMachine());
        assertEquals("brush", PasteMode.BRUSH.key());
        assertEquals("area_same", PasteMode.AREA_SAME.key());
        assertEquals("area_any", PasteMode.AREA_ANY.key());
    }
}
