package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EaseTest {
    @Test
    void asCurvasDoFilme() {
        assertEquals(0.0, Ease.eob(0, 1.4), 1e-12);
        assertEquals(1.0, Ease.eob(1, 1.4), 1e-12);
        // 1 + 2,4·(−0,125) + 1,4·0,25 = 1,05: passa de 1 no caminho
        assertEquals(1.05, Ease.eob(0.5, 1.4), 1e-12);
        assertEquals(0.5, Ease.eio(0.5), 1e-12);
        assertEquals(0.5, Ease.ss(0, 1, 0.5), 1e-12);
        assertEquals(0.5, Ease.seg(5, 0, 10), 1e-12);
        assertEquals(0.875, Ease.eo(0.5), 1e-12);
        assertEquals(15.0, Ease.lerp(10, 20, 0.5), 1e-12);
        assertEquals(0.0, Ease.cl(-3), 0);
        assertEquals(1.0, Ease.cl(3), 0);
    }

    @Test
    void wrapReduzAoIntervalo() {
        assertEquals(7 - 2 * Math.PI, Ease.wrap(7), 1e-6);
        assertEquals(0.0, Ease.wrap(2 * Math.PI * 1000), 1e-6);
        assertEquals(1.0, Ease.wrap(1.0 + 2 * Math.PI * 100000), 1e-4);
    }
}
