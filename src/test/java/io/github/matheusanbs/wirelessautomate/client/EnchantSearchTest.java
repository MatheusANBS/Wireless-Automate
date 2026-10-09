package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

class EnchantSearchTest {
    private record E(String name, String id) {}

    private static final List<E> ALL = List.of(
            new E("Proteção", "minecraft:protection"),
            new E("Afiação", "minecraft:sharpness"),
            new E("Fortuna", "minecraft:fortune"),
            new E("Proteção contra fogo", "minecraft:fire_protection"),
            new E("Eficiência", "minecraft:efficiency"),
            new E("Remendo", "minecraft:mending"));

    private static List<String> names(String query) {
        return EnchantSearch.search(ALL, E::name, E::id, query).stream().map(E::name).toList();
    }

    @Test
    void ignoraAcentosEMaiusculas() {
        assertEquals(List.of("Proteção", "Proteção contra fogo"), names("PROTECAO"));
        assertEquals(List.of("Eficiência"), names("eficiencia"));
    }

    @Test
    void achaPeloId() {
        assertEquals(List.of("Afiação"), names("minecraft:sh"));
        assertEquals(List.of("Afiação"), names("sharp"));
    }

    @Test
    void comecaComAntesDeContem() {
        // "prot" começa "Proteção" e "Proteção contra fogo"; "fire_protection" só contém (e começa com "fire")
        assertEquals(List.of("Proteção", "Proteção contra fogo"), names("prot"));
        // "fo": começa "Fortuna"; "Proteção contra fogo" só contém
        assertEquals(List.of("Fortuna", "Proteção contra fogo"), names("fo"));
    }

    @Test
    void vazioDevolveTodosEmOrdemAlfabetica() {
        assertEquals(List.of("Afiação", "Eficiência", "Fortuna", "Proteção", "Proteção contra fogo", "Remendo"),
                names(""));
        assertEquals(names(""), names("   "));
    }

    @Test
    void semResultado() {
        assertTrue(names("xyz").isEmpty());
    }

    @Test
    void parseDoNivel() {
        assertEquals(OptionalInt.empty(), EnchantSearch.parseLevel("0"));
        assertEquals(OptionalInt.empty(), EnchantSearch.parseLevel("256"));
        assertEquals(OptionalInt.empty(), EnchantSearch.parseLevel(""));
        assertEquals(OptionalInt.empty(), EnchantSearch.parseLevel("12a"));
        assertEquals(OptionalInt.empty(), EnchantSearch.parseLevel("-1"));
        assertEquals(OptionalInt.of(255), EnchantSearch.parseLevel("255"));
        assertEquals(OptionalInt.of(1), EnchantSearch.parseLevel("1"));
        assertEquals(OptionalInt.of(37), EnchantSearch.parseLevel("037"));
    }

    @Test
    void passoDoNivel() {
        assertEquals(2, EnchantSearch.stepLevel(1, 1));
        assertEquals(1, EnchantSearch.stepLevel(1, -1));
        assertEquals(255, EnchantSearch.stepLevel(250, 10));
        assertEquals(1, EnchantSearch.stepLevel(5, -10));
    }
}
