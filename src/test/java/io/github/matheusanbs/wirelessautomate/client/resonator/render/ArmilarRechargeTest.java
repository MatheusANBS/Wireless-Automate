package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import org.junit.jupiter.api.Test;

class ArmilarRechargeTest {
    private static final byte[] CH = {1, 3, 4, 5, 3};

    static ChamberView view(byte[] chambers, long start, int ticks, int rechargers) {
        return new ChamberView(chambers, new byte[0], 0, false, start, ticks, rechargers, 0, new int[3], new byte[0],
                false);
    }

    @Test
    void postos() {
        int[] esperado = {-1, 0, 1, -1, 2};
        for (int i = 0; i < CH.length; i++) {
            assertEquals(esperado[i], ArmilarRecharge.rank(i, CH), "câmara " + i);
        }
    }

    @Test
    void quemRecarrega() {
        ChamberView v = view(CH, 100, 60, 2);
        boolean[] esperado = {false, true, true, false, false};
        for (int i = 0; i < CH.length; i++) {
            assertEquals(esperado[i], ArmilarRecharge.isRecharging(i, CH, v), "câmara " + i);
        }
        assertEquals(1, ArmilarRecharge.firstRecharging(CH, v));
        ChamberView sem = view(CH, -1, 60, 2);
        for (int i = 0; i < CH.length; i++) {
            assertFalse(ArmilarRecharge.isRecharging(i, CH, sem));
        }
        assertEquals(-1, ArmilarRecharge.firstRecharging(CH, sem));
    }

    @Test
    void progresso() {
        ChamberView v = view(CH, 100, 60, 2);
        assertEquals(0.5, ArmilarRecharge.progress(1, CH, 130, v), 1e-12);
        assertEquals(0, ArmilarRecharge.progress(1, CH, 90, v), 1e-12);
        assertEquals(1, ArmilarRecharge.progress(1, CH, 500, v), 1e-12);
        assertEquals(0, ArmilarRecharge.progress(0, CH, 130, v), 1e-12);
        assertEquals(0, ArmilarRecharge.progress(4, CH, 130, v), 1e-12);
        assertTrue(ArmilarRecharge.rechargeable((byte) 4));
        assertFalse(ArmilarRecharge.rechargeable((byte) 5));
    }

    @Test
    void queda() {
        assertEquals(1, ArmilarRecharge.fall(0), 1e-12);
        assertEquals(0, ArmilarRecharge.fall(1), 1e-12);
        assertEquals(0.5, ArmilarRecharge.fall(0.5), 1e-12);
    }
}
