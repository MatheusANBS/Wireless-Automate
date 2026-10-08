package io.github.matheusanbs.wirelessautomate.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScalarStoreTest {
    private int notifications;

    private ScalarStore store(long capacity) {
        notifications = 0;
        return new ScalarStore(() -> notifications++, () -> capacity);
    }

    @Test
    void replaceLimitaACapacidade() {
        ScalarStore s = store(1_000);
        s.replace(5_000);
        assertEquals(1_000, s.stored());
    }

    @Test
    void replaceLimitaEmZero() {
        ScalarStore s = store(1_000);
        s.replace(500);
        s.replace(-10);
        assertEquals(0, s.stored());
    }

    @Test
    void replaceIgnoraValorIgual() {
        ScalarStore s = store(1_000);
        s.replace(500);
        int version = s.version();
        s.replace(500);
        assertEquals(1, notifications);
        assertEquals(version, s.version());
    }

    @Test
    void replaceAvisaUmaVezNaMudancaReal() {
        ScalarStore s = store(1_000);
        s.replace(300);
        assertEquals(1, notifications);
        assertEquals(300, s.stored());
        assertEquals(1, s.version());
    }

    @Test
    void capacidadeSemLimiteAceitaValoresGrandes() {
        ScalarStore s = store(0);
        s.replace(9_000_000_000L);
        assertEquals(9_000_000_000L, s.stored());
        ScalarStore negativa = store(-1);
        negativa.replace(9_000_000_000L);
        assertEquals(9_000_000_000L, negativa.stored());
    }
}
