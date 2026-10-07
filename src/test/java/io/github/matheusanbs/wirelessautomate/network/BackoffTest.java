package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BackoffTest {
    @Test
    void startsAwake() {
        Backoff backoff = new Backoff(40);
        assertTrue(backoff.isAwake(0L));
        assertTrue(backoff.isAwake(Long.MIN_VALUE));
        assertFalse(backoff.isSleeping(0L));
        assertEquals(1, backoff.nextIntervalTicks());
    }

    @Test
    void firstSleepLastsOneTick() {
        Backoff backoff = new Backoff(40);
        backoff.sleep(100L);
        assertTrue(backoff.isSleeping(100L));
        assertTrue(backoff.isAwake(101L));
    }

    @Test
    void intervalDoublesUpToMax() {
        Backoff backoff = new Backoff(10);
        long now = 0L;
        int[] expected = {1, 2, 4, 8, 10, 10};
        for (int interval : expected) {
            assertEquals(interval, backoff.nextIntervalTicks());
            backoff.sleep(now);
            assertTrue(backoff.isSleeping(now + interval - 1));
            assertTrue(backoff.isAwake(now + interval));
            now += interval;
        }
    }

    @Test
    void wakeResetsInterval() {
        Backoff backoff = new Backoff(64);
        backoff.sleep(0L);
        backoff.sleep(1L);
        backoff.sleep(3L);
        assertTrue(backoff.isSleeping(5L));
        backoff.wake();
        assertTrue(backoff.isAwake(5L));
        assertEquals(1, backoff.nextIntervalTicks());
        backoff.sleep(5L);
        assertTrue(backoff.isAwake(6L));
    }

    @Test
    void maxBelowOneBecomesOne() {
        Backoff backoff = new Backoff(0);
        assertEquals(1, backoff.maxTicks());
        backoff.sleep(0L);
        backoff.sleep(1L);
        assertEquals(1, backoff.nextIntervalTicks());
        assertTrue(backoff.isAwake(2L));
        assertEquals(1, new Backoff(-5).maxTicks());
    }

    @Test
    void hugeMaxDoesNotOverflow() {
        Backoff backoff = new Backoff(Integer.MAX_VALUE);
        for (int i = 0; i < 40; i++) {
            backoff.sleep(0L);
        }
        assertEquals(Integer.MAX_VALUE, backoff.nextIntervalTicks());
    }

    @Test
    void sleepUntilWakesAtTheGivenTickAndKeepsTheInterval() {
        Backoff backoff = new Backoff(40);
        backoff.sleep(0L);
        backoff.sleep(1L);
        assertEquals(4, backoff.nextIntervalTicks());
        backoff.sleepUntil(10L, 25L);
        assertTrue(backoff.isSleeping(24L));
        assertTrue(backoff.isAwake(25L));
        assertEquals(25L, backoff.wakeAt());
        assertEquals(4, backoff.nextIntervalTicks());
    }

    @Test
    void sleepUntilInThePastStillSleepsOneTick() {
        Backoff backoff = new Backoff(40);
        backoff.sleepUntil(10L, 3L);
        assertTrue(backoff.isSleeping(10L));
        assertTrue(backoff.isAwake(11L));
    }

    @Test
    void earliestWakeOfSeveralDestinations() {
        Backoff a = new Backoff(40);
        Backoff b = new Backoff(40);
        Backoff c = new Backoff(40);
        a.sleepUntil(0L, 30L);
        b.sleepUntil(0L, 12L);
        c.sleepUntil(0L, 20L);
        long wake = Long.MAX_VALUE;
        for (Backoff backoff : new Backoff[] {a, b, c}) {
            wake = backoff.earliestWake(wake);
        }
        assertEquals(12L, wake);
        // Um acordado puxa para o passado: a origem não tem por que esperar.
        c.wake();
        assertEquals(Long.MIN_VALUE, c.earliestWake(wake));
    }
}
