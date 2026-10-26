package io.github.matheusanbs.wirelessautomate.client.router;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NetworkSearchTest {
    @Test
    void foldKeepsTheLengthAndDropsAccentsAndCase() {
        assertEquals("quimica", NetworkSearch.fold("Química"));
        assertEquals("fabrica do joao", NetworkSearch.fold("Fábrica do João"));
        assertEquals("Química".length(), NetworkSearch.fold("Química").length());
    }

    @Test
    void findIsBySubstringIgnoringAccentsAndCase() {
        assertEquals(0, NetworkSearch.find("Mineração", NetworkSearch.fold("mi")));
        assertEquals(2, NetworkSearch.find("Química", NetworkSearch.fold("IMI")));
        assertEquals(2, NetworkSearch.find("Química", NetworkSearch.fold("í")));
        assertEquals(-1, NetworkSearch.find("Reator", NetworkSearch.fold("mi")));
    }

    @Test
    void anEmptyQueryMatchesEverythingAtZero() {
        assertEquals(0, NetworkSearch.find("Qualquer", ""));
    }
}
