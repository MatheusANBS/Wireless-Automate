package io.github.matheusanbs.wirelessautomate.range;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RemoteSideTest {
    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final int WEST = 4;
    private static final int EAST = 5;

    @Test
    void canonicalFrameWithFrontNorth() {
        assertEquals(NORTH, RemoteSide.FRONT.canonical());
        assertEquals(SOUTH, RemoteSide.BACK.canonical());
        assertEquals(EAST, RemoteSide.LEFT.canonical());
        assertEquals(WEST, RemoteSide.RIGHT.canonical());
        assertEquals(UP, RemoteSide.TOP.canonical());
        assertEquals(DOWN, RemoteSide.BOTTOM.canonical());
        for (int face = 0; face < 6; face++) {
            assertEquals(face, RemoteSide.fromCanonical(face).canonical());
        }
    }

    @Test
    void roundTripForAllFronts() {
        for (int front : new int[] {NORTH, EAST, SOUTH, WEST}) {
            for (RemoteSide side : RemoteSide.values()) {
                assertEquals(side, RemoteSide.fromAbsolute(front, side.toAbsolute(front)));
            }
            for (int face = 0; face < 6; face++) {
                assertEquals(face, RemoteSide.fromAbsolute(front, face).toAbsolute(front));
            }
        }
    }

    @Test
    void noFrontAndVerticalFrontAreNorth() {
        for (RemoteSide side : RemoteSide.values()) {
            assertEquals(side.toAbsolute(NORTH), side.toAbsolute(-1));
            assertEquals(side.toAbsolute(NORTH), side.toAbsolute(UP));
            assertEquals(side.toAbsolute(NORTH), side.toAbsolute(DOWN));
        }
        assertEquals(RemoteSide.fromAbsolute(NORTH, EAST), RemoteSide.fromAbsolute(-1, EAST));
    }

    @Test
    void frontEastTurnsHorizontalsClockwise() {
        assertEquals(WEST, RemoteSide.BACK.toAbsolute(EAST));
        assertEquals(SOUTH, RemoteSide.LEFT.toAbsolute(EAST));
        assertEquals(EAST, RemoteSide.FRONT.toAbsolute(EAST));
        assertEquals(NORTH, RemoteSide.RIGHT.toAbsolute(EAST));
        assertEquals(UP, RemoteSide.TOP.toAbsolute(EAST));
        assertEquals(RemoteSide.LEFT, RemoteSide.fromAbsolute(EAST, SOUTH));
    }

    @Test
    void keys() {
        assertEquals("front", RemoteSide.FRONT.key());
        assertEquals("bottom", RemoteSide.BOTTOM.key());
    }
}
