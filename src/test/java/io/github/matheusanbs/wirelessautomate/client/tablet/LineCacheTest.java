package io.github.matheusanbs.wirelessautomate.client.tablet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LineCacheTest {
    private final Object page = new Object();
    private final Object tree = new Object();

    private LineCache<String> filled() {
        LineCache<String> cache = new LineCache<>();
        cache.key(page, tree, "item", 0, 3);
        cache.put(0, 100, "a");
        cache.put(2, 100, "c");
        return cache;
    }

    @Test
    void mesmaChaveEMesmaLarguraGuardam() {
        LineCache<String> cache = filled();
        cache.key(page, tree, "item", 0, 3);
        assertEquals("a", cache.get(0, 100));
        assertEquals("c", cache.get(2, 100));
        assertNull(cache.get(1, 100));
    }

    @Test
    void outraLarguraRefazSoAquelaLinha() {
        LineCache<String> cache = filled();
        assertNull(cache.get(0, 90));
        cache.put(0, 90, "a2");
        assertEquals("a2", cache.get(0, 90));
        assertEquals("c", cache.get(2, 100));
    }

    @Test
    void outraPaginaEsqueceTudo() {
        LineCache<String> cache = filled();
        cache.key(new Object(), tree, "item", 0, 3);
        assertNull(cache.get(0, 100));
        assertNull(cache.get(2, 100));
    }

    @Test
    void outraArvoreTipoTemaOuTamanhoEsquecem() {
        LineCache<String> cache = filled();
        cache.key(page, new Object(), "item", 0, 3);
        assertNull(cache.get(0, 100));

        cache = filled();
        cache.key(page, tree, "fluid", 0, 3);
        assertNull(cache.get(0, 100));

        cache = filled();
        cache.key(page, tree, "item", 1, 3);
        assertNull(cache.get(0, 100));

        cache = filled();
        cache.key(page, tree, "item", 0, 4);
        assertNull(cache.get(0, 100));
        cache.put(3, 100, "d");
        assertEquals("d", cache.get(3, 100));
    }

    @Test
    void foraDaFaixaNaoQuebra() {
        LineCache<String> cache = filled();
        cache.put(7, 100, "x");
        assertNull(cache.get(7, 100));
        assertNull(cache.get(-1, 100));
    }
}
