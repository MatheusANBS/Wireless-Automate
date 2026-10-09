package io.github.matheusanbs.wirelessautomate.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LnChannelPlanTest {
    @Test
    void eliteCabeNoDiamante() {
        // Elite: 2.048 itens/s, 128.000 mB/s, 64.000 FE/t.
        LnChannelPlan plan = LnChannelPlan.forRates(2_048, 128_000, 64_000);
        assertEquals(LnChannelPlan.DIAMOND, plan.upgrade());
        assertEquals(102, plan.items());
        assertEquals(6_400, plan.fluid());
        assertEquals(64_000, plan.energy());
    }

    @Test
    void acimaDoDiamantePassaParaONetherite() {
        // Esmeralda: 16.384 itens/s = 819 por tick, acima dos 256 do Diamante.
        LnChannelPlan plan = LnChannelPlan.forRates(16_384, 1_024_000, 512_000);
        assertEquals(LnChannelPlan.NETHERITE, plan.upgrade());
        assertEquals(819, plan.items());
        assertEquals(51_200, plan.fluid());
        assertEquals(512_000, plan.energy());
    }

    @Test
    void semLimiteVaiAoTetoDoNetherite() {
        LnChannelPlan plan = LnChannelPlan.forRates(0, 0, 0);
        assertEquals(LnChannelPlan.NETHERITE, plan.upgrade());
        assertEquals(10_000, plan.items());
        assertEquals(2_100_000_000, plan.fluid());
        assertEquals(Integer.MAX_VALUE, plan.energy());
    }

    @Test
    void acimaDoTetoDoNetheriteFicaNoTeto() {
        LnChannelPlan plan = LnChannelPlan.forRates(8_388_608, 524_288_000, 262_144_000);
        assertEquals(10_000, plan.items());
        assertEquals(26_214_400, plan.fluid());
        assertEquals(262_144_000, plan.energy());
    }

    @Test
    void vazaoPequenaNuncaZera() {
        assertEquals(1, LnChannelPlan.perTick(10));
        assertEquals(0, LnChannelPlan.perTick(0));
        assertEquals(1, LnChannelPlan.forRates(10, 2_000, 1_000).items());
    }
}
