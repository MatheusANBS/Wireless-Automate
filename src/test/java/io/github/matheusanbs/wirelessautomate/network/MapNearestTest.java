package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class MapNearestTest {
    @Test
    void keepsAllWhenUnderTheCap() {
        assertArrayEquals(new int[] {0, 1, 2}, MapNearest.pick(new long[] {5, 1, 9}, 3));
        assertArrayEquals(new int[] {0, 1, 2}, MapNearest.pick(new long[] {5, 1, 9}, 10));
    }

    @Test
    void keepsTheNearestInOriginalOrder() {
        assertArrayEquals(new int[] {1, 3}, MapNearest.pick(new long[] {50, 2, 40, 1, 99}, 2));
    }

    @Test
    void tiesGoToTheLowerIndex() {
        assertArrayEquals(new int[] {0, 1}, MapNearest.pick(new long[] {7, 7, 7, 7}, 2));
    }

    @Test
    void aZeroCapKeepsNothing() {
        assertArrayEquals(new int[0], MapNearest.pick(new long[] {1, 2}, 0));
    }
}
