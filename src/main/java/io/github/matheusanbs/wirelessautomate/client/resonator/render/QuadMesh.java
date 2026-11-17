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

    /**
     * A parte da malha entre os planos horizontais {@code y0} e {@code y1} (inclusive): Sutherland-Hodgman em cada quad,
     * uma vez para {@code y >= y0} e outra para {@code y <= y1}. Posição e uv interpolam linearmente; a normal e o tom
     * são os do quad de origem. O polígono vira 0, 1 ou 2 quads (vértice repetido onde faltar). Só na montagem.
     */
    public QuadMesh clipY(double y0, double y1) {
        QuadMesh out = new QuadMesh(texW, texH);
        for (int q = 0; q < count; q++) {
            double[][] poly = new double[4][5];
            for (int v = 0; v < 4; v++) {
                poly[v] = new double[] {x(q, v), y(q, v), z(q, v), u(q, v), v(q, v)};
            }
            double[][] cut = clipPlane(clipPlane(poly, y0, true), y1, false);
            if (cut.length < 3) {
                continue;
            }
            out.putRaw(cut[0], cut[1], cut[2], cut.length > 3 ? cut[3] : cut[2], q, this);
            if (cut.length > 4) {
                out.putRaw(cut[0], cut[3], cut[4], cut.length > 5 ? cut[5] : cut[4], q, this);
            }
        }
        return out;
    }

    /** Um passo do Sutherland-Hodgman: fica o lado {@code y >= plane} ({@code keepAbove}) ou {@code y <= plane}. */
    private static double[][] clipPlane(double[][] poly, double plane, boolean keepAbove) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < poly.length; i++) {
            double[] a = poly[i];
            double[] b = poly[(i + 1) % poly.length];
            boolean ina = keepAbove ? a[1] >= plane : a[1] <= plane;
            boolean inb = keepAbove ? b[1] >= plane : b[1] <= plane;
            if (ina) {
                out.add(a);
            }
            if (ina != inb) {
                double k = (plane - a[1]) / (b[1] - a[1]);
                double[] m = new double[5];
                for (int c = 0; c < 5; c++) {
                    m[c] = a[c] + (b[c] - a[c]) * k;
                }
                m[1] = plane;
                out.add(m);
            }
        }
        return out.toArray(new double[0][]);
    }

    /** Põe um quad de vértices {@code {x, y, z, u, v}} (uv já em 0 a 1) com a normal e o tom do quad {@code src} de {@code from}. */
    private void putRaw(double[] a, double[] b, double[] c, double[] d, int src, QuadMesh from) {
        if (count == tones.length) {
            int cap = count * 2;
            pos = Arrays.copyOf(pos, cap * 12);
            uv = Arrays.copyOf(uv, cap * 8);
            nrm = Arrays.copyOf(nrm, cap * 3);
            tones = Arrays.copyOf(tones, cap);
        }
        double[][] vs = {a, b, c, d};
        for (int v = 0; v < 4; v++) {
            pos[count * 12 + v * 3] = (float) vs[v][0];
            pos[count * 12 + v * 3 + 1] = (float) vs[v][1];
            pos[count * 12 + v * 3 + 2] = (float) vs[v][2];
            uv[count * 8 + v * 2] = (float) vs[v][3];
            uv[count * 8 + v * 2 + 1] = (float) vs[v][4];
        }
        nrm[count * 3] = from.nx(src);
        nrm[count * 3 + 1] = from.ny(src);
        nrm[count * 3 + 2] = from.nz(src);
        tones[count] = from.tone(src);
        count++;
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
