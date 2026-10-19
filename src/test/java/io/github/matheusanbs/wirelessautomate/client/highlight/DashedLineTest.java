package io.github.matheusanbs.wirelessautomate.client.highlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DashedLineTest {
    private static final float EPS = 1e-4f;

    @Test
    void samePointHasNoDash() {
        DashedLine line = new DashedLine();
        assertEquals(0, line.compute(1, 2, 3, 1, 2, 3));
        assertEquals(0, line.count());
    }

    @Test
    void shortLineUsesTheMinimumPeriod() {
        DashedLine line = new DashedLine();
        // 5 blocos: períodos de cerca de 0,8, ajustados para caber inteiros
        int n = line.compute(0, 0, 0, 5, 0, 0);
        assertEquals((int) Math.ceil(5 / DashedLine.MIN_PERIOD), n);
        float period = 5f / n;
        float[] p = line.points();
        assertEquals(0f, p[0], EPS);
        assertEquals(period * (float) DashedLine.DASH, p[3], EPS);
        assertEquals(period, p[6], EPS);
        // o último traço termina antes do fim
        assertTrue(p[(n - 1) * 6 + 3] <= 5f + EPS);
    }

    @Test
    void longLineNeverPassesTheMaximum() {
        DashedLine line = new DashedLine();
        assertEquals(DashedLine.MAX_DASHES, line.compute(0, 0, 0, 0, 0, 512));
        float[] p = line.points();
        double period = 512.0 / DashedLine.MAX_DASHES;
        assertEquals((float) period, p[6 + 2], 1e-3f);
        assertEquals((float) ((DashedLine.MAX_DASHES - 1) * period + period * DashedLine.DASH),
                p[(DashedLine.MAX_DASHES - 1) * 6 + 5], 1e-2f);
    }

    @Test
    void dashesFollowTheDirection() {
        DashedLine line = new DashedLine();
        int n = line.compute(1, 1, 1, -2, 5, 1);
        float[] p = line.points();
        for (int i = 0; i < n; i++) {
            int o = i * 6;
            // na reta de (1,1,1) a (-2,5,1): z fixo e 4·Δx = −3·Δy
            assertEquals(1f, p[o + 2], EPS);
            assertEquals(1f, p[o + 5], EPS);
            assertEquals(-3f * (p[o + 4] - 1f), 4f * (p[o + 3] - 1f), 1e-3f);
        }
    }

    @Test
    void reusesTheArray() {
        DashedLine line = new DashedLine();
        float[] first = line.points();
        line.compute(0, 0, 0, 10, 0, 0);
        line.compute(0, 0, 0, 1, 0, 0);
        assertSame(first, line.points());
        assertEquals(2, line.count());
    }
}
