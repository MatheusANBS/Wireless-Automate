package io.github.matheusanbs.wirelessautomate.client.highlight;

/**
 * Os traços de uma linha tracejada entre dois pontos, num array reaproveitado: cada traço são dois pontos
 * ({@code x0 y0 z0 x1 y1 z1}). O período começa em {@link #MIN_PERIOD} blocos e cresce numa linha longa, para
 * nunca passar de {@link #MAX_DASHES} traços (o desenho de 64 linhas até a distância de visão fica em O(64 × 48)
 * vértices). Os pontos são relativos à câmera, para caberem num {@code float} sem perder precisão longe da
 * origem. O primeiro traço começa no ponto {@code a} e o último termina antes de {@code b} ou nele. Lógica pura,
 * com JUnit.
 */
public final class DashedLine {
    public static final int MAX_DASHES = 48;
    /** O período (traço e vão) numa linha curta, em blocos. */
    public static final double MIN_PERIOD = 0.8;
    /** A parte do período que é traço. */
    public static final double DASH = 0.55;

    private final float[] points = new float[MAX_DASHES * 6];
    private int count;

    /** Monta os traços de {@code a} a {@code b} e devolve quantos são. */
    public int compute(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        count = 0;
        if (length < 1e-6) {
            return 0;
        }
        double period = Math.max(MIN_PERIOD, length / MAX_DASHES);
        int n = Math.min(MAX_DASHES, (int) Math.ceil(length / period));
        period = length / n;
        double dash = period * DASH;
        for (int i = 0; i < n; i++) {
            double t0 = i * period / length;
            double t1 = (i * period + dash) / length;
            int o = count * 6;
            points[o] = (float) (ax + dx * t0);
            points[o + 1] = (float) (ay + dy * t0);
            points[o + 2] = (float) (az + dz * t0);
            points[o + 3] = (float) (ax + dx * t1);
            points[o + 4] = (float) (ay + dy * t1);
            points[o + 5] = (float) (az + dz * t1);
            count++;
        }
        return count;
    }

    /** Os traços do último {@link #compute}, 6 floats cada; só os primeiros {@link #count} valem. */
    public float[] points() {
        return points;
    }

    public int count() {
        return count;
    }
}
