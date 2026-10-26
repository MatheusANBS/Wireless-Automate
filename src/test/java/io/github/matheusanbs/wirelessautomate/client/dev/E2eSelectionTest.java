package io.github.matheusanbs.wirelessautomate.client.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
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

    private static final List<String> SUITES = List.of("a", "b", "c", "d");
    private static final Set<String> OPTIONAL = Set.of("d");

    @Test
    void suitesPadraoPegaAsNaoOpcionais() {
        assertEquals(List.of("a", "b", "c"), E2eSelection.selectSuites(null, null, false, SUITES, OPTIONAL));
        assertEquals(List.of("a", "b", "c"), E2eSelection.selectSuites("todas", "", false, SUITES, OPTIONAL));
    }

    @Test
    void suitesNaOrdemDoRegistro() {
        assertEquals(List.of("a", "b"), E2eSelection.selectSuites("b,a", null, false, SUITES, OPTIONAL));
        assertEquals(List.of("a", "d"), E2eSelection.selectSuites("d, a", null, false, SUITES, OPTIONAL));
    }

    @Test
    void suitesPerfilCompletoIncluiOpcionaisESkipTira() {
        assertEquals(List.of("a", "b", "c", "d"), E2eSelection.selectSuites(null, null, true, SUITES, OPTIONAL));
        assertEquals(List.of("a", "c", "d"), E2eSelection.selectSuites(null, "b", true, SUITES, OPTIONAL));
        assertEquals(List.of("a", "c"), E2eSelection.selectSuites("a,b,c", "b", false, SUITES, OPTIONAL));
    }

    @Test
    void suiteDesconhecidaListaAsValidas() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> E2eSelection.selectSuites("a,x", null, false, SUITES, OPTIONAL));
        assertTrue(e.getMessage().contains("x") && e.getMessage().contains("a, b, c, d"));
        assertThrows(IllegalArgumentException.class,
                () -> E2eSelection.selectSuites(null, "x", false, SUITES, OPTIONAL));
    }

    @Test
    void listaSoMostra() {
        assertTrue(E2eSelection.listOnly("lista"));
        assertFalse(E2eSelection.listOnly("a,b"));
        assertFalse(E2eSelection.listOnly(null));
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
