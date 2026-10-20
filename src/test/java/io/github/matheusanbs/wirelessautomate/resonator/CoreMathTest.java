package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CoreMathTest {
    @Test
    void camarasPeloTier() {
        assertEquals(1, CoreMath.chambers(0));
        assertEquals(3, CoreMath.chambers(2));
        assertEquals(8, CoreMath.chambers(7));
    }

    @Test
    void fePorCristalPadraoDaTabela() {
        long[] expected = {128, 1_024, 8_192, 32_768, 262_144, 2_097_152, 134_217_728L, 1_073_741_824L};
        for (int tier = 0; tier < expected.length; tier++) {
            assertEquals(expected[tier], CoreMath.defaultFePerCrystal(tier), "tier " + tier);
        }
    }

    @Test
    void maximoEBufferDaSpec() {
        assertEquals(256, CoreMath.maxOutput(1, 128));
        assertEquals(49_152, CoreMath.maxOutput(3, 8_192));
        assertEquals(1_879_048_192L, CoreMath.maxOutput(7, 134_217_728L));
        assertEquals(17_179_869_184L, CoreMath.maxOutput(8, 1_073_741_824L));
        assertEquals(51_200, CoreMath.buffer(1, 128));
        assertEquals(9_830_400, CoreMath.buffer(3, 8_192));
        assertEquals(3_435_973_836_800L, CoreMath.buffer(8, 1_073_741_824L));
    }

    @Test
    void geracaoDoUltimateCabeNoLong() {
        assertEquals(17_179_869_184L, CoreMath.perTick(1_073_741_824L, 16, 1.0));
        assertEquals(17_179_869_184L * 20, CoreMath.mulSat(CoreMath.perTick(1_073_741_824L, 16, 1.0), 20));
    }

    @Test
    void geracaoPorTickArredondaParaBaixo() {
        assertEquals(4_096 * 4, CoreMath.perTick(4_096, 4, 1.0));
        assertEquals(11_796, CoreMath.perTick(4_096, 4, 0.72));
        assertEquals(0, CoreMath.perTick(4_096, 4, 0.0));
        assertEquals(0, CoreMath.perTick(4_096, 0, 1.0));
    }

    @Test
    void multiplicacaoSatura() {
        assertEquals(Long.MAX_VALUE, CoreMath.mulSat(Long.MAX_VALUE / 2, 3));
        assertEquals(Long.MAX_VALUE, CoreMath.buffer(8, Long.MAX_VALUE / 4));
        assertEquals(6, CoreMath.mulSat(2, 3));
    }

    @Test
    void faixaDeVazaoPelaRaiz() {
        assertEquals(0, CoreMath.flowBand(0, 20, 1_000));
        assertEquals(0, CoreMath.flowBand(5, 20, 0));
        assertEquals(7, CoreMath.flowBand(20_000, 20, 1_000));
        assertEquals(1 + (int) Math.floor(7 * Math.sqrt(0.01)), CoreMath.flowBand(20 * 1_000 / 100, 20, 1_000));
        assertEquals(1, CoreMath.flowBand(20 * 1_000 / 100, 20, 1_000));
        assertEquals(4, CoreMath.flowBand(20 * 250, 20, 1_000));
        assertEquals(7, CoreMath.flowBand(Long.MAX_VALUE, 20, 1));
    }
}
