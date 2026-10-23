package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * A malha do cristal da câmara, um prisma de quartzo de 8 lados com as pontas chanfradas: 32 triângulos, em unidades
 * do cristal (antes da escala do desenho). Dois anéis de 8 vértices formam o corpo (o de cima torcido ~12° em relação
 * ao de baixo, então as facetas laterais não são retângulos perfeitos) e uma pirâmide fecha cada ponta. O raio e a
 * altura de cada vértice têm um desvio fixo, para o cristal ser irregular. Montada uma vez; o desenho só lê as tabelas.
 *
 * <p>Por vértice: a posição, os cantos da faceta (0,0), (1,0) e (0,1), que o shader usa para acender as arestas, e a
 * altura relativa no cristal, de 0 na ponta de baixo a 1 na de cima. Por triângulo: a normal plana, para fora, e o
 * canto cuja aresta oposta é a diagonal de uma face lateral (que não é aresta do cristal e não deve acender); os
 * vértices vêm em sentido anti-horário vistos de fora.
 */
final class CrystalMesh {
    static final int SIDES = 8;
    /** 16 do corpo (2 por face lateral) e 16 das pontas. */
    static final int TRIANGLES = 4 * SIDES;
    static final int VERTICES = 3 * TRIANGLES;
    static final float TOP = 0.58f;
    static final float BOTTOM = -0.46f;
    private static final double RADIUS = 0.28;
    private static final float LOWER_Y = -0.20f;
    private static final float UPPER_Y = 0.26f;
    private static final double TWIST = Math.toRadians(12);
    /** Um ponto dentro do cristal, para a normal sair para fora. */
    private static final float INSIDE_Y = 0.05f;
    /** O valor somado ao u de cada vértice, por canto escondido mais um, para o shader decodificar. */
    private static final float HIDDEN_STEP = 8f;
    /** x, y, z de cada vértice. */
    private static final float[] POSITIONS = new float[VERTICES * 3];
    /** u, v de cada vértice. */
    private static final float[] CORNERS = new float[VERTICES * 2];
    /** A altura relativa de cada vértice. */
    private static final float[] HEIGHTS = new float[VERTICES];
    /** nx, ny, nz de cada triângulo. */
    private static final float[] NORMALS = new float[TRIANGLES * 3];
    /** O canto (0 a 2) de cada triângulo cuja aresta oposta é a diagonal, ou −1. */
    private static final byte[] HIDDEN = new byte[TRIANGLES];

    static {
        float[] lower = new float[SIDES * 3];
        float[] upper = new float[SIDES * 3];
        for (int i = 0; i < SIDES; i++) {
            double a = 2 * Math.PI * i / SIDES;
            double r1 = RADIUS * (0.9 + 0.2 * jitter(i, 1));
            double r2 = RADIUS * (0.9 + 0.2 * jitter(i, 3));
            lower[i * 3] = (float) (Math.cos(a) * r1);
            lower[i * 3 + 1] = (float) (LOWER_Y + 0.04 * (jitter(i, 2) - 0.5));
            lower[i * 3 + 2] = (float) (Math.sin(a) * r1);
            upper[i * 3] = (float) (Math.cos(a + TWIST) * r2);
            upper[i * 3 + 1] = (float) (UPPER_Y + 0.04 * (jitter(i, 4) - 0.5));
            upper[i * 3 + 2] = (float) (Math.sin(a + TWIST) * r2);
        }
        float[] top = {0, TOP, 0};
        float[] bottom = {0, BOTTOM, 0};
        int t = 0;
        for (int i = 0; i < SIDES; i++) {
            int j = (i + 1) % SIDES;
            // a face lateral i: li, lj, uj, ui; a diagonal li-uj não é aresta
            triangle(t++, lower, i, lower, j, upper, j, 1);
            triangle(t++, lower, i, upper, j, upper, i, 2);
            triangle(t++, upper, i, upper, j, top, 0, -1);
            triangle(t++, lower, j, lower, i, bottom, 0, -1);
        }
    }

    private CrystalMesh() {
    }

    /** O desvio fixo do vértice {@code i} do anel, de 0 a 1. */
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

    /** O canto da faceta (0 ou 1), sem o sinal da diagonal. */
    static float u(int vertex) {
        return CORNERS[vertex * 2];
    }

    static float v(int vertex) {
        return CORNERS[vertex * 2 + 1];
    }

    /** O {@code u} que vai para o buffer: o canto mais 8 × (o canto escondido + 1), que o shader separa. */
    static float encodedU(int vertex) {
        return CORNERS[vertex * 2] + HIDDEN_STEP * (HIDDEN[vertex / 3] + 1);
    }

    /** O canto (0 a 2) do triângulo cuja aresta oposta é a diagonal de uma face lateral, ou −1 sem diagonal. */
    static int hidden(int triangle) {
        return HIDDEN[triangle];
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

    /**
     * Grava o triângulo {@code t} de {@code a}, {@code b}, {@code c}, trocando a ordem se a normal sair para dentro.
     * {@code hiddenSlot} é o canto (0, 1 ou 2, na ordem dada) cuja aresta oposta é a diagonal, ou −1; a troca de
     * ordem leva o canto junto.
     */
    private static void triangle(int t, float[] pa, int ia, float[] pb, int ib, float[] pc, int ic, int hiddenSlot) {
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
        int slot = hiddenSlot;
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
            slot = hiddenSlot == 1 ? 2 : hiddenSlot == 2 ? 1 : hiddenSlot;
        }
        NORMALS[t * 3] = n[0];
        NORMALS[t * 3 + 1] = n[1];
        NORMALS[t * 3 + 2] = n[2];
        HIDDEN[t] = (byte) slot;
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
