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
    void escadaNova() {
        long[] esperado = {216, 6_912, 10_000, 160_000, 6_400_000, 6_700_000, 184_000_000L, 268_435_456L};
        for (int i = 0; i < esperado.length; i++) {
            assertEquals(esperado[i], CoreMath.defaultFePerCrystal(i), "tier " + i);
        }
    }

    @Test
    void quartosPorTier() {
        assertEquals(4, CoreMath.yieldQuarters(CoreMath.KIND_SIGNAL, -1, 0));
        assertEquals(8, CoreMath.yieldQuarters(CoreMath.KIND_CUT, -1, 1));
        assertEquals(1, CoreMath.yieldQuarters(CoreMath.KIND_SIGNAL, -1, 2));
        assertEquals(2, CoreMath.yieldQuarters(CoreMath.KIND_CUT, -1, 7));
        assertEquals(8, CoreMath.yieldQuarters(CoreMath.KIND_TUNED, 3, 3));
        assertEquals(8, CoreMath.yieldQuarters(CoreMath.KIND_TUNED, 7, 4));
        assertEquals(2, CoreMath.yieldQuarters(CoreMath.KIND_TUNED, 2, 3));
        assertEquals(8, CoreMath.yieldQuarters(CoreMath.KIND_TUNED, 2, 0));
    }

    @Test
    void perTickEmQuartos() {
        assertEquals(432, CoreMath.perTick(216, 8, 1.0));
        assertEquals(54, CoreMath.perTick(216, 1, 1.0));
        assertEquals(388, CoreMath.perTick(216, 8, 0.9));
        assertEquals(0, CoreMath.perTick(216, 0, 1.0));
        assertEquals(4_294_967_296L, CoreMath.maxOutput(8, 268_435_456L));
        assertEquals(64_000_000L, CoreMath.maxOutput(5, 6_400_000L));
    }

    @Test
    void maximoEBufferDaSpec() {
        assertEquals(432, CoreMath.maxOutput(1, 216));
        assertEquals(60_000, CoreMath.maxOutput(3, 10_000));
        assertEquals(2_576_000_000L, CoreMath.maxOutput(7, 184_000_000L));
        assertEquals(86_400, CoreMath.buffer(1, 216));
        assertEquals(12_000_000, CoreMath.buffer(3, 10_000));
    }

    @Test
    void geracaoDoUltimateCabeNoLong() {
        long max = CoreMath.perTick(268_435_456L, 64, 1.0);
        assertEquals(4_294_967_296L, max);
        assertEquals(max * 20, CoreMath.mulSat(max, 20));
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
    @Test
    void vidaPadraoPorTier() {
        int[] esperado = {20, 20, 15, 10, 5, 5, 4, 3};
        for (int i = 0; i < esperado.length; i++) {
            assertEquals(esperado[i], CoreMath.defaultLifeMinutes(i), "tier " + i);
        }
        assertEquals(3, CoreMath.defaultLifeMinutes(99));
    }

    @Test
    void vidaNaEscalaDoTier() {
        long minuto = CoreMath.TICKS_PER_MINUTE;
        // No Básico e no Avançado (20 min) a vida da config fica como está: os mundos antigos não mudam.
        assertEquals(24_000L, CoreMath.crystalLife(24_000L, 20 * minuto));
        assertEquals(12_000L, CoreMath.crystalLife(12_000L, 20 * minuto));
        // O Afinado (base do Lapidado, 20 min) no Ultimate (3 min) e o Cristal de Sinal (10 min) no Elite (15 min).
        assertEquals(3_600L, CoreMath.crystalLife(24_000L, 3 * minuto));
        assertEquals(9_000L, CoreMath.crystalLife(12_000L, 15 * minuto));
        assertEquals(1L, CoreMath.crystalLife(1L, minuto), "nunca menos de 1 tick");
        assertEquals(0L, CoreMath.crystalLife(0L, minuto), "sem vida não gasta");
        assertEquals(0L, CoreMath.crystalLife(24_000L, 0L));
        assertEquals(Long.MAX_VALUE, CoreMath.crystalLife(Long.MAX_VALUE, 240 * minuto), "satura");
        assertEquals(Long.MAX_VALUE / 24_000L * 1_200L, CoreMath.crystalLife(Long.MAX_VALUE, minuto));
    }
}
