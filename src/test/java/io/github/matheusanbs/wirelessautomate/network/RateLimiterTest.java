package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimiterTest {
    @Test
    void zeroRateIsUnlimited() {
        RateLimiter limiter = new RateLimiter();
        assertTrue(limiter.isUnlimited());
        assertEquals(Long.MAX_VALUE, limiter.available(0L));
        limiter.consume(1_000_000L);
        assertEquals(Long.MAX_VALUE, limiter.available(1L));
    }

    @Test
    void startsFullAndFirstCallOnlyMarksTime() {
        RateLimiter limiter = new RateLimiter(100L);
        assertEquals(100L, limiter.available(5_000L));
        limiter.consume(100L);
        assertEquals(0L, limiter.available(5_000L));
    }

    @Test
    void refillsPerTick() {
        RateLimiter limiter = new RateLimiter(200L);
        limiter.available(0L);
        limiter.consume(200L);
        assertEquals(10L, limiter.available(1L));
        assertEquals(30L, limiter.available(3L));
    }

    @Test
    void accumulatesFractionalRefill() {
        RateLimiter limiter = new RateLimiter(30L);
        limiter.available(0L);
        limiter.consume(30L);
        assertEquals(1L, limiter.available(1L));
        assertEquals(3L, limiter.available(2L));
        assertEquals(4L, limiter.available(3L));
        assertEquals(6L, limiter.available(4L));
    }

    @Test
    void fractionSurvivesConsumption() {
        RateLimiter limiter = new RateLimiter(1L);
        limiter.available(0L);
        limiter.consume(1L);
        for (long t = 1; t < 20; t++) {
            assertEquals(0L, limiter.available(t));
        }
        assertEquals(1L, limiter.available(20L));
    }

    @Test
    void capsAtOneSecondOfRate() {
        RateLimiter limiter = new RateLimiter(30L);
        limiter.available(0L);
        limiter.consume(10L);
        assertEquals(30L, limiter.available(1_000L));
        assertEquals(30L, limiter.available(1_001L));
    }

    @Test
    void consumeNeverGoesNegative() {
        RateLimiter limiter = new RateLimiter(40L);
        limiter.available(0L);
        limiter.consume(1_000L);
        assertEquals(0L, limiter.available(0L));
        assertEquals(2L, limiter.available(1L));
        assertThrows(IllegalArgumentException.class, () -> limiter.consume(-1L));
    }

    @Test
    void timeGoingBackDoesNotRefillOrBreak() {
        RateLimiter limiter = new RateLimiter(20L);
        limiter.available(100L);
        limiter.consume(20L);
        assertEquals(0L, limiter.available(50L));
        assertEquals(2L, limiter.available(52L));
    }

    @Test
    void saturatesWithHugeRates() {
        RateLimiter limiter = new RateLimiter(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, limiter.available(0L));
        limiter.consume(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE / 20, limiter.available(1L));
        assertEquals(Long.MAX_VALUE, limiter.available(Long.MAX_VALUE));

        limiter.consume(1L);
        assertEquals(Long.MAX_VALUE - 1, limiter.available(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE - 1, limiter.available(Long.MIN_VALUE));
    }

    @Test
    void saturatesFromAnyStartingTime() {
        RateLimiter limiter = new RateLimiter(Long.MAX_VALUE - 7);
        limiter.available(Long.MIN_VALUE);
        limiter.consume(5L);
        assertEquals(Long.MAX_VALUE - 7, limiter.available(Long.MAX_VALUE));
    }

    @Test
    void lowerRateCutsExcess() {
        RateLimiter limiter = new RateLimiter(100L);
        limiter.available(0L);
        limiter.setRatePerSecond(40L);
        assertEquals(40L, limiter.available(0L));
        limiter.consume(40L);
        assertEquals(2L, limiter.available(1L));
    }

    @Test
    void higherRateKeepsBalanceAndRaisesCap() {
        RateLimiter limiter = new RateLimiter(20L);
        limiter.available(0L);
        limiter.setRatePerSecond(200L);
        assertEquals(20L, limiter.available(0L));
        assertEquals(200L, limiter.available(100L));
    }

    @Test
    void leavingUnlimitedStartsFull() {
        RateLimiter limiter = new RateLimiter();
        limiter.available(0L);
        limiter.setRatePerSecond(60L);
        assertEquals(60L, limiter.available(1L));
        limiter.setRatePerSecond(0L);
        assertEquals(Long.MAX_VALUE, limiter.available(2L));
    }

    @Test
    void rejectsNegativeRate() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(-1L));
    }

    @Test
    void energyRateIsPerTickTimesTwenty() {
        assertEquals(2_000L, RateLimiter.perSecondFromPerTick(100L));
        assertEquals(Long.MAX_VALUE, RateLimiter.perSecondFromPerTick(Long.MAX_VALUE / 10));

        RateLimiter limiter = new RateLimiter(RateLimiter.perSecondFromPerTick(100L));
        limiter.available(0L);
        limiter.consume(Long.MAX_VALUE);
        assertEquals(100L, limiter.available(1L));
    }
}
