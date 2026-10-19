package io.github.matheusanbs.wirelessautomate.client.highlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.highlight.MachineHighlights.Result;
import org.junit.jupiter.api.Test;

class MachineHighlightsTest {
    private static final String OVERWORLD = "overworld";
    private static final String NETHER = "nether";

    @Test
    void togglesOnAndOff() {
        MachineHighlights lit = new MachineHighlights();
        assertEquals(Result.LIT, lit.toggle(OVERWORLD, 5, 9));
        assertTrue(lit.isLit(OVERWORLD, 5));
        assertEquals(1, lit.count());
        assertEquals(9, lit.router(0));
        assertEquals(Result.OFF, lit.toggle(OVERWORLD, 5, 9));
        assertFalse(lit.isLit(OVERWORLD, 5));
        assertEquals(0, lit.count());
    }

    @Test
    void dimensionIsPartOfTheKey() {
        MachineHighlights lit = new MachineHighlights();
        lit.toggle(OVERWORLD, 5, 9);
        assertFalse(lit.isLit(NETHER, 5));
        assertEquals(Result.LIT, lit.toggle(NETHER, 5, 9));
        assertEquals(2, lit.count());
        assertEquals(NETHER, lit.dimension(1));
    }

    @Test
    void anotherRouterTurnsTheSameMachineOff() {
        MachineHighlights lit = new MachineHighlights();
        lit.toggle(OVERWORLD, 5, 9);
        assertEquals(Result.OFF, lit.toggle(OVERWORLD, 5, 77));
        assertEquals(0, lit.count());
    }

    @Test
    void theOldestGoesPastTheLimit() {
        MachineHighlights lit = new MachineHighlights();
        for (int i = 0; i < MachineHighlights.LIMIT; i++) {
            assertEquals(Result.LIT, lit.toggle(OVERWORLD, i, 1000 + i));
        }
        assertEquals(MachineHighlights.LIMIT, lit.count());
        assertEquals(Result.LIT_OLDEST_OFF, lit.toggle(OVERWORLD, 500, 1500));
        assertEquals(MachineHighlights.LIMIT, lit.count());
        assertFalse(lit.isLit(OVERWORLD, 0));
        assertTrue(lit.isLit(OVERWORLD, 1));
        assertTrue(lit.isLit(OVERWORLD, 500));
        assertEquals(1, lit.machine(0));
        assertEquals(500, lit.machine(MachineHighlights.LIMIT - 1));
        assertEquals(1500, lit.router(MachineHighlights.LIMIT - 1));
    }

    @Test
    void removingKeepsTheOrder() {
        MachineHighlights lit = new MachineHighlights();
        lit.toggle(OVERWORLD, 1, 0);
        lit.toggle(OVERWORLD, 2, 0);
        lit.toggle(OVERWORLD, 3, 0);
        lit.toggle(OVERWORLD, 2, 0);
        assertEquals(2, lit.count());
        assertEquals(1, lit.machine(0));
        assertEquals(3, lit.machine(1));
    }

    @Test
    void clearEmptiesAndBumpsTheVersion() {
        MachineHighlights lit = new MachineHighlights();
        lit.toggle(OVERWORLD, 1, 0);
        lit.toggle(NETHER, 2, 0);
        int version = lit.version();
        lit.clear();
        assertEquals(0, lit.count());
        assertFalse(lit.isLit(OVERWORLD, 1));
        assertNotEquals(version, lit.version());
        int empty = lit.version();
        lit.clear();
        assertEquals(empty, lit.version());
    }

    @Test
    void everyToggleBumpsTheVersion() {
        MachineHighlights lit = new MachineHighlights();
        int v0 = lit.version();
        lit.toggle(OVERWORLD, 1, 0);
        int v1 = lit.version();
        lit.toggle(OVERWORLD, 1, 0);
        assertNotEquals(v0, v1);
        assertNotEquals(v1, lit.version());
    }
}
