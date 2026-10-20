package io.github.matheusanbs.wirelessautomate.client.tablet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapLinesTest {
    private static List<String> pixels(MapLines lines) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            out.add(lines.x(i) + "," + lines.y(i));
        }
        return out;
    }

    @Test
    void horizontalLineIsDashedFromTheStart() {
        MapLines lines = new MapLines();
        lines.dashed(0, 0, 9, 0, -100, -100, 100, 100);
        // traços de 2 e vãos de 3: 0 1 · 5 6 · (10 fica fora)
        assertEquals(List.of("0,0", "1,0", "5,0", "6,0"), pixels(lines));
    }

    @Test
    void diagonalStepsOnePixelPerStep() {
        MapLines lines = new MapLines();
        lines.dashed(0, 0, 6, 6, -100, -100, 100, 100);
        assertEquals(List.of("0,0", "1,1", "5,5", "6,6"), pixels(lines));
    }

    @Test
    void pointLineIsOnePixel() {
        MapLines lines = new MapLines();
        lines.dashed(3, 4, 3, 4, 0, 0, 10, 10);
        assertEquals(List.of("3,4"), pixels(lines));
    }

    @Test
    void clippedLineKeepsTheDashPhaseFromTheRouter() {
        MapLines whole = new MapLines();
        whole.dashed(0, 0, 40, 0, -100, -100, 100, 100);
        MapLines clipped = new MapLines();
        clipped.dashed(0, 0, 40, 0, 12, -5, 30, 5);
        List<String> expected = new ArrayList<>();
        for (String p : pixels(whole)) {
            int x = Integer.parseInt(p.split(",")[0]);
            if (x >= 12 && x <= 30) {
                expected.add(p);
            }
        }
        assertEquals(expected, pixels(clipped));
    }

    @Test
    void lineOutsideTheRectangleAddsNothing() {
        MapLines lines = new MapLines();
        lines.dashed(0, 50, 40, 60, 0, 0, 30, 30);
        assertEquals(0, lines.size());
    }

    @Test
    void farLineOnlyWalksInside() {
        MapLines lines = new MapLines();
        lines.dashed(0, 0, 1_000_000, 0, 0, 0, 99, 0);
        assertEquals(40, lines.size());
        for (String p : pixels(lines)) {
            assertTrue(Integer.parseInt(p.split(",")[0]) <= 99);
        }
    }

    @Test
    void clearReusesTheArrays() {
        MapLines lines = new MapLines();
        lines.dashed(0, 0, 2000, 0, 0, 0, 2000, 0);
        assertTrue(lines.size() > 256);
        lines.clear();
        assertEquals(0, lines.size());
        lines.dashed(0, 0, 1, 0, 0, 0, 10, 10);
        assertEquals(List.of("0,0", "1,0"), pixels(lines));
    }
}
