package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TvGraphHistoryTest {
    @Test
    void emptyHasNoSamples() {
        TvGraphHistory h = new TvGraphHistory();
        assertEquals(0, h.size());
        assertTrue(Double.isNaN(h.ago(0)));
        assertTrue(Double.isNaN(h.min(60)));
        assertArrayEquals(new double[] {0, 1}, h.scale(60, 0, 0));
    }

    @Test
    void agoCountsFromNewest() {
        TvGraphHistory h = new TvGraphHistory();
        h.push(1);
        h.push(2);
        h.push(3);
        assertEquals(3, h.ago(0));
        assertEquals(2, h.ago(1));
        assertEquals(1, h.ago(2));
        assertTrue(Double.isNaN(h.ago(3)));
    }

    @Test
    void wrapsAtCapacityKeepingTheNewest() {
        TvGraphHistory h = new TvGraphHistory();
        for (int i = 0; i < TvGraphHistory.CAPACITY + 50; i++) {
            h.push(i);
        }
        assertEquals(TvGraphHistory.CAPACITY, h.size());
        assertEquals(TvGraphHistory.CAPACITY + 49, h.ago(0));
        assertEquals(50, h.ago(TvGraphHistory.CAPACITY - 1));
    }

    @Test
    void windowLimitsMinAndMax() {
        TvGraphHistory h = new TvGraphHistory();
        h.push(100);
        h.push(5);
        h.push(9);
        assertEquals(5, h.min(2));
        assertEquals(9, h.max(2));
        assertEquals(100, h.max(3));
    }

    @Test
    void nanIsSkippedByTheScale() {
        TvGraphHistory h = new TvGraphHistory();
        h.push(4);
        h.push(Double.NaN);
        h.push(8);
        assertArrayEquals(new double[] {4, 8}, h.scale(60, 0, 0));
    }

    @Test
    void flatSeriesOpensTheScale() {
        TvGraphHistory h = new TvGraphHistory();
        h.push(7);
        h.push(7);
        assertArrayEquals(new double[] {6, 8}, h.scale(60, 0, 0));
    }

    @Test
    void fixedRangeWins() {
        TvGraphHistory h = new TvGraphHistory();
        h.push(7);
        assertArrayEquals(new double[] {0, 100}, h.scale(60, 0, 100));
    }

    @Test
    void secondsOfTheWindowAreCapped() {
        assertEquals(60, TvGraphHistory.seconds(1));
        assertEquals(600, TvGraphHistory.seconds(10));
        assertEquals(600, TvGraphHistory.seconds(99));
    }
}
