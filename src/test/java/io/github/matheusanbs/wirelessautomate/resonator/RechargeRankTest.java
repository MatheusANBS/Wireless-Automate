package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RechargeRankTest {
    @Test
    void umPorCatalisadorAteOTotal() {
        assertEquals(1, RechargeRank.size(3, 1));
        assertEquals(2, RechargeRank.size(2, 5));
        assertEquals(3, RechargeRank.size(3, 3));
    }

    @Test
    void semOpacoOuSemCatalisadorNinguemRecarrega() {
        assertEquals(0, RechargeRank.size(0, 4));
        assertEquals(0, RechargeRank.size(3, 0));
        assertEquals(0, RechargeRank.size(-1, 2));
        assertEquals(0, RechargeRank.size(2, -1));
    }

    @Test
    void osPrimeirosDaFilaEntram() {
        assertTrue(RechargeRank.includes(0, 1));
        assertFalse(RechargeRank.includes(1, 1));
        assertTrue(RechargeRank.includes(1, 2));
    }

    @Test
    void camaraNaoRecarregavelNuncaEntra() {
        assertFalse(RechargeRank.includes(-1, 8));
        assertFalse(RechargeRank.includes(0, 0));
    }
}
