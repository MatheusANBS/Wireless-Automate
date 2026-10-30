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

    @Test
    void quantidadeTruncaEmVezDeArredondar() {
        assertEquals("0", RateFormat.abbreviateAmount(0));
        assertEquals("999", RateFormat.abbreviateAmount(999));
        assertEquals("1k", RateFormat.abbreviateAmount(1_000));
        assertEquals("1k", RateFormat.abbreviateAmount(1_099));
        assertEquals("1.9k", RateFormat.abbreviateAmount(1_999));
        assertEquals("2.6k", RateFormat.abbreviateAmount(2_664));
        assertEquals("99.9k", RateFormat.abbreviateAmount(99_999));
        assertEquals("100k", RateFormat.abbreviateAmount(100_000));
        assertEquals("999k", RateFormat.abbreviateAmount(999_999));
        assertEquals("1M", RateFormat.abbreviateAmount(1_000_000));
        assertEquals("1.9M", RateFormat.abbreviateAmount(1_999_999));
        assertEquals("8.5G", RateFormat.abbreviateAmount(8_589_934_592L));
    }

    @Test
    void quantidadeNaoEstouraNosLimites() {
        assertEquals("9.2E", RateFormat.abbreviateAmount(Long.MAX_VALUE));
        assertEquals("-9.2E", RateFormat.abbreviateAmount(Long.MIN_VALUE));
        assertEquals("-2.6k", RateFormat.abbreviateAmount(-2_664));
        assertEquals("-12", RateFormat.abbreviateAmount(-12));
    }
}
