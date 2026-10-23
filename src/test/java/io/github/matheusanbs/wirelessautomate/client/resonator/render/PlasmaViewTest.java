package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlasmaViewTest {
    private static final float EPS = 1e-5f;

    @Test
    void identityRotationGivesIdentityMatrix() {
        float[] m = new float[9];
        PlasmaView.inverseRotation(0, 0, 0, 1, m);
        float[] expected = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        for (int i = 0; i < 9; i++) {
            assertEquals(expected[i], m[i], EPS, "elemento " + i);
        }
    }

    @Test
    void quarterTurnAroundYSendsWorldXToLocalZ() {
        // R gira +X para -Z (90° em Y); o inverso leva o +X do mundo para o +Z da câmera
        float s = (float) Math.sqrt(0.5);
        float[] m = new float[9];
        PlasmaView.inverseRotation(0, s, 0, s, m);
        // a primeira coluna (coluna-maior) é a imagem de (1, 0, 0)
        assertEquals(0f, m[0], EPS);
        assertEquals(0f, m[1], EPS);
        assertEquals(1f, m[2], EPS);
        // a terceira coluna é a imagem de (0, 0, 1): vira -X
        assertEquals(-1f, m[6], EPS);
        assertEquals(0f, m[7], EPS);
        assertEquals(0f, m[8], EPS);
    }
}
