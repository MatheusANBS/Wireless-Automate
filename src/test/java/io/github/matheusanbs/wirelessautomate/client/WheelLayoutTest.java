package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WheelLayoutTest {
    private static final WheelLayout WHEEL = WheelLayout.forRadius(200);
    private static final int NONE = WheelLayout.NONE;

    @Test
    void centerAndOutsideChooseNothing() {
        assertEquals(NONE, WHEEL.hit(0, 0, 6));
        assertEquals(NONE, WHEEL.hit(0, -95, 6));
        assertEquals(NONE, WHEEL.hit(0, -213, 6));
        assertEquals(NONE, WHEEL.hit(170, 170, 6));
    }

    @Test
    void topIsSliceZero() {
        assertEquals(0, WHEEL.hit(0, -170, 6));
    }

    @Test
    void clockwiseFromTheTop() {
        // Seis fatias: a direita (90°) é o meio entre a 1 (60°) e a 2 (120°); 60° em ponto é a 1.
        assertEquals(1, WHEEL.hit(170 * Math.sin(Math.toRadians(60)), -170 * Math.cos(Math.toRadians(60)), 6));
        // Quatro fatias: direita, baixo e esquerda.
        assertEquals(1, WHEEL.hit(170, 0, 4));
        assertEquals(2, WHEEL.hit(0, 170, 4));
        assertEquals(3, WHEEL.hit(-170, 0, 4));
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
    void everyCountCoversTheCircle() {
        for (int count : new int[] {3, 4, 5, 6}) {
            for (int i = 0; i < count; i++) {
                double middle = WheelLayout.sliceMiddle(i, count);
                assertEquals(i, WHEEL.hit(170 * Math.sin(middle), -170 * Math.cos(middle), count), count + " fatias, " + i);
            }
        }
    }

    @Test
    void scalesWithTheRadius() {
        WheelLayout small = WheelLayout.forRadius(100);
        assertEquals(50, small.innerRadius(), 1e-9);
        assertEquals(3, small.gap(), 1e-9);
        assertEquals(47, small.centerRadius(), 1e-9);
        assertEquals(NONE, small.hit(0, -48, 6));
        assertEquals(0, small.hit(0, -56, 6));
    }

    @Test
    void emptyRingChoosesNothing() {
        assertEquals(NONE, WHEEL.hit(0, -170, 0));
    }

    @Test
    void contentSitsInTheMiddleOfTheRing() {
        WheelLayout wheel = WheelLayout.forRadius(100);
        assertEquals(75, wheel.ringMiddle(), 1e-9);
        // seis fatias no raio 75: o arco de 60° menos o vão 3 e 2 de margem de cada lado
        assertEquals(75 * Math.PI / 3 - 3 - 4, wheel.labelArc(75, 6, 2), 1e-9);
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
