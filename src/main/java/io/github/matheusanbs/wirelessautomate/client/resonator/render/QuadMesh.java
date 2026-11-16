package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import java.util.Arrays;

/**
 * Uma malha de quads em px, com uv (0 a 1), normal e tom por quad. É o {@code quad} e o {@code quadUV} do filme
 * ({@code shared.js}) em listas de {@code float}. Lógica pura; montada uma vez e só lida depois.
 */
public final class QuadMesh {
    private final int texW;
    private final int texH;
    private float[] pos = new float[12 * 64];
    private float[] uv = new float[8 * 64];
    private float[] nrm = new float[3 * 64];
    private float[] tones = new float[64];
    private int count;

    public QuadMesh(int texW, int texH) {
        this.texW = texW;
        this.texH = texH;
    }

    public int quads() {
        return count;
    }

    public int texW() {
        return texW;
    }

    public int texH() {
        return texH;
    }

    public float x(int q, int v) {
        return pos[q * 12 + v * 3];
    }

    public float y(int q, int v) {
        return pos[q * 12 + v * 3 + 1];
    }

    public float z(int q, int v) {
        return pos[q * 12 + v * 3 + 2];
    }

    public float u(int q, int v) {
        return uv[q * 8 + v * 2];
    }

    public float v(int q, int v) {
        return uv[q * 8 + v * 2 + 1];
    }

    public float nx(int q) {
        return nrm[q * 3];
    }

    public float ny(int q) {
        return nrm[q * 3 + 1];
    }

    public float nz(int q) {
        return nrm[q * 3 + 2];
    }

    public float tone(int q) {
        return tones[q];
    }

    /** O {@code quad} do filme: a normal é −normalize(cross(p1 − p0, p3 − p0)); o uv é (u0,v0) (u1,v0) (u1,v1) (u0,v1) em texels. */
    void add(double[][] p, double u0, double v0, double u1, double v1, double tone) {
        double[] n = normal(p);
        put(p, u0, v0, u1, v1, new double[] {-n[0], -n[1], -n[2]}, tone);
    }

    /**
     * O {@code quadUV} do filme: a normal é a do plano, virada para o lado de {@code want}. O uv é o mesmo mapeamento
     * de {@link #add}: no filme o quad invertido leva o uv junto, então cada canto original mantém o seu uv.
     */
    void addToward(double[][] p, double u0, double v0, double u1, double v1, double[] want, double tone) {
        double[] n = normal(p);
        double side = n[0] * want[0] + n[1] * want[1] + n[2] * want[2];
        put(p, u0, v0, u1, v1, side > 0 ? n : new double[] {-n[0], -n[1], -n[2]}, tone);
    }

    private static double[] normal(double[][] p) {
        double ax = p[1][0] - p[0][0];
        double ay = p[1][1] - p[0][1];
        double az = p[1][2] - p[0][2];
        double bx = p[3][0] - p[0][0];
        double by = p[3][1] - p[0][1];
        double bz = p[3][2] - p[0][2];
        double cx = ay * bz - az * by;
        double cy = az * bx - ax * bz;
        double cz = ax * by - ay * bx;
        double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len == 0) {
            return new double[] {0, 1, 0};
        }
        return new double[] {cx / len, cy / len, cz / len};
    }

    private void put(double[][] p, double u0, double v0, double u1, double v1, double[] n, double tone) {
        if (count == tones.length) {
            int cap = count * 2;
            pos = Arrays.copyOf(pos, cap * 12);
            uv = Arrays.copyOf(uv, cap * 8);
            nrm = Arrays.copyOf(nrm, cap * 3);
            tones = Arrays.copyOf(tones, cap);
        }
        double[][] t = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (int v = 0; v < 4; v++) {
            pos[count * 12 + v * 3] = (float) p[v][0];
            pos[count * 12 + v * 3 + 1] = (float) p[v][1];
            pos[count * 12 + v * 3 + 2] = (float) p[v][2];
            uv[count * 8 + v * 2] = (float) (t[v][0] / texW);
            uv[count * 8 + v * 2 + 1] = (float) (t[v][1] / texH);
        }
        nrm[count * 3] = (float) n[0];
        nrm[count * 3 + 1] = (float) n[1];
        nrm[count * 3 + 2] = (float) n[2];
        tones[count] = (float) tone;
        count++;
    }
}
