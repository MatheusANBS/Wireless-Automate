package io.github.matheusanbs.wirelessautomate.repairer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RepairMathTest {
    @Test
    void tierDoCustoPeloTipoDoCristal() {
        assertEquals(0, RepairMath.costTier(false, -1));
        assertEquals(1, RepairMath.costTier(true, -1));
        assertEquals(2, RepairMath.costTier(true, 2));
        assertEquals(7, RepairMath.costTier(false, 7));
    }

    @Test
    void custoEhFatorVezesOFePorCristal() {
        assertEquals(432, RepairMath.cost(216, 2));
        assertEquals(13_824, RepairMath.cost(6_912, 2));
        assertEquals(536_870_912L, RepairMath.cost(268_435_456L, 2));
        assertEquals(0, RepairMath.cost(216, 0));
    }

    @Test
    void custoSaturaEmVezDeEstourar() {
        assertEquals(Long.MAX_VALUE, RepairMath.cost(Long.MAX_VALUE, 1000));
        assertEquals(0, RepairMath.cost(-5, 2));
        assertEquals(0, RepairMath.cost(216, -3));
    }

    @Test
    void bufferCobreAsOitoLinhas() {
        assertEquals(4_294_967_296L, RepairMath.bufferCapacity(536_870_912L, 8));
        assertEquals(Long.MAX_VALUE, RepairMath.bufferCapacity(Long.MAX_VALUE, 8));
        assertEquals(1L, RepairMath.bufferCapacity(0L, 8));
    }

    @Test
    void progressoEmMilesimos() {
        assertEquals(0, RepairMath.progressPermille(100, 240, 140));
        assertEquals(500, RepairMath.progressPermille(170, 240, 140));
        assertEquals(1000, RepairMath.progressPermille(240, 240, 140));
        assertEquals(1000, RepairMath.progressPermille(500, 240, 140));
        assertEquals(0, RepairMath.progressPermille(50, 240, 140));
        assertEquals(1000, RepairMath.progressPermille(10, 20, 0));
    }
}
