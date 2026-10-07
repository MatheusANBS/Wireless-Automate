package io.github.matheusanbs.wirelessautomate.bench;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SamplesTest {
    @Test
    void emptyIsZero() {
        Samples samples = new Samples();
        assertEquals(0, samples.mean());
        assertEquals(0, samples.max());
        assertEquals(0, samples.percentile(99));
    }

    @Test
    void meanMaxAndPercentiles() {
        Samples samples = new Samples(2);
        for (int i = 1; i <= 100; i++) {
            samples.add(i);
        }
        assertEquals(100, samples.size());
        assertEquals(50.5, samples.mean(), 1e-9);
        assertEquals(100, samples.max());
        assertEquals(50, samples.percentile(50));
        assertEquals(99, samples.percentile(99));
        assertEquals(1, samples.percentile(0));
        assertEquals(100, samples.percentile(100));
        assertEquals(10, samples.countAbove(90));
    }

    @Test
    void clearReuses() {
        Samples samples = new Samples();
        samples.add(7);
        samples.clear();
        samples.add(3);
        assertEquals(1, samples.size());
        assertEquals(3, samples.max());
    }

    @Test
    void meanAndDeviation() {
        assertArrayEquals(new double[] {0, 0}, Samples.meanAndDeviation(new double[0]));
        assertArrayEquals(new double[] {4, 0}, Samples.meanAndDeviation(new double[] {4}));
        double[] result = Samples.meanAndDeviation(new double[] {2, 4, 4, 4, 5, 5, 7, 9});
        assertEquals(5, result[0], 1e-9);
        assertEquals(2.138, result[1], 1e-3);
    }
}
