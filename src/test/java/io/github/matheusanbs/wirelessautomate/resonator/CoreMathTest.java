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
    void maximoEBufferDaSpec() {
        assertEquals(128, CoreMath.maxOutput(1, 64));
        assertEquals(24_576, CoreMath.maxOutput(3, 4_096));
        assertEquals(2_147_483_648L, CoreMath.maxOutput(8, 134_217_728L));
        assertEquals(25_600, CoreMath.buffer(1, 64));
        assertEquals(4_915_200, CoreMath.buffer(3, 4_096));
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
}
