package io.github.matheusanbs.wirelessautomate.bench;

import java.util.Arrays;

/**
 * Amostras de tempo (uma por tick, em ns) com média, percentis e máximo. Lógica pura, testada por
 * JUnit. Cresce sozinha; {@link #clear} reaproveita o arranjo.
 */
public final class Samples {
    private long[] values;
    private int size;

    public Samples() {
        this(256);
    }

    public Samples(int capacity) {
        values = new long[Math.max(1, capacity)];
    }

    public void add(long value) {
        if (size == values.length) {
            values = Arrays.copyOf(values, values.length * 2);
        }
        values[size++] = value;
    }

    public void clear() {
        size = 0;
    }

    public int size() {
        return size;
    }

    public double mean() {
        if (size == 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < size; i++) {
            sum += values[i];
        }
        return sum / size;
    }

    public long max() {
        long max = 0;
        for (int i = 0; i < size; i++) {
            max = Math.max(max, values[i]);
        }
        return max;
    }

    /** Percentil pelo método do posto mais próximo; {@code p} entre 0 e 100. Vazio dá 0. */
    public long percentile(double p) {
        if (size == 0) {
            return 0;
        }
        long[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(p / 100.0 * size);
        return sorted[Math.min(size, Math.max(1, rank)) - 1];
    }

    /** Quantas amostras passam de {@code limit}. */
    public int countAbove(long limit) {
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (values[i] > limit) {
                count++;
            }
        }
        return count;
    }

    /** Média e desvio padrão amostral de uma série (por exemplo, a média de cada repetição). */
    public static double[] meanAndDeviation(double[] series) {
        int n = series.length;
        if (n == 0) {
            return new double[] {0, 0};
        }
        double mean = 0;
        for (double v : series) {
            mean += v;
        }
        mean /= n;
        if (n == 1) {
            return new double[] {mean, 0};
        }
        double squares = 0;
        for (double v : series) {
            squares += (v - mean) * (v - mean);
        }
        return new double[] {mean, Math.sqrt(squares / (n - 1))};
    }
}
