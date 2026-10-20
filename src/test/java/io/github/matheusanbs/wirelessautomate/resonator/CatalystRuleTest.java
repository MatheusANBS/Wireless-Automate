package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.resonator.CatalystRule.Kind;
import org.junit.jupiter.api.Test;

class CatalystRuleTest {
    private static final int BASIC = 0;
    private static final int ADVANCED = 1;
    private static final int ELITE = 2;
    private static final int EMERALD = 3;
    private static final int ALLTHEMODIUM = 4;
    private static final int ULTIMATE = 7;

    @Test
    void basicoEAvancadoAceitamRedstoneEPo() {
        for (int tier : new int[] {BASIC, ADVANCED}) {
            assertTrue(CatalystRule.accepts(tier, Kind.REDSTONE, -1));
            assertTrue(CatalystRule.accepts(tier, Kind.DUST, -1));
            assertFalse(CatalystRule.accepts(tier, Kind.CHARGED, ALLTHEMODIUM));
        }
    }

    @Test
    void eliteEEsmeraldaSoOPo() {
        for (int tier : new int[] {ELITE, EMERALD}) {
            assertFalse(CatalystRule.accepts(tier, Kind.REDSTONE, -1));
            assertTrue(CatalystRule.accepts(tier, Kind.DUST, -1));
            assertFalse(CatalystRule.accepts(tier, Kind.CHARGED, ALLTHEMODIUM));
        }
    }

    @Test
    void doAllthemodiumParaCimaSoOPoCarregadoDoTier() {
        for (int tier = ALLTHEMODIUM; tier <= ULTIMATE; tier++) {
            assertFalse(CatalystRule.accepts(tier, Kind.REDSTONE, -1));
            assertFalse(CatalystRule.accepts(tier, Kind.DUST, -1));
            for (int charged = ALLTHEMODIUM; charged <= ULTIMATE; charged++) {
                assertEquals(charged == tier, CatalystRule.accepts(tier, Kind.CHARGED, charged),
                        "Núcleo " + tier + ", Pó Carregado " + charged);
            }
        }
    }

    @Test
    void pedeOPoCarregadoSoDoAllthemodiumParaCima() {
        for (int tier = BASIC; tier <= ULTIMATE; tier++) {
            assertEquals(tier >= ALLTHEMODIUM, CatalystRule.needsCharged(tier));
        }
    }
}
