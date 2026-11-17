package io.github.matheusanbs.wirelessautomate.client.fx;

/** A amostragem das fitas (Catmull-Rom e bezier), pura. Os pontos e os passos na mesma unidade (px ou blocos). */
public final class FxRibbon {
    public static final int MAX = 256;

    /** Os pontos, a meia largura e o alfa de cada amostra; o chamador preenche {@code w} e {@code a} depois da amostragem. */
    public static final class Buffer {
        public final float[] pts = new float[MAX * 3];
        public final float[] w = new float[MAX];
        public final float[] a = new float[MAX];
    }

    private FxRibbon() {
    }

    /**
     * O {@code spline} do filme: curva de Catmull-Rom pelos {@code count} controles (x, y, z em {@code ctl}), com uma
     * amostra a cada {@code step} no máximo. Com menos de 3 controles devolve os próprios controles. Devolve o número de
     * amostras (as primeiras de {@code out.pts}), no máximo {@link #MAX}.
     */
    public static int catmullRom(float[] ctl, int count, float step, Buffer out) {
        if (count < 3) {
            for (int i = 0; i < count * 3; i++) {
                out.pts[i] = ctl[i];
            }
            return count;
        }
        int n = 0;
        for (int i = 0; i < count - 1; i++) {
            // a curva é mais longa que a corda: a margem de 35% mantém o passo entre amostras abaixo de {@code step}
            float len = dist(ctl, i, i + 1);
            int steps = Math.max(1, (int) Math.ceil(len * 1.35f / step));
            for (int k = 0; k < steps && n < MAX - 1; k++) {
                float t = k / (float) steps;
                for (int j = 0; j < 3; j++) {
                    float p0 = i == 0 ? 2 * ctl[j] - ctl[3 + j] : ctl[(i - 1) * 3 + j];
                    float p1 = ctl[i * 3 + j];
                    float p2 = ctl[(i + 1) * 3 + j];
                    float p3 = i + 2 >= count ? 2 * ctl[(count - 1) * 3 + j] - ctl[(count - 2) * 3 + j]
                            : ctl[(i + 2) * 3 + j];
                    float t2 = t * t;
                    float t3 = t2 * t;
                    out.pts[n * 3 + j] = 0.5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                            + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
                }
                n++;
            }
        }
        for (int j = 0; j < 3; j++) {
            out.pts[n * 3 + j] = ctl[(count - 1) * 3 + j];
        }
        return n + 1;
    }

    /** O {@code bez} do filme: bezier cúbica por 4 pontos ({@code p}, 12 floats), com {@code steps + 1} amostras. */
    public static int bezier(float[] p, int steps, Buffer out) {
        // steps + 1 amostras têm de caber no buffer
        steps = Math.min(steps, MAX - 1);
        for (int i = 0; i <= steps; i++) {
            float f = i / (float) steps;
            float g = 1 - f;
            for (int j = 0; j < 3; j++) {
                out.pts[i * 3 + j] = g * g * g * p[j] + 3 * g * g * f * p[3 + j] + 3 * g * f * f * p[6 + j]
                        + f * f * f * p[9 + j];
            }
        }
        return steps + 1;
    }

    private static float dist(float[] c, int i, int j) {
        float dx = c[i * 3] - c[j * 3];
        float dy = c[i * 3 + 1] - c[j * 3 + 1];
        float dz = c[i * 3 + 2] - c[j * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
