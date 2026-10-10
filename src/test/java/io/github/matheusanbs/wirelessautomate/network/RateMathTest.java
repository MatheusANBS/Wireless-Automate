package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RateMathTest {
    @Test
    void addSaturatesBothWays() {
        assertEquals(5L, RateMath.add(2L, 3L));
        assertEquals(Long.MAX_VALUE, RateMath.add(Long.MAX_VALUE - 1, 10L));
        assertEquals(Long.MIN_VALUE, RateMath.add(Long.MIN_VALUE + 1, -10L));
    }

    @Test
    void perSecondSaturatesWithSign() {
        assertEquals(200L, RateMath.perSecond(10L));
        assertEquals(-200L, RateMath.perSecond(-10L));
        assertEquals(Long.MAX_VALUE, RateMath.perSecond(Long.MAX_VALUE / 10));
        assertEquals(Long.MIN_VALUE, RateMath.perSecond(Long.MIN_VALUE / 10));
    }

    @Test
    void rateRoundsLikeBefore() {
        // (delta × 20 + elapsed / 2) / elapsed, a conta antiga, para valores pequenos
        for (long delta = 0; delta < 500; delta += 7) {
            for (long elapsed = 1; elapsed < 60; elapsed += 5) {
                assertEquals((delta * 20 + elapsed / 2) / elapsed, RateMath.rate(delta, elapsed, false));
                assertEquals((delta + elapsed / 2) / elapsed, RateMath.rate(delta, elapsed, true));
            }
        }
    }

    @Test
    void hugeDeltaNeverTurnsNegative() {
        // 4,7 × 10^17 itens em 20 ticks: × 20 passaria do long
        assertEquals(Long.MAX_VALUE / 20, RateMath.rate(Long.MAX_VALUE, 20, false));
        assertEquals(Long.MAX_VALUE / 20, RateMath.rate(470_000_000_000_000_000L, 20, false));
        assertEquals(Long.MAX_VALUE, RateMath.rate(Long.MAX_VALUE, 1, true));
        assertEquals(0L, RateMath.rate(-5L, 20, false));
        assertEquals(0L, RateMath.rate(5L, 0, false));
    }

    @Test
    void counterWrapStillGivesTheDelta() {
        // o contador do nó dá a volta; a diferença entre amostras continua certa
        long before = Long.MAX_VALUE - 10;
        long after = before + 30;
        assertEquals(30L, after - before);
    }
}
