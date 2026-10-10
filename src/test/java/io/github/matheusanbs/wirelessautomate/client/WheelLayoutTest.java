package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.matheusanbs.wirelessautomate.client.WheelLayout.Hit;
import io.github.matheusanbs.wirelessautomate.client.WheelLayout.Ring;
import org.junit.jupiter.api.Test;

class WheelLayoutTest {
    private static final WheelLayout WHEEL = WheelLayout.forRadius(200);

    @Test
    void centerAndOutsideChooseNothing() {
        assertNull(WHEEL.hit(0, 0, 3, 6));
        assertNull(WHEEL.hit(0, -85, 3, 6));
        assertNull(WHEEL.hit(0, -213, 3, 6));
        assertNull(WHEEL.hit(170, 170, 3, 6));
    }

    @Test
    void topIsSliceZeroOnBothRings() {
        assertEquals(new Hit(Ring.INNER, 0), WHEEL.hit(0, -100, 3, 6));
        assertEquals(new Hit(Ring.OUTER, 0), WHEEL.hit(0, -170, 3, 6));
    }

    @Test
    void clockwiseFromTheTop() {
        // Seis fatias: a direita (90°) é o meio entre a 1 (60°) e a 2 (120°); 60° em ponto é a 1.
        assertEquals(new Hit(Ring.OUTER, 1), WHEEL.hit(170 * Math.sin(Math.toRadians(60)),
                -170 * Math.cos(Math.toRadians(60)), 3, 6));
        // Quatro fatias: direita, baixo e esquerda.
        assertEquals(new Hit(Ring.OUTER, 1), WHEEL.hit(170, 0, 3, 4));
        assertEquals(new Hit(Ring.OUTER, 2), WHEEL.hit(0, 170, 3, 4));
        assertEquals(new Hit(Ring.OUTER, 3), WHEEL.hit(-170, 0, 3, 4));
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
        // anel de dentro até 140, de fora a partir de 146: o limite é 143
        assertEquals(Ring.INNER, WHEEL.hit(0, -142, 3, 6).ring());
        assertEquals(Ring.OUTER, WHEEL.hit(0, -144, 3, 6).ring());
    }

    @Test
    void everyCountCoversTheCircle() {
        for (int count : new int[] {3, 4, 5, 6}) {
            for (int i = 0; i < count; i++) {
                double middle = WheelLayout.sliceMiddle(i, count);
                Hit hit = WHEEL.hit(170 * Math.sin(middle), -170 * Math.cos(middle), 3, count);
                assertEquals(new Hit(Ring.OUTER, i), hit, count + " fatias, " + i);
            }
        }
    }

    @Test
    void scalesWithTheRadius() {
        WheelLayout small = WheelLayout.forRadius(100);
        assertEquals(43, small.innerRadius(), 1e-9);
        assertEquals(3, small.gap(), 1e-9);
        assertEquals(40, small.centerRadius(), 1e-9);
        assertEquals(70, small.innerRingEnd(), 1e-9);
        assertEquals(73, small.outerRingStart(), 1e-9);
        assertNull(small.hit(0, -42, 3, 6));
        assertEquals(new Hit(Ring.INNER, 0), small.hit(0, -56, 3, 6));
        assertEquals(new Hit(Ring.OUTER, 0), small.hit(0, -85, 3, 6));
    }

    @Test
    void emptyRingChoosesNothing() {
        assertNull(WHEEL.hit(0, -170, 3, 0));
    }

    @Test
    void contentSitsInTheMiddleOfTheRing() {
        WheelLayout wheel = WheelLayout.forRadius(100);
        assertEquals(56.5, wheel.ringMiddle(Ring.INNER), 1e-9);
        assertEquals(86.5, wheel.ringMiddle(Ring.OUTER), 1e-9);
        // seis fatias no raio 86,5: o arco de 60° menos o vão 3 e 2 de margem de cada lado
        assertEquals(86.5 * Math.PI / 3 - 3 - 4, wheel.labelArc(86.5, 6, 2), 1e-9);
        assertEquals(0, wheel.labelArc(56.5, 400, 2), 1e-9);
    }

    @Test
    void bottomHalfIsFlipped() {
        assertEquals(false, WheelLayout.flipped(WheelLayout.sliceMiddle(0, 3)));
        assertEquals(true, WheelLayout.flipped(WheelLayout.sliceMiddle(1, 3)));
        assertEquals(true, WheelLayout.flipped(WheelLayout.sliceMiddle(2, 3)));
        assertEquals(false, WheelLayout.flipped(WheelLayout.sliceMiddle(1, 6)));
        assertEquals(true, WheelLayout.flipped(WheelLayout.sliceMiddle(3, 6)));
        // nas laterais exatas (90° e 270°) não vira
        assertEquals(false, WheelLayout.flipped(WheelLayout.sliceMiddle(1, 4)));
    }
}
