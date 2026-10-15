package io.github.matheusanbs.wirelessautomate.client.router;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.List;
import org.junit.jupiter.api.Test;

class FaceNetTest {
    private static final int CELL_W = 30;
    private static final int CELL_H = 16;
    private static final int GAP = 2;

    @Test
    void cellOfEachSide() {
        assertArrayEquals(new int[] {1, 0}, FaceNet.cell(RemoteSide.TOP));
        assertArrayEquals(new int[] {0, 1}, FaceNet.cell(RemoteSide.LEFT));
        assertArrayEquals(new int[] {1, 1}, FaceNet.cell(RemoteSide.FRONT));
        assertArrayEquals(new int[] {2, 1}, FaceNet.cell(RemoteSide.RIGHT));
        assertArrayEquals(new int[] {3, 1}, FaceNet.cell(RemoteSide.BACK));
        assertArrayEquals(new int[] {1, 2}, FaceNet.cell(RemoteSide.BOTTOM));
    }

    @Test
    void sideOfEachCellAndOfTheEmptyOnes() {
        for (RemoteSide side : RemoteSide.values()) {
            int[] cell = FaceNet.cell(side);
            assertEquals(side, FaceNet.at(cell[0], cell[1]));
        }
        assertNull(FaceNet.at(0, 0));
        assertNull(FaceNet.at(3, 0));
        assertNull(FaceNet.at(2, 2));
        assertNull(FaceNet.at(4, 1));
        assertNull(FaceNet.at(-1, 1));
    }

    @Test
    void sideUnderAPoint() {
        // o centro da célula de cada lado
        for (RemoteSide side : RemoteSide.values()) {
            int[] cell = FaceNet.cell(side);
            int x = cell[0] * (CELL_W + GAP) + CELL_W / 2;
            int y = cell[1] * (CELL_H + GAP) + CELL_H / 2;
            assertEquals(side, FaceNet.sideAt(x, y, CELL_W, CELL_H, GAP));
        }
        // a borda da célula ainda é dela; o vão entre duas, não
        assertEquals(RemoteSide.LEFT, FaceNet.sideAt(0, CELL_H + GAP, CELL_W, CELL_H, GAP));
        assertEquals(RemoteSide.LEFT, FaceNet.sideAt(CELL_W - 1, CELL_H + GAP, CELL_W, CELL_H, GAP));
        assertNull(FaceNet.sideAt(CELL_W, CELL_H + GAP + 4, CELL_W, CELL_H, GAP));
        // célula vazia e fora da planificação
        assertNull(FaceNet.sideAt(5, 5, CELL_W, CELL_H, GAP));
        assertNull(FaceNet.sideAt(-1, CELL_H + GAP + 4, CELL_W, CELL_H, GAP));
        assertNull(FaceNet.sideAt(4 * (CELL_W + GAP), CELL_H + GAP + 4, CELL_W, CELL_H, GAP));
    }

    @Test
    void orderOfTheSixSquaresInTheList() {
        assertEquals(List.of(RemoteSide.TOP, RemoteSide.FRONT, RemoteSide.LEFT,
                RemoteSide.BOTTOM, RemoteSide.BACK, RemoteSide.RIGHT), FaceNet.CHIP_ORDER);
    }

    @Test
    void shortNameBelowTheCellWidth() {
        assertEquals(FaceNet.Label.FULL, FaceNet.label(40, 20, 40));
        assertEquals(FaceNet.Label.SHORT, FaceNet.label(41, 20, 40));
        assertEquals(FaceNet.Label.SHORT, FaceNet.label(60, 20, 20));
        assertEquals(FaceNet.Label.LETTER, FaceNet.label(60, 21, 20));
    }
}
