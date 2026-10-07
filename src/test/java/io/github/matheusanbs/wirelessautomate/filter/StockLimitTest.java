package io.github.matheusanbs.wirelessautomate.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StockLimitTest {
    @Test
    void withoutStockEverythingGoes() {
        assertEquals(64, StockLimit.extractable(10, 0, 64));
        assertEquals(64, StockLimit.acceptable(1000, 0, 64));
        assertEquals(0, StockLimit.extractable(10, 0, -5));
    }

    @Test
    void sourceKeepsStock() {
        assertEquals(6, StockLimit.extractable(10, 4, 64));
        assertEquals(3, StockLimit.extractable(10, 4, 3));
        assertEquals(0, StockLimit.extractable(4, 4, 64));
        assertEquals(0, StockLimit.extractable(2, 4, 64));
    }

    @Test
    void destinationAcceptsUpToStock() {
        assertEquals(7, StockLimit.acceptable(0, 7, 64));
        assertEquals(2, StockLimit.acceptable(5, 7, 64));
        assertEquals(1, StockLimit.acceptable(5, 7, 1));
        assertEquals(0, StockLimit.acceptable(7, 7, 64));
        assertEquals(0, StockLimit.acceptable(9, 7, 64));
    }

    @Test
    void hugeStockDoesNotOverflow() {
        assertEquals(0, StockLimit.extractable(Long.MAX_VALUE - 1, Long.MAX_VALUE, 64));
        assertEquals(64, StockLimit.acceptable(0, Long.MAX_VALUE, 64));
    }
}
