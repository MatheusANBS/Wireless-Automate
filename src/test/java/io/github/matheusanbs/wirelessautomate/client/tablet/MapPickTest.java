package io.github.matheusanbs.wirelessautomate.client.tablet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapPickTest {
    private static final int[] XS = {10, 20, 20, 40};
    private static final int[] YS = {10, 20, 20, 40};

    @Test
    void nearestWithinTheDistance() {
        assertEquals(0, MapPick.nearest(XS, YS, 4, 11, 11, 16, -1));
        assertEquals(3, MapPick.nearest(XS, YS, 4, 42, 40, 16, -1));
    }

    @Test
    void nothingBeyondTheDistance() {
        assertEquals(-1, MapPick.nearest(XS, YS, 4, 30, 30, 16, -1));
        assertEquals(1, MapPick.nearest(XS, YS, 4, 24, 20, 16, -1));
        assertEquals(-1, MapPick.nearest(XS, YS, 4, 25, 20, 16, -1));
    }

    @Test
    void overlappingPointsPickTheFirst() {
        assertEquals(1, MapPick.nearest(XS, YS, 4, 20, 20, 16, -1));
        assertEquals(1, MapPick.nearest(XS, YS, 4, 20, 20, 16, 0));
    }

    @Test
    void overlappingPointsPickThePreferred() {
        assertEquals(2, MapPick.nearest(XS, YS, 4, 20, 20, 16, 2));
    }

    @Test
    void closerBeatsThePreferred() {
        int[] xs = {20, 22};
        int[] ys = {20, 20};
        assertEquals(1, MapPick.nearest(xs, ys, 2, 22, 20, 16, 0));
    }

    @Test
    void onlyTheFirstCountPoints() {
        assertEquals(-1, MapPick.nearest(XS, YS, 3, 40, 40, 16, -1));
        assertEquals(-1, MapPick.nearest(XS, YS, 0, 10, 10, 16, -1));
    }
}
