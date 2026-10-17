package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlowWindowTest {
    @Test
    void startsEmpty() {
        FlowWindow window = new FlowWindow();
        assertEquals(0, window.seconds());
        assertEquals(0L, window.total());
        assertEquals(0.0, window.perSecond());
        assertFalse(window.filled());
    }

    @Test
    void openBucketIsNotCounted() {
        FlowWindow window = new FlowWindow();
        window.add(100);
        assertEquals(0L, window.total());
        assertEquals(0.0, window.perSecond());
        window.roll();
        assertEquals(100L, window.total());
        assertEquals(100.0, window.perSecond());
    }

    @Test
    void averagesClosedBucketsWhileFilling() {
        FlowWindow window = new FlowWindow();
        window.add(10);
        window.roll();
        window.add(30);
        window.roll();
        assertEquals(2, window.seconds());
        assertEquals(40L, window.total());
        assertEquals(20.0, window.perSecond());
        assertFalse(window.filled());
    }

    @Test
    void fillsAfterAllBuckets() {
        FlowWindow window = new FlowWindow();
        for (int i = 1; i < FlowWindow.SECONDS; i++) {
            window.roll();
            assertFalse(window.filled());
        }
        window.roll();
        assertTrue(window.filled());
        assertEquals(FlowWindow.SECONDS, window.seconds());
    }

    @Test
    void oldestBucketLeavesWhenFull() {
        FlowWindow window = new FlowWindow();
        for (int i = 1; i <= FlowWindow.SECONDS; i++) {
            window.add(i);
            window.roll();
        }
        assertEquals(15L, window.total());
        assertEquals(3.0, window.perSecond());
        window.add(100);
        window.roll();
        // Sai o 1, entra o 100: 2 + 3 + 4 + 5 + 100.
        assertEquals(114L, window.total());
        assertEquals(FlowWindow.SECONDS, window.seconds());
        window.roll();
        // Sai o 2, entra um balde vazio.
        assertEquals(112L, window.total());
    }

    @Test
    void zeroSecondsKeepTheAverageAtZero() {
        FlowWindow window = new FlowWindow();
        for (int i = 0; i < FlowWindow.SECONDS * 2; i++) {
            window.roll();
        }
        assertTrue(window.filled());
        assertEquals(0L, window.total());
        assertEquals(0.0, window.perSecond());
    }

    @Test
    void resetEmptiesEverything() {
        FlowWindow window = new FlowWindow();
        window.add(7);
        window.roll();
        window.add(9);
        window.reset();
        assertEquals(0, window.seconds());
        assertEquals(0L, window.total());
        window.roll();
        assertEquals(0L, window.total());
        assertEquals(1, window.seconds());
    }

    @Test
    void handlesLargeTotals() {
        FlowWindow window = new FlowWindow();
        long big = 30_000_000_000L;
        window.add(big);
        window.roll();
        window.add(big);
        window.roll();
        assertEquals(2 * big, window.total());
        assertEquals((double) big, window.perSecond());
    }
}
