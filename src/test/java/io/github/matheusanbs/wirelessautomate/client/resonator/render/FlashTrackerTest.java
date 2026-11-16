package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import org.junit.jupiter.api.Test;

class FlashTrackerTest {
    private static ChamberView view(byte[] c, long start) {
        return ArmilarRechargeTest.view(c, start, 60, 1);
    }

    @Test
    void terminaPeloProgresso() {
        byte[] c = {3};
        ChamberView v = view(c, 100);
        FlashTracker f = new FlashTracker();
        f.onView(v, c, 100);
        for (long t = 101; t <= 159; t++) {
            f.tick(v, c, t);
            assertEquals(-1f, f.flash(0, t, 0), "tick " + t);
        }
        f.tick(v, c, 160);
        assertEquals(0f, f.flash(0, 160, 0), 1e-6);
        for (long t = 161; t <= 173; t++) {
            f.tick(v, c, t);
        }
        assertEquals(0.5f, f.flash(0, 167, 0), 1e-6);
        f.tick(v, c, 174);
        assertEquals(-1f, f.flash(0, 174, 0));
    }

    @Test
    void servidorTerminaAntes() {
        byte[] c = {3};
        ChamberView v = view(c, 100);
        FlashTracker f = new FlashTracker();
        f.onView(v, c, 100);
        for (long t = 101; t <= 139; t++) {
            f.tick(v, c, t);
        }
        byte[] viva = {1};
        ChamberView nova = view(viva, -1);
        f.onView(nova, viva, 140);
        assertEquals(0f, f.flash(0, 140, 0), 1e-6);
        for (long t = 140; t <= 160; t++) {
            f.tick(nova, viva, t);
        }
        // o tick de 160 não dispara outro: o clarão de 140 acabou em 154 e nada o reacendeu
        assertEquals(-1f, f.flash(0, 160, 0));
    }

    @Test
    void esvaziadaNaoBrilha() {
        byte[] c = {3};
        FlashTracker f = new FlashTracker();
        f.onView(view(c, 100), c, 100);
        byte[] vazia = {0};
        ChamberView nova = view(vazia, 100);
        f.onView(nova, vazia, 120);
        for (long t = 120; t <= 170; t++) {
            f.tick(nova, vazia, t);
            assertEquals(-1f, f.flash(0, t, 0));
        }
    }
}
