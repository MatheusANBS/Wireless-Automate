package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.matheusanbs.wirelessautomate.client.WheelLayout.Hit;
import io.github.matheusanbs.wirelessautomate.client.WheelLayout.Ring;
import org.junit.jupiter.api.Test;

class WheelLayoutTest {
    private static final WheelLayout WHEEL = WheelLayout.forRadius(182);

    @Test
    void centerAndOutsideChooseNothing() {
        assertNull(WHEEL.hit(0, 0, 3, 6));
        assertNull(WHEEL.hit(0, -45, 3, 6));
        assertNull(WHEEL.hit(0, -193, 3, 6));
        assertNull(WHEEL.hit(150, 150, 3, 6));
    }

    @Test
    void topIsSliceZeroOnBothRings() {
        assertEquals(new Hit(Ring.INNER, 0), WHEEL.hit(0, -80, 3, 6));
        assertEquals(new Hit(Ring.OUTER, 0), WHEEL.hit(0, -150, 3, 6));
    }

    @Test
    void clockwiseFromTheTop() {
        // Seis fatias: a direita (90°) é o meio entre a 1 (60°) e a 2 (120°); 60° em ponto é a 1.
        assertEquals(new Hit(Ring.OUTER, 1), WHEEL.hit(150 * Math.sin(Math.toRadians(60)),
                -150 * Math.cos(Math.toRadians(60)), 3, 6));
        // Quatro fatias: direita, baixo e esquerda.
        assertEquals(new Hit(Ring.OUTER, 1), WHEEL.hit(150, 0, 3, 4));
        assertEquals(new Hit(Ring.OUTER, 2), WHEEL.hit(0, 150, 3, 4));
        assertEquals(new Hit(Ring.OUTER, 3), WHEEL.hit(-150, 0, 3, 4));
    }

    @Test
    void sliceEdges() {
        // Três fatias de 120°: a 0 vai de -60° a 60°.
        assertEquals(0, WheelLayout.slice(Math.toRadians(59.9), 3));
        assertEquals(1, WheelLayout.slice(Math.toRadians(60.1), 3));
        assertEquals(0, WheelLayout.slice(Math.toRadians(300.1), 3));
        assertEquals(2, WheelLayout.slice(Math.toRadians(299.9), 3));
        assertEquals(Math.toRadians(-60), WheelLayout.sliceStart(0, 3), 1e-9);
        assertEquals(Math.toRadians(60), WheelLayout.sliceEnd(0, 3), 1e-9);
        assertEquals(Math.toRadians(120), WheelLayout.sliceMiddle(1, 3), 1e-9);
    }

    @Test
    void gapBetweenRingsSplitsInTheMiddle() {
        assertEquals(Ring.INNER, WHEEL.hit(0, -114, 3, 6).ring());
        assertEquals(Ring.OUTER, WHEEL.hit(0, -116, 3, 6).ring());
    }

    @Test
    void everyCountCoversTheCircle() {
        for (int count : new int[] {3, 4, 5, 6}) {
            for (int i = 0; i < count; i++) {
                double middle = WheelLayout.sliceMiddle(i, count);
                Hit hit = WHEEL.hit(150 * Math.sin(middle), -150 * Math.cos(middle), 3, count);
                assertEquals(new Hit(Ring.OUTER, i), hit, count + " fatias, " + i);
            }
        }
    }

    @Test
    void scalesWithTheRadius() {
        WheelLayout small = WheelLayout.forRadius(91);
        assertEquals(23, small.innerRadius(), 1e-9);
        assertNull(small.hit(0, -22, 3, 6));
        assertEquals(new Hit(Ring.INNER, 0), small.hit(0, -40, 3, 6));
        assertEquals(new Hit(Ring.OUTER, 0), small.hit(0, -80, 3, 6));
    }

    @Test
    void emptyRingChoosesNothing() {
        assertNull(WHEEL.hit(0, -150, 3, 0));
    }
}
