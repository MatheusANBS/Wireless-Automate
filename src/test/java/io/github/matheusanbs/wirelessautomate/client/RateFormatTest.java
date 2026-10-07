package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RateFormatTest {
    @Test
    void smallValuesStayWhole() {
        assertEquals("0", RateFormat.abbreviate(0));
        assertEquals("7", RateFormat.abbreviate(7));
        assertEquals("999", RateFormat.abbreviate(999));
    }

    @Test
    void largeValuesGetSuffix() {
        assertEquals("1k", RateFormat.abbreviate(1_000));
        assertEquals("1.2k", RateFormat.abbreviate(1_240));
        assertEquals("12.3k", RateFormat.abbreviate(12_345));
        assertEquals("123k", RateFormat.abbreviate(123_456));
        assertEquals("1.5M", RateFormat.abbreviate(1_500_000));
        assertEquals("4M", RateFormat.abbreviate(4_000_000));
        assertEquals("9.2E", RateFormat.abbreviate(Long.MAX_VALUE));
    }

    @Test
    void roundingCarriesToNextUnit() {
        assertEquals("100k", RateFormat.abbreviate(99_960));
        assertEquals("1M", RateFormat.abbreviate(999_999));
        assertEquals("1M", RateFormat.abbreviate(999_600));
    }

    @Test
    void negativesKeepSign() {
        assertEquals("-1.5k", RateFormat.abbreviate(-1_500));
        assertEquals("-12", RateFormat.abbreviate(-12));
    }
}
