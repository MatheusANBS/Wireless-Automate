package io.github.matheusanbs.wirelessautomate.client.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FaceMemoryTest {
    @Test
    void lembraAFaceDeCadaAlvoDoMesmoRoteador() {
        FaceMemory<String, String> memory = new FaceMemory<>(8);
        memory.put("roteador", "presa", "norte");
        memory.put("roteador", "maquina 2", "cima");
        assertEquals("norte", memory.get("roteador", "presa"));
        assertEquals("cima", memory.get("roteador", "maquina 2"));
        assertNull(memory.get("roteador", "grupo 1"));
        assertNull(memory.get("outro", "presa"));
    }

    @Test
    void esqueceOsMenosUsadosAoPassarDoLimite() {
        FaceMemory<Integer, String> memory = new FaceMemory<>(2);
        memory.put(1, "a", "x");
        memory.put(2, "a", "x");
        memory.get(1, "a");
        memory.put(3, "a", "x");
        assertEquals("x", memory.get(1, "a"));
        assertNull(memory.get(2, "a"));
    }
}
