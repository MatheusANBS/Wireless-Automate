package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** O limite de bytes por minuto e a trava automática do envio de imagens. */
class TvUploadLimitsTest {
    private static final long MB = 1L << 20;
    private static final long HOUR = 60 * 60_000L;

    @Test
    void limitePorMinutoReservaEAndaComAJanela() {
        TvByteRate<String> rate = new TvByteRate<>();
        assertTrue(rate.tryUse("a", 20 * MB, 32 * MB, 0));
        assertFalse(rate.tryUse("a", 13 * MB, 32 * MB, 1000), "passaria de 32 MB no minuto");
        assertTrue(rate.tryUse("a", 12 * MB, 32 * MB, 1000), "cabe certinho");
        assertTrue(rate.tryUse("b", 32 * MB, 32 * MB, 1000), "cada um tem o seu");
        assertEquals(32 * MB, rate.used("a", 59_999));
        assertEquals(12 * MB, rate.used("a", 60_000), "o primeiro saiu da janela");
        assertTrue(rate.tryUse("a", 20 * MB, 32 * MB, 60_000));
        rate.sweep(10 * 60_000);
        assertEquals(0, rate.used("a", 10 * 60_000));
    }

    @Test
    void terceiraBatidaEmDezMinutosTravaPorUmaHora() {
        TvUploadAbuse<String> abuse = new TvUploadAbuse<>();
        assertFalse(abuse.strike("a", 0, HOUR));
        assertFalse(abuse.strike("a", 60_000, HOUR));
        assertEquals(0, abuse.cooldownLeft("a", 60_000));
        assertTrue(abuse.strike("a", 120_000, HOUR), "a terceira em 10 minutos trava");
        assertEquals(HOUR, abuse.cooldownLeft("a", 120_000));
        assertEquals(1, abuse.cooldownLeft("a", 120_000 + HOUR - 1));
        assertEquals(0, abuse.cooldownLeft("a", 120_000 + HOUR), "passou a hora");
        assertEquals(3, abuse.refusals().get("a"));
    }

    @Test
    void batidasEspalhadasNaoTravamEZeroDesliga() {
        TvUploadAbuse<String> abuse = new TvUploadAbuse<>();
        assertFalse(abuse.strike("a", 0, HOUR));
        assertFalse(abuse.strike("a", 6 * 60_000, HOUR));
        assertFalse(abuse.strike("a", 11 * 60_000, HOUR), "a primeira já saiu da janela de 10 minutos");
        assertFalse(abuse.strike("b", 0, 0));
        assertFalse(abuse.strike("b", 1, 0));
        assertFalse(abuse.strike("b", 2, 0), "com 0 minutos a trava não existe");
        assertEquals(0, abuse.cooldownLeft("b", 3));
        assertEquals(3, abuse.refusals().get("b"), "mas a recusa conta na lista");
    }
}
