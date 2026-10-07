package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReachBoxTest {
    @Test
    void emptyBoxIsAlwaysWithin() {
        ReachBox box = new ReachBox();
        assertTrue(box.isEmpty());
        assertEquals(0, box.maxDistanceSqr(100, 5, -7));
        assertTrue(box.allWithin(100, 5, -7, 1));
    }

    @Test
    void farthestCornerDecides() {
        ReachBox box = new ReachBox();
        box.include(0, 0, 0);
        box.include(10, 2, 4);
        // De (0,0,0) o canto mais distante é (10,2,4): 100 + 4 + 16.
        assertEquals(120, box.maxDistanceSqr(0, 0, 0));
        // De dentro da caixa, cada eixo pega o lado mais longe: (5,1,2) -> 5² + 1² + 2².
        assertEquals(30, box.maxDistanceSqr(5, 1, 2));
        assertTrue(box.allWithin(0, 0, 0, 11));
        assertFalse(box.allWithin(0, 0, 0, 10));
    }

    @Test
    void rangeZeroOrNegativeIsUnlimited() {
        ReachBox box = new ReachBox();
        box.include(-30_000_000, -64, -30_000_000);
        box.include(30_000_000, 320, 30_000_000);
        assertTrue(box.allWithin(0, 0, 0, 0));
        assertTrue(box.allWithin(0, 0, 0, -1));
        // Sem estouro de long nas bordas do mundo nem com o maior alcance.
        assertFalse(box.allWithin(0, 0, 0, 40_000_000));
        assertTrue(box.allWithin(0, 0, 0, Integer.MAX_VALUE));
    }

    @Test
    void clearForgetsPoints() {
        ReachBox box = new ReachBox();
        box.include(1000, 0, 0);
        box.clear();
        assertTrue(box.isEmpty());
        box.include(1, 0, 0);
        assertEquals(1, box.maxDistanceSqr(0, 0, 0));
    }
}
