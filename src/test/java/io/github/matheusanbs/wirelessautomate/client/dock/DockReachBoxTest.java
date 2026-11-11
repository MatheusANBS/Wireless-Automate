package io.github.matheusanbs.wirelessautomate.client.dock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DockReachBoxTest {
    @Test
    void cubeOfSideTwoRPlusOneAroundTheDock() {
        assertArrayEquals(new float[] {-6, -6, -6, 7, 7, 7}, DockReachBox.bounds(6, 64, -64, 320));
    }

    @Test
    void clippedAtTheBottomAndTopOfTheWorld() {
        // Doca em y = -60, raio 16: o fundo para no y = -64 (4 abaixo dela)
        assertArrayEquals(new float[] {-16, -4, -16, 17, 17, 17}, DockReachBox.bounds(16, -60, -64, 320));
        // Doca em y = 315, raio 16: o topo para no 320 (5 acima do canto dela)
        assertArrayEquals(new float[] {-16, -16, -16, 17, 5, 17}, DockReachBox.bounds(16, 315, -64, 320));
    }

    @Test
    void radiusZeroIsTheDockItself() {
        assertArrayEquals(new float[] {0, 0, 0, 1, 1, 1}, DockReachBox.bounds(0, 10, 0, 256));
    }

    @Test
    void twelveAxisAlignedEdgesOnTheBox() {
        float[] box = {-2, -1, -3, 3, 4, 5};
        float[] edges = DockReachBox.edges(box);
        assertEquals(72, edges.length);
        for (int e = 0; e < 12; e++) {
            int axis = e / 4;
            int i = e * 6;
            for (int c = 0; c < 3; c++) {
                float a = edges[i + c];
                float b = edges[i + 3 + c];
                if (c == axis) {
                    assertEquals(box[c], a, "começo da aresta " + e);
                    assertEquals(box[c + 3], b, "fim da aresta " + e);
                } else {
                    assertEquals(a, b, "aresta " + e + " fora do eixo");
                    assertEquals(true, a == box[c] || a == box[c + 3], "aresta " + e + " fora da borda");
                }
            }
        }
    }

    @Test
    void distanceToTheBoxIsZeroInside() {
        float[] box = {-6, -6, -6, 7, 7, 7};
        assertEquals(0, DockReachBox.distanceSqr(box, 0, 0, 0));
        assertEquals(9, DockReachBox.distanceSqr(box, 10, 0, 0));
        assertEquals(9 + 16, DockReachBox.distanceSqr(box, 10, -10, 0));
    }
}
