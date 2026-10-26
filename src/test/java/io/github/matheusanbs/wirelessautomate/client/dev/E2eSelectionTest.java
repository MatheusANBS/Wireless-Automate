package io.github.matheusanbs.wirelessautomate.client.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class E2eSelectionTest {
    @Test
    void parseListTiraEspacosEVazios() {
        assertEquals(List.of("a", "b", "c"), E2eSelection.parseList(" a, ,B ,c"));
        assertEquals(List.of(), E2eSelection.parseList(null));
    }

    @Test
    void capturaPorModoEPrefixo() {
        assertFalse(E2eSelection.captureWanted("off", "x"));
        assertTrue(E2eSelection.captureWanted("todas", "x"));
        assertTrue(E2eSelection.captureWanted("reparador-tela,ressonador-camara", "reparador-tela-escuro"));
        assertFalse(E2eSelection.captureWanted("reparador-tela", "ressonador-camara"));
    }

    @Test
    void variantes() {
        assertFalse(E2eSelection.variantOn("off", "pt"));
        assertTrue(E2eSelection.variantOn("todas", "escuro"));
        assertTrue(E2eSelection.variantOn("pt", "pt"));
        assertFalse(E2eSelection.variantOn("pt", "escuro"));
    }

    @Test
    void perfis() {
        E2eSelection.Profile fast = E2eSelection.profile("rapido");
        assertEquals("off", fast.capture());
        assertEquals("off", fast.variants());
        assertFalse(fast.optional());
        E2eSelection.Profile full = E2eSelection.profile("completo");
        assertEquals("todas", full.capture());
        assertEquals("todas", full.variants());
        assertTrue(full.optional());
        assertEquals(fast, E2eSelection.profile("qualquer"));
    }
}
