package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PlacementFacingTest {
    private static final int[] ORIGIN = {10, 20, 30};

    @Test
    void miolosDasFacesDaoAPropriaFace() {
        assertEquals(Face.WEST, PlacementFacing.wallFacing(ORIGIN, 10, 22, 32, 0, 1, 0));
        assertEquals(Face.EAST, PlacementFacing.wallFacing(ORIGIN, 14, 21, 33, 0, 0, -1));
        assertEquals(Face.DOWN, PlacementFacing.wallFacing(ORIGIN, 12, 20, 32, 1, 0, 0));
        assertEquals(Face.UP, PlacementFacing.wallFacing(ORIGIN, 12, 24, 32, 0, -1, 0));
        assertEquals(Face.NORTH, PlacementFacing.wallFacing(ORIGIN, 12, 22, 30, 0, 0, 1));
        assertEquals(Face.SOUTH, PlacementFacing.wallFacing(ORIGIN, 13, 23, 34, 0, 0, 0));
    }

    @Test
    void arestaEscolheAParedeDoLadoDoJogador() {
        assertEquals(Face.EAST, PlacementFacing.wallFacing(ORIGIN, 14, 22, 30, 5, 0, -2));
        assertEquals(Face.NORTH, PlacementFacing.wallFacing(ORIGIN, 14, 22, 30, 2, 0, -5));
    }

    @Test
    void empateNaArestaVaiPelaOrdemDoDirection() {
        assertEquals(Face.NORTH, PlacementFacing.wallFacing(ORIGIN, 14, 22, 30, 3, 0, -3));
        assertEquals(Face.DOWN, PlacementFacing.wallFacing(ORIGIN, 10, 20, 32, 0, 0, 0));
    }

    @Test
    void cantoComTresParedes() {
        assertEquals(Face.UP, PlacementFacing.wallFacing(ORIGIN, 10, 24, 34, -1, 4, 2));
        assertEquals(Face.WEST, PlacementFacing.wallFacing(ORIGIN, 10, 24, 34, -5, 1, 2));
        assertEquals(Face.SOUTH, PlacementFacing.wallFacing(ORIGIN, 10, 24, 34, -1, 1, 6));
    }

    @Test
    void mioloDaCaixaNaoEParede() {
        assertNull(PlacementFacing.wallFacing(ORIGIN, 12, 22, 32, 1, 0, 0));
        assertNull(PlacementFacing.wallFacing(ORIGIN, 11, 23, 31, 0, 1, 0));
    }

    @Test
    void foraDaCaixaNaoEParede() {
        assertNull(PlacementFacing.wallFacing(ORIGIN, 9, 22, 32, -1, 0, 0));
        assertNull(PlacementFacing.wallFacing(ORIGIN, 15, 22, 32, 1, 0, 0));
        assertNull(PlacementFacing.wallFacing(ORIGIN, 12, 25, 32, 0, 1, 0));
        assertNull(PlacementFacing.wallFacing(ORIGIN, 12, 22, 29, 0, 0, -1));
    }

    /** A caixa de um Núcleo em cada face: o Núcleo fica na parede dele e a parede oposta fica a quatro blocos. */
    @ParameterizedTest
    @EnumSource(Face.class)
    void cadaFaceDoNucleoDaACaixaCerta(Face coreFace) {
        int cx = 7;
        int cy = 64;
        int cz = -3;
        int[] origin = ResonatorBlueprint.origin(cx, cy, cz, coreFace);
        assertEquals(coreFace, PlacementFacing.wallFacing(origin, cx, cy, cz, 0, 0, 0));
        int fx = cx - 4 * coreFace.dx();
        int fy = cy - 4 * coreFace.dy();
        int fz = cz - 4 * coreFace.dz();
        Face opposite = Face.values()[coreFace.ordinal() ^ 1];
        assertEquals(opposite, PlacementFacing.wallFacing(origin, fx, fy, fz, 0, 0, 0));
        assertNull(PlacementFacing.wallFacing(origin, cx - coreFace.dx(), cy - coreFace.dy(), cz - coreFace.dz(), 0, 0, 0));
        assertNull(PlacementFacing.wallFacing(origin, cx + coreFace.dx(), cy + coreFace.dy(), cz + coreFace.dz(), 0, 0, 0));
    }
}
