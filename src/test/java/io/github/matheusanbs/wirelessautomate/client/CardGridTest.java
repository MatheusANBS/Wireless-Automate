package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CardGridTest {
    @Test
    void columnsFollowTheWidth() {
        assertEquals(3, CardGrid.columns(5, 282, 86, 4));
        assertEquals(5, CardGrid.columns(5, 452, 86, 4));
        assertEquals(4, CardGrid.columns(5, 445, 86, 4));
    }

    @Test
    void neverMoreColumnsThanCards() {
        assertEquals(4, CardGrid.columns(4, 1000, 86, 4));
    }

    @Test
    void atLeastOneColumn() {
        assertEquals(1, CardGrid.columns(5, 40, 86, 4));
        assertEquals(1, CardGrid.columns(0, 300, 86, 4));
    }
}
