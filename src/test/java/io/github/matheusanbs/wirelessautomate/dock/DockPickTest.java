package io.github.matheusanbs.wirelessautomate.dock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DockPickTest {
    @Test
    void emptyListGivesMinusOne() {
        assertEquals(-1, DockPick.pick(0, 0, i -> true, i -> true, 8));
    }

    @Test
    void startsAtTheCursor() {
        assertEquals(2, DockPick.pick(4, 2, i -> true, i -> true, 8));
    }

    @Test
    void skipsSleepingMachines() {
        assertEquals(3, DockPick.pick(4, 1, i -> i == 3, i -> true, 8));
    }

    @Test
    void wrapsAroundToTheStart() {
        assertEquals(0, DockPick.pick(4, 2, i -> i == 0, i -> true, 8));
    }

    @Test
    void cursorBeyondCountWrapsByModulo() {
        assertEquals(1, DockPick.pick(4, 9, i -> true, i -> true, 8));
        assertEquals(1, DockPick.pick(4, -3, i -> true, i -> true, 8));
    }

    @Test
    void acceptsIsOnlyAskedOfUsableOnes() {
        boolean[] asked = new boolean[4];
        int r = DockPick.pick(4, 0, i -> i >= 2, i -> {
            asked[i] = true;
            return i == 3;
        }, 8);
        assertEquals(3, r);
        assertEquals(false, asked[0] || asked[1]);
    }

    @Test
    void stopsAfterMaxUsableTries() {
        int[] tries = {0};
        int r = DockPick.pick(20, 0, i -> true, i -> {
            tries[0]++;
            return i == 12;
        }, 8);
        assertEquals(-1, r);
        assertEquals(8, tries[0]);
    }

    @Test
    void unusableDoNotCountAgainstTheLimit() {
        assertEquals(15, DockPick.pick(20, 0, i -> i >= 10, i -> i == 15, 8));
    }

    @Test
    void oneLapAtMostAndNoneAcceptsGivesMinusOne() {
        int[] tries = {0};
        assertEquals(-1, DockPick.pick(3, 1, i -> true, i -> {
            tries[0]++;
            return false;
        }, 8));
        assertEquals(3, tries[0]);
    }
}
