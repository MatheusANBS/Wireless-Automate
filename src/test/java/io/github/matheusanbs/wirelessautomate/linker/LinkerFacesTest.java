package io.github.matheusanbs.wirelessautomate.linker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkerFacesTest {
    // índices de Direction: DOWN 0, UP 1, NORTH 2, SOUTH 3, WEST 4, EAST 5
    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final int WEST = 4;
    private static final int EAST = 5;

    private static int bits(int... faces) {
        int mask = 0;
        for (int face : faces) {
            mask |= 1 << face;
        }
        return mask;
    }

    @Test
    void allAndNone() {
        assertEquals(63, LinkerFaces.ALL.mask());
        assertTrue(LinkerFaces.ALL.isAll());
        assertFalse(LinkerFaces.ALL.isEmpty());
        assertTrue(LinkerFaces.NONE.isEmpty());
        assertFalse(LinkerFaces.NONE.isAll());
        assertEquals(List.of(), LinkerFaces.NONE.marked());
        assertEquals(List.of(RemoteSide.values()), LinkerFaces.ALL.marked());
    }

    @Test
    void toggleAddsAndRemoves() {
        LinkerFaces faces = LinkerFaces.NONE.toggle(RemoteSide.BACK);
        assertTrue(faces.contains(RemoteSide.BACK));
        assertFalse(faces.contains(RemoteSide.FRONT));
        assertTrue(faces.toggle(RemoteSide.BACK).isEmpty());
        assertFalse(LinkerFaces.ALL.toggle(RemoteSide.TOP).isAll());
        assertFalse(LinkerFaces.ALL.toggle(RemoteSide.TOP).contains(RemoteSide.TOP));
    }

    @Test
    void markedFollowsButtonOrder() {
        LinkerFaces faces = LinkerFaces.NONE.toggle(RemoteSide.BOTTOM).toggle(RemoteSide.LEFT)
                .toggle(RemoteSide.BACK).toggle(RemoteSide.FRONT);
        assertEquals(List.of(RemoteSide.FRONT, RemoteSide.BACK, RemoteSide.LEFT, RemoteSide.BOTTOM), faces.marked());
    }

    @Test
    void invalidMaskDropsHighBits() {
        assertEquals(63, LinkerFaces.of(0xFFFF).mask());
        assertEquals(1, LinkerFaces.of(64 | 1).mask());
        assertTrue(LinkerFaces.of(64).isEmpty());
        assertEquals(LinkerFaces.ALL, LinkerFaces.of(-1));
    }

    @Test
    void absoluteMaskFacingSouth() {
        LinkerFaces backAndBottom = LinkerFaces.NONE.toggle(RemoteSide.BACK).toggle(RemoteSide.BOTTOM);
        assertEquals(bits(NORTH, DOWN), backAndBottom.absoluteMask(SOUTH));
        assertEquals(bits(SOUTH), LinkerFaces.NONE.toggle(RemoteSide.FRONT).absoluteMask(SOUTH));
    }

    @Test
    void absoluteMaskFacingEast() {
        assertEquals(bits(EAST), LinkerFaces.NONE.toggle(RemoteSide.FRONT).absoluteMask(EAST));
        assertEquals(bits(WEST), LinkerFaces.NONE.toggle(RemoteSide.BACK).absoluteMask(EAST));
        assertEquals(bits(UP), LinkerFaces.NONE.toggle(RemoteSide.TOP).absoluteMask(EAST));
        // Esquerda e Direita vistas de frente: com a frente em Leste, a Esquerda é Sul
        assertEquals(bits(SOUTH), LinkerFaces.NONE.toggle(RemoteSide.LEFT).absoluteMask(EAST));
        assertEquals(bits(NORTH), LinkerFaces.NONE.toggle(RemoteSide.RIGHT).absoluteMask(EAST));
    }

    @Test
    void absoluteMaskWithoutFrontIsNorth() {
        assertEquals(bits(NORTH), LinkerFaces.NONE.toggle(RemoteSide.FRONT).absoluteMask(-1));
        assertEquals(bits(SOUTH), LinkerFaces.NONE.toggle(RemoteSide.BACK).absoluteMask(-1));
        assertEquals(63, LinkerFaces.ALL.absoluteMask(-1));
        assertEquals(63, LinkerFaces.ALL.absoluteMask(EAST));
        assertEquals(0, LinkerFaces.NONE.absoluteMask(SOUTH));
    }
}
