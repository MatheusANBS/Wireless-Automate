package io.github.matheusanbs.wirelessautomate.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.guide.doc.SceneCamera;
import org.junit.jupiter.api.Test;

class SceneViewTest {
    private static SceneView view() {
        return new SceneView(new SceneCamera(200, 30), 4);
    }

    @Test
    void comecaNaVistaDaPagina() {
        SceneView v = view();
        assertEquals(new SceneView.State(200, 30, 1), v.state());
        assertTrue(v.isHome());
        assertEquals(1, v.scale(), 1e-9);
    }

    @Test
    void arrastoMudaYawEPitch() {
        SceneView v = view();
        int before = v.version();
        v.drag(40, 20);
        assertEquals(220, v.yaw(), 1e-9);
        assertEquals(40, v.pitch(), 1e-9);
        assertNotEquals(before, v.version());
        assertFalse(v.isHome());
    }

    @Test
    void yawDaAVoltaSemSair() {
        SceneView v = view();
        v.drag(400, 0);
        assertEquals(40, v.yaw(), 1e-9);
        v.drag(-200, 0);
        assertEquals(300, v.yaw(), 1e-9);
    }

    @Test
    void pitchPreso() {
        SceneView v = view();
        v.drag(0, 1000);
        assertEquals(SceneView.PITCH_MAX, v.pitch(), 1e-9);
        v.drag(0, -1000);
        assertEquals(SceneView.PITCH_MIN, v.pitch(), 1e-9);
    }

    @Test
    void zoomPresoESemMudancaSemVersaoNova() {
        SceneView v = view();
        v.scroll(1);
        assertEquals(1.1, v.zoom(), 1e-9);
        v.scroll(100);
        assertEquals(SceneView.ZOOM_MAX, v.zoom(), 1e-9);
        int at = v.version();
        v.scroll(1);
        assertEquals(at, v.version());
        v.scroll(-100);
        assertEquals(SceneView.ZOOM_MIN, v.zoom(), 1e-9);
    }

    @Test
    void resetVoltaAVistaDaPagina() {
        SceneView v = view();
        v.drag(33, -7);
        v.scroll(3);
        v.reset();
        assertTrue(v.isHome());
        assertEquals(new SceneView.State(200, 30, 1), v.state());
    }

    @Test
    void zoomDaPaginaViraEnquadramento() {
        assertEquals(1, SceneView.frame(4), 1e-9);
        assertEquals(0.875, SceneView.frame(3), 1e-9);
        assertEquals(0.625, SceneView.frame(1), 1e-9);
        assertEquals(1, SceneView.frame(8), 1e-9);
        assertEquals(0.875, new SceneView(SceneCamera.DEFAULT, 3).scale(), 1e-9);
    }
}
