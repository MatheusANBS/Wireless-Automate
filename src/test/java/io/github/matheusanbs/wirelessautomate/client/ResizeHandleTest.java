package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResizeHandleTest {
    // painel em (100, 50), 200 × 120: borda direita em x = 300, de baixo em y = 170
    private final ResizeHandle handle = new ResizeHandle(7, 3);

    @Test
    void cornerWinsOverEdges() {
        assertEquals(ResizeHandle.Edge.BOTH, handle.at(299, 169, 100, 50, 200, 120));
        assertEquals(ResizeHandle.Edge.BOTH, handle.at(293, 163, 100, 50, 200, 120));
    }

    @Test
    void outsideThePanelIsNull() {
        assertNull(handle.at(300, 100, 100, 50, 200, 120));
        assertNull(handle.at(200, 170, 100, 50, 200, 120));
        assertNull(handle.at(99, 100, 100, 50, 200, 120));
        assertNull(handle.at(200, 49, 100, 50, 200, 120));
        // dentro, longe das bordas
        assertNull(handle.at(200, 100, 100, 50, 200, 120));
    }

    @Test
    void rightAndBottomEdges() {
        assertEquals(ResizeHandle.Edge.WIDTH, handle.at(297, 100, 100, 50, 200, 120));
        assertNull(handle.at(296, 100, 100, 50, 200, 120));
        assertEquals(ResizeHandle.Edge.HEIGHT, handle.at(200, 167, 100, 50, 200, 120));
        assertNull(handle.at(200, 166, 100, 50, 200, 120));
    }

    @Test
    void gripSizeComesFromTheConstructor() {
        ResizeHandle chest = new ResizeHandle(9, 3);
        assertEquals(ResizeHandle.Edge.BOTH, chest.at(291, 161, 100, 50, 200, 120));
        assertNull(handle.at(291, 161, 100, 50, 200, 120));
    }

    @Test
    void wantedSizeKeepsThePanelCentered() {
        assertTrue(handle.begin(298, 100, 100, 50, 200, 120));
        assertTrue(handle.dragging());
        assertEquals(ResizeHandle.Edge.WIDTH, handle.edge());
        // parado, pede o tamanho de hoje; 10 px para fora, +20 (a borda oposta anda igual)
        assertEquals(200, handle.wantedWidth(298), 1e-9);
        assertEquals(220, handle.wantedWidth(308), 1e-9);
        assertTrue(handle.changesWidth());
        assertFalse(handle.changesHeight());
    }

    @Test
    void wantedHeightFollowsTheBottomEdge() {
        assertTrue(handle.begin(296, 166, 100, 50, 200, 120));
        assertEquals(ResizeHandle.Edge.BOTH, handle.edge());
        assertEquals(120, handle.wantedHeight(166), 1e-9);
        assertEquals(100, handle.wantedHeight(156), 1e-9);
        assertTrue(handle.changesWidth());
        assertTrue(handle.changesHeight());
    }

    @Test
    void beginMissesAndEndStops() {
        assertFalse(handle.begin(200, 100, 100, 50, 200, 120));
        assertFalse(handle.dragging());
        assertTrue(handle.begin(200, 168, 100, 50, 200, 120));
        assertEquals(ResizeHandle.Edge.HEIGHT, handle.hover(0, 0, 100, 50, 200, 120));
        handle.end();
        assertFalse(handle.dragging());
        assertNull(handle.edge());
        assertNull(handle.hover(0, 0, 100, 50, 200, 120));
    }
}
