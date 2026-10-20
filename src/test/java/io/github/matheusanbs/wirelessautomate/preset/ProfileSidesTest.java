package io.github.matheusanbs.wirelessautomate.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import org.junit.jupiter.api.Test;

class ProfileSidesTest {
    // RelativeSide: FRONT, BACK, TOP, BOTTOM, LEFT, RIGHT
    private static final int R_FRONT = 0;
    private static final int R_BACK = 1;
    private static final int R_TOP = 2;
    private static final int R_BOTTOM = 3;
    private static final int R_LEFT = 4;
    private static final int R_RIGHT = 5;

    private static RemoteSide remote(int relative, int frame) {
        return RemoteSide.values()[ProfileSides.toRemote(relative, frame)];
    }

    @Test
    void routerOnTopOfAMachineFacingNorth() {
        // facing=up sem giro: FRONT é o cima do mundo, BOTTOM o norte (a frente da máquina), LEFT o leste
        int frame = ProfileSides.frame(R_BOTTOM, R_FRONT);
        assertEquals(RemoteSide.TOP, remote(R_FRONT, frame));
        assertEquals(RemoteSide.BOTTOM, remote(R_BACK, frame));
        assertEquals(RemoteSide.FRONT, remote(R_BOTTOM, frame));
        assertEquals(RemoteSide.BACK, remote(R_TOP, frame));
        assertEquals(RemoteSide.LEFT, remote(R_LEFT, frame));
        assertEquals(RemoteSide.RIGHT, remote(R_RIGHT, frame));
    }

    @Test
    void routerOnTheFrontOfTheMachine() {
        // o roteador preso na frente: FRONT do roteador é a frente da máquina e TOP é o cima do mundo
        int frame = ProfileSides.frame(R_FRONT, R_TOP);
        assertEquals(RemoteSide.FRONT, remote(R_FRONT, frame));
        assertEquals(RemoteSide.BACK, remote(R_BACK, frame));
        assertEquals(RemoteSide.TOP, remote(R_TOP, frame));
        assertEquals(RemoteSide.BOTTOM, remote(R_BOTTOM, frame));
        assertEquals(RemoteSide.LEFT, remote(R_LEFT, frame));
        assertEquals(RemoteSide.RIGHT, remote(R_RIGHT, frame));
    }

    @Test
    void routerOnTheEastSideOfAMachineFacingSouth() {
        // facing=east: FRONT é leste, LEFT é sul (a frente da máquina) e TOP é o cima; o leste da máquina virada
        // para o sul é a direita dela
        int frame = ProfileSides.frame(R_LEFT, R_TOP);
        assertEquals(RemoteSide.FRONT, remote(R_LEFT, frame));
        assertEquals(RemoteSide.BACK, remote(R_RIGHT, frame));
        assertEquals(RemoteSide.RIGHT, remote(R_FRONT, frame));
        assertEquals(RemoteSide.LEFT, remote(R_BACK, frame));
        assertEquals(RemoteSide.TOP, remote(R_TOP, frame));
        assertEquals(RemoteSide.BOTTOM, remote(R_BOTTOM, frame));
    }

    @Test
    void everyFrameIsABijection() {
        int frames = 0;
        for (int front = 0; front < 6; front++) {
            for (int up = 0; up < 6; up++) {
                int frame = ProfileSides.frame(front, up);
                if (frame == ProfileSides.NONE) {
                    continue;
                }
                frames++;
                assertTrue(ProfileSides.valid(frame));
                boolean[] seen = new boolean[6];
                for (int side = 0; side < 6; side++) {
                    int to = ProfileSides.toRemote(side, frame);
                    assertFalse(seen[to], "dois lados no mesmo lado da máquina");
                    seen[to] = true;
                }
            }
        }
        assertEquals(24, frames, "as 24 orientações");
    }

    @Test
    void parallelSidesAndNoneAreNotFrames() {
        assertEquals(ProfileSides.NONE, ProfileSides.frame(R_FRONT, R_BACK));
        assertEquals(ProfileSides.NONE, ProfileSides.frame(R_TOP, R_TOP));
        assertFalse(ProfileSides.valid(ProfileSides.NONE));
        assertFalse(ProfileSides.valid(1));
        assertFalse(ProfileSides.valid(99));
    }
}
