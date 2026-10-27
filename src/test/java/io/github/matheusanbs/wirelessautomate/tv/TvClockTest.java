package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TvClockTest {
    @Test
    void oTempoZeroEAsSeisDaManha() {
        assertEquals(6 * 60, TvClock.minuteOfDay(0));
    }

    @Test
    void meioDiaEMeiaNoite() {
        assertEquals(12 * 60, TvClock.minuteOfDay(6_000));
        assertEquals(0, TvClock.minuteOfDay(18_000));
    }

    @Test
    void oRelogioDaVolta() {
        assertEquals(6 * 60, TvClock.minuteOfDay(24_000));
        assertEquals(1_439, TvClock.minuteOfDay(17_999));
    }

    @Test
    void oDiaContaDeZero() {
        assertEquals(0, TvClock.day(23_999));
        assertEquals(3, TvClock.day(24_000 * 3L + 5));
            }
}
