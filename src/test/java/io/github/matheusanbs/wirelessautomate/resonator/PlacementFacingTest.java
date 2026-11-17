package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import org.junit.jupiter.api.Test;

class PlacementFacingTest {
    private static final int[] ORIGIN = {10, 20, 30};

    @Test
    void asQuatroCasasDasPortasEDoSintonizador() {
        assertEquals(Face.NORTH, PlacementFacing.wallFacing(ORIGIN, 12, 20, 30));
        assertEquals(Face.SOUTH, PlacementFacing.wallFacing(ORIGIN, 12, 20, 34));
        assertEquals(Face.WEST, PlacementFacing.wallFacing(ORIGIN, 10, 20, 32));
        assertEquals(Face.EAST, PlacementFacing.wallFacing(ORIGIN, 14, 20, 32));
    }

    @Test
    void umCantoNaoTemParede() {
        assertNull(PlacementFacing.wallFacing(ORIGIN, 10, 20, 30));
        assertNull(PlacementFacing.wallFacing(ORIGIN, 14, 20, 34));
    }

    @Test
    void soAPrimeiraCamadaTemParede() {
        assertNull(PlacementFacing.wallFacing(ORIGIN, 12, 21, 30));
        assertNull(PlacementFacing.wallFacing(ORIGIN, 14, 22, 32));
    }

    @Test
    void foraDaCaixaEOMioloNaoSaoParede() {
        assertNull(PlacementFacing.wallFacing(ORIGIN, 12, 19, 30));
        assertNull(PlacementFacing.wallFacing(ORIGIN, 9, 20, 32));
        assertNull(PlacementFacing.wallFacing(ORIGIN, 12, 20, 32));
        assertNull(PlacementFacing.wallFacing(ORIGIN, 11, 20, 31));
    }
}
