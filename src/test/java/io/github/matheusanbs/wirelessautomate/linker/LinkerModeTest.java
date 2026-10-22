package io.github.matheusanbs.wirelessautomate.linker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LinkerModeTest {
    @Test
    void oldNamesBecomeLink() {
        assertEquals(LinkerMode.LINK, LinkerMode.byName("single"));
        assertEquals(LinkerMode.LINK, LinkerMode.byName("area"));
        assertEquals(LinkerMode.LINK, LinkerMode.byName("machines"));
        assertEquals(LinkerMode.LINK, LinkerMode.byName("link"));
        assertEquals(LinkerMode.LINK, LinkerMode.byName("qualquer coisa"));
    }

    @Test
    void openIsKept() {
        assertEquals(LinkerMode.OPEN, LinkerMode.byName("open"));
    }

    @Test
    void serializedNamesRoundTrip() {
        for (LinkerMode mode : LinkerMode.values()) {
            assertEquals(mode, LinkerMode.byName(mode.getSerializedName()));
        }
    }

    @Test
    void nextAlternates() {
        assertEquals(LinkerMode.OPEN, LinkerMode.LINK.next());
        assertEquals(LinkerMode.LINK, LinkerMode.OPEN.next());
    }

    @Test
    void onlyLinkMarksTheArea() {
        assertEquals(true, LinkerMode.LINK.marksArea());
        assertEquals(false, LinkerMode.OPEN.marksArea());
    }
}
