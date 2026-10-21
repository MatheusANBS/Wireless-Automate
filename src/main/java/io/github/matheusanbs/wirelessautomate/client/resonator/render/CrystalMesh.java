package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * A malha do cristal da câmara, a "Ponta dupla": uma bipirâmide de 6 lados, 12 triângulos, em unidades do cristal
 * (antes da escala do desenho). O anel tem raio e altura um pouco desiguais por vértice, para as facetas não saírem
 * perfeitas. Montada uma vez; o desenho só lê as tabelas.
 *
 * <p>Por vértice: a posição, os cantos da faceta (0,0), (1,0) e (0,1), que o shader usa para acender as arestas, e
 * a altura relativa no cristal, de 0 na ponta de baixo a 1 na de cima. Por triângulo: a normal plana, para fora; os
 * vértices vêm em sentido anti-horário vistos de fora.
 */
final class CrystalMesh {
    static final int SIDES = 6;
    static final int TRIANGLES = 2 * SIDES;
    static final int VERTICES = 3 * TRIANGLES;
    static final float TOP = 0.58f;
    static final float BOTTOM = -0.46f;
    /** Um ponto dentro do cristal, para a normal sair para fora. */
    private static final float INSIDE_Y = 0.05f;
    /** x, y, z de cada vértice. */
    private static final float[] POSITIONS = new float[VERTICES * 3];
    /** u, v de cada vértice. */
    private static final float[] CORNERS = new float[VERTICES * 2];
    /** A altura relativa de cada vértice. */
    private static final float[] HEIGHTS = new float[VERTICES];
    /** nx, ny, nz de cada triângulo. */
    private static final float[] NORMALS = new float[TRIANGLES * 3];

    static {
        float[] ring = new float[SIDES * 3];
        for (int i = 0; i < SIDES; i++) {
            double a = 2 * Math.PI * i / SIDES;
            double r = 0.3 * (0.9 + 0.2 * jitter(i, 1));
            ring[i * 3] = (float) (Math.cos(a) * r);
            ring[i * 3 + 1] = (float) (0.04 + 0.05 * (jitter(i, 2) - 0.5));
            ring[i * 3 + 2] = (float) (Math.sin(a) * r);
        }
        float[] top = {0, TOP, 0};
        float[] bottom = {0, BOTTOM, 0};
        for (int i = 0; i < SIDES; i++) {
            int j = (i + 1) % SIDES;
            triangle(2 * i, ring, i, ring, j, top, 0);
            triangle(2 * i + 1, ring, j, ring, i, bottom, 0);
        }
    }

    private CrystalMesh() {
    }

    /** O desvio fixo do vértice {@code i} do anel, de 0 a 1, o mesmo do protótipo. */
    static double jitter(int i, int k) {
        double s = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453;
        return s - Math.floor(s);
    }

    static float x(int vertex) {
        return POSITIONS[vertex * 3];
    }

    static float y(int vertex) {
        return POSITIONS[vertex * 3 + 1];
    }

    static float z(int vertex) {
        return POSITIONS[vertex * 3 + 2];
    }

    static float u(int vertex) {
        return CORNERS[vertex * 2];
    }

    static float v(int vertex) {
        return CORNERS[vertex * 2 + 1];
    }

    static float height(int vertex) {
        return HEIGHTS[vertex];
    }

    static float normalX(int triangle) {
        return NORMALS[triangle * 3];
    }

    static float normalY(int triangle) {
        return NORMALS[triangle * 3 + 1];
    }

    static float normalZ(int triangle) {
        return NORMALS[triangle * 3 + 2];
    }

    /** Grava o triângulo {@code t} de {@code a}, {@code b}, {@code c}, trocando a ordem se a normal sair para dentro. */
    private static void triangle(int t, float[] pa, int ia, float[] pb, int ib, float[] pc, int ic) {
        float ax = pa[ia * 3];
        float ay = pa[ia * 3 + 1];
        float az = pa[ia * 3 + 2];
        float bx = pb[ib * 3];
        float by = pb[ib * 3 + 1];
        float bz = pb[ib * 3 + 2];
        float cx = pc[ic * 3];
        float cy = pc[ic * 3 + 1];
        float cz = pc[ic * 3 + 2];
        float[] n = cross(bx - ax, by - ay, bz - az, cx - ax, cy - ay, cz - az);
        float mx = (ax + bx + cx) / 3;
        float my = (ay + by + cy) / 3 - INSIDE_Y;
        float mz = (az + bz + cz) / 3;
        if (n[0] * mx + n[1] * my + n[2] * mz < 0) {
            float sx = bx;
            float sy = by;
            float sz = bz;
            bx = cx;
            by = cy;
            bz = cz;
            cx = sx;
            cy = sy;
            cz = sz;
            n[0] = -n[0];
            n[1] = -n[1];
            n[2] = -n[2];
        }
        NORMALS[t * 3] = n[0];
        NORMALS[t * 3 + 1] = n[1];
        NORMALS[t * 3 + 2] = n[2];
        vertex(3 * t, ax, ay, az, 0, 0);
        vertex(3 * t + 1, bx, by, bz, 1, 0);
        vertex(3 * t + 2, cx, cy, cz, 0, 1);
    }

    private static void vertex(int vertex, float x, float y, float z, float u, float v) {
        POSITIONS[vertex * 3] = x;
        POSITIONS[vertex * 3 + 1] = y;
        POSITIONS[vertex * 3 + 2] = z;
        CORNERS[vertex * 2] = u;
        CORNERS[vertex * 2 + 1] = v;
        HEIGHTS[vertex] = (y - BOTTOM) / (TOP - BOTTOM);
    }

    private static float[] cross(float ax, float ay, float az, float bx, float by, float bz) {
        float x = ay * bz - az * by;
        float y = az * bx - ax * bz;
        float z = ax * by - ay * bx;
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / length, y / length, z / length};
    }
}
