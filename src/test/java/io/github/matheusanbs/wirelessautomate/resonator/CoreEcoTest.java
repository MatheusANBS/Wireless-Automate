package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CoreEcoTest {
    @Test
    void fatorSobeCincoPorEco() {
        assertEquals(1.0, CoreEco.factor(0, 0.05, 0), 1e-12);
        assertEquals(1.05, CoreEco.factor(1, 0.05, 0), 1e-12);
        assertEquals(1.5, CoreEco.factor(10, 0.05, 0), 1e-12);
        assertEquals(1.0, CoreEco.factor(-3, 0.05, 0), 1e-12);
    }

    @Test
    void aplicaSobreABaseParaBaixo() {
        assertEquals(1_000, CoreEco.apply(1_000, 0, 0.05, 0));
        assertEquals(1_050, CoreEco.apply(1_000, 1, 0.05, 0));
        assertEquals(10, CoreEco.apply(10, 1, 0.05, 0));
        assertEquals(0, CoreEco.apply(0, 50, 0.05, 0));
        assertEquals(8_589_934_592L + 429_496_729L, CoreEco.apply(8_589_934_592L, 1, 0.05, 0));
        assertEquals(1_000, CoreEco.apply(1_000, 7, 0, 0));
    }

    @Test
    void tetoLimitaOEco() {
        assertEquals(3, CoreEco.effective(10, 3));
        assertEquals(10, CoreEco.effective(10, 0));
        assertEquals(1_150, CoreEco.apply(1_000, 10, 0.05, 3));
        assertEquals(2, CoreEco.room(1, 3, 64));
        assertEquals(0, CoreEco.room(3, 3, 1));
        assertEquals(0, CoreEco.room(5, 3, 1));
        assertEquals(64, CoreEco.room(1_000_000, 0, 64));
    }

    @Test
    void saturaEmLongMax() {
        assertEquals(Long.MAX_VALUE, CoreEco.apply(Long.MAX_VALUE / 2, 40, 0.05, 0));
        assertEquals(Long.MAX_VALUE, CoreEco.apply(1_000, Long.MAX_VALUE, 1.0, 0));
        assertEquals(Long.MAX_VALUE, CoreEco.add(Long.MAX_VALUE - 1, 5));
        assertEquals(6, CoreEco.add(1, 5));
        assertEquals(0, CoreEco.room(Long.MAX_VALUE, 0, 1));
    }
}
