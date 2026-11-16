package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArmilarGrainsTest {
    private static int vivos(ArmilarGrains g) {
        int n = 0;
        for (int i = 0; i < ArmilarGrains.MAX; i++) {
            if (g.alive(i)) {
                n++;
            }
        }
        return n;
    }

    @Test
    void semAlvoNaoNasce() {
        ArmilarGrains g = new ArmilarGrains();
        for (int t = 0; t < 100; t++) {
            g.tick(-1, 3);
            assertEquals(0, vivos(g));
        }
    }

    @Test
    void nuncaMaisDeQuatro() {
        ArmilarGrains g = new ArmilarGrains();
        int max = 0;
        for (int t = 0; t < 200; t++) {
            g.tick(2, 3);
            assertTrue(vivos(g) <= ArmilarGrains.MAX);
            max = Math.max(max, vivos(g));
        }
        assertEquals(4, max);
    }

    @Test
    void cadaGraoVive33Ticks() {
        ArmilarGrains g = new ArmilarGrains();
        int[] nasceu = new int[ArmilarGrains.MAX];
        boolean[] antes = new boolean[ArmilarGrains.MAX];
        int medidos = 0;
        for (int t = 1; t <= 200; t++) {
            g.tick(2, 3);
            for (int i = 0; i < ArmilarGrains.MAX; i++) {
                if (g.alive(i) && !antes[i]) {
                    nasceu[i] = t;
                } else if (!g.alive(i) && antes[i]) {
                    // o grão nasce e já anda no tick do nascimento: são 32 quadros desenhados (vivo ao fim de 32 ticks), e a vida de 33 ticks conta de t0 a t_morte inclusive
                    assertEquals(ArmilarGrains.TICKS, t - nasceu[i] + 1, "grão " + i);
                    medidos++;
                }
                antes[i] = g.alive(i);
            }
        }
        assertTrue(medidos >= 4);
    }

    @Test
    void camaraQueSomeMataOGrao() {
        ArmilarGrains g = new ArmilarGrains();
        int t = 0;
        while (vivos(g) == 0) {
            g.tick(2, 3);
            t++;
            assertTrue(t < 100);
        }
        assertTrue(g.alive(0));
        assertEquals(2, g.chamber(0));
        g.tick(2, 2);
        assertFalse(g.alive(0));
    }
}
