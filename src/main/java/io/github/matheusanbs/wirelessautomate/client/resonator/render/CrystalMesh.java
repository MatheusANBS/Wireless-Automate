package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * As duas malhas do cristal da câmara, em unidades do cristal (antes da escala do desenho), montadas uma vez; o
 * desenho só lê as tabelas. Acompanham os sprites do mod:
 * <ul>
 *   <li>{@link #CLUSTER}, o aglomerado de 4 pontas hexagonais de alturas diferentes do Cristal de Sinal (96
 *       triângulos);</li>
 *   <li>{@link #DIAMOND}, o losango com cinta do Lapidado e dos Afinados: um hexágono com os dois ápices e uma
 *       faixa entre os anéis, que de qualquer lado lê como o quadrado girado do sprite (24 triângulos), em {@link #BELT_BOTTOM} a
 *       {@link #BELT_TOP} de altura relativa.</li>
 * </ul>
 * Por vértice: a posição, a altura relativa no cristal, de 0 em {@link #BOTTOM} a 1 em {@link #TOP}, e a direção do
 * contorno, do ponto de dentro da peça até o vértice: a mesma em todos os triângulos que dividem o vértice, para o
 * casco inflado não abrir nas arestas. Por triângulo: a normal plana, para fora, e os vértices em sentido anti-horário
 * vistos de fora.
 */
public final class CrystalMesh {
    public static final float TOP = 0.58f;
    public static final float BOTTOM = -0.46f;
    /** A altura relativa em que a faixa do losango começa e termina, a mesma do shader. */
    static final float BELT_BOTTOM = 0.42f;
    static final float BELT_TOP = 0.56f;
    static final int SPIKE_SIDES = 6;
    static final int BELT_SIDES = 6;
    private static final float BELT_UPPER_Y = 0.12f;
    private static final float BELT_LOWER_Y = -0.02f;
    static final double BELT_RADIUS = 0.38;
    static final CrystalMesh CLUSTER = cluster();
    static final CrystalMesh DIAMOND = diamond();

    private final int triangles;
    /** x, y, z de cada vértice. */
    private final float[] positions;
    /** nx, ny, nz de cada triângulo. */
    private final float[] normals;
    /** A direção do contorno de cada vértice, unitária. */
    private final float[] outlines;

    private CrystalMesh(int triangles, float[] positions, float[] normals, float[] outlines) {
        this.triangles = triangles;
        this.positions = positions;
        this.normals = normals;
        this.outlines = outlines;
    }

    int triangles() {
        return triangles;
    }

    int vertices() {
        return 3 * triangles;
    }

    float x(int vertex) {
        return positions[vertex * 3];
    }

    float y(int vertex) {
        return positions[vertex * 3 + 1];
    }

    float z(int vertex) {
        return positions[vertex * 3 + 2];
    }

    /** A altura relativa do vértice no cristal, de 0 a 1. */
    float height(int vertex) {
        return (y(vertex) - BOTTOM) / (TOP - BOTTOM);
    }

    float outlineX(int vertex) {
        return outlines[vertex * 3];
    }

    float outlineY(int vertex) {
        return outlines[vertex * 3 + 1];
    }

    float outlineZ(int vertex) {
        return outlines[vertex * 3 + 2];
    }

    float normalX(int triangle) {
        return normals[triangle * 3];
    }

    float normalY(int triangle) {
        return normals[triangle * 3 + 1];
    }

    float normalZ(int triangle) {
        return normals[triangle * 3 + 2];
    }

    private static CrystalMesh cluster() {
        Builder b = new Builder(4 * SPIKE_SIDES * 4);
        // x, z do centro, raio, inclinação x e z do ápice, altura do ombro, do ápice e giro
        spike(b, 0.05, 0.00, 0.17, 0.02, 0.00, 0.30, TOP, 0.0);
        spike(b, -0.23, 0.05, 0.14, -0.04, 0.00, 0.10, 0.30, 0.3);
        spike(b, 0.25, -0.05, 0.12, 0.04, 0.00, -0.14, 0.06, 0.6);
        spike(b, 0.02, 0.22, 0.10, 0.00, 0.03, -0.20, -0.02, 0.9);
        return b.build();
    }

    /** Uma ponta hexagonal: o corpo do fundo ao ombro, a capa até o ápice e o fundo. */
    private static void spike(Builder b, double cx, double cz, double r, double leanX, double leanZ, double shoulder,
            double apexY, double turn) {
        float[][] bottom = new float[SPIKE_SIDES][];
        float[][] top = new float[SPIKE_SIDES][];
        for (int i = 0; i < SPIKE_SIDES; i++) {
            double a = turn + 2 * Math.PI * i / SPIKE_SIDES;
            bottom[i] = new float[] {(float) (cx + Math.cos(a) * r), BOTTOM, (float) (cz + Math.sin(a) * r)};
            top[i] = new float[] {(float) (cx + Math.cos(a) * r * 0.9 + leanX * 0.5), (float) shoulder,
                (float) (cz + Math.sin(a) * r * 0.9 + leanZ * 0.5)};
        }
        float[] apex = {(float) (cx + leanX), (float) apexY, (float) (cz + leanZ)};
        float[] floor = {(float) cx, BOTTOM, (float) cz};
        float insideY = (float) ((BOTTOM + apexY) / 2);
        for (int i = 0; i < SPIKE_SIDES; i++) {
            int j = (i + 1) % SPIKE_SIDES;
            b.triangle(bottom[i], bottom[j], top[j], (float) cx, insideY, (float) cz);
            b.triangle(bottom[i], top[j], top[i], (float) cx, insideY, (float) cz);
            b.triangle(top[i], top[j], apex, (float) cx, insideY, (float) cz);
            b.triangle(bottom[j], bottom[i], floor, (float) cx, insideY, (float) cz);
        }
    }

    private static CrystalMesh diamond() {
        Builder b = new Builder(4 * BELT_SIDES);
        float[][] upper = new float[BELT_SIDES][];
        float[][] lower = new float[BELT_SIDES][];
        for (int i = 0; i < BELT_SIDES; i++) {
            double a = Math.PI / BELT_SIDES + 2 * Math.PI * i / BELT_SIDES;
            float x = (float) (Math.cos(a) * BELT_RADIUS);
            float z = (float) (Math.sin(a) * BELT_RADIUS);
            upper[i] = new float[] {x, BELT_UPPER_Y, z};
            lower[i] = new float[] {x, BELT_LOWER_Y, z};
        }
        float[] top = {0, TOP, 0};
        float[] bottom = {0, BOTTOM, 0};
        float insideY = (BELT_UPPER_Y + BELT_LOWER_Y) / 2;
        for (int i = 0; i < BELT_SIDES; i++) {
            int j = (i + 1) % BELT_SIDES;
            b.triangle(upper[i], upper[j], top, 0, insideY, 0);
            b.triangle(lower[j], lower[i], bottom, 0, insideY, 0);
            b.triangle(lower[i], lower[j], upper[j], 0, insideY, 0);
            b.triangle(lower[i], upper[j], upper[i], 0, insideY, 0);
        }
        return b.build();
    }

    /** Junta os triângulos, virando para fora de um ponto de dentro. */
    private static final class Builder {
        private final int expected;
        private final float[] positions;
        private final float[] normals;
        private final float[] outlines;
        private int count;

        Builder(int expected) {
            this.expected = expected;
            this.positions = new float[expected * 9];
            this.normals = new float[expected * 3];
            this.outlines = new float[expected * 9];
        }

        /** O triângulo {@code a}, {@code b}, {@code c}, trocando a ordem se a normal sair para o lado de {@code inside}. */
        void triangle(float[] a, float[] b, float[] c, float insideX, float insideY, float insideZ) {
            float ux = b[0] - a[0];
            float uy = b[1] - a[1];
            float uz = b[2] - a[2];
            float vx = c[0] - a[0];
            float vy = c[1] - a[1];
            float vz = c[2] - a[2];
            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= length;
            ny /= length;
            nz /= length;
            float mx = (a[0] + b[0] + c[0]) / 3 - insideX;
            float my = (a[1] + b[1] + c[1]) / 3 - insideY;
            float mz = (a[2] + b[2] + c[2]) / 3 - insideZ;
            float[] second = b;
            float[] third = c;
            if (nx * mx + ny * my + nz * mz < 0) {
                second = c;
                third = b;
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            int p = count * 9;
            System.arraycopy(a, 0, positions, p, 3);
            System.arraycopy(second, 0, positions, p + 3, 3);
            System.arraycopy(third, 0, positions, p + 6, 3);
            normals[count * 3] = nx;
            normals[count * 3 + 1] = ny;
            normals[count * 3 + 2] = nz;
            outline(a, p, insideX, insideY, insideZ);
            outline(second, p + 3, insideX, insideY, insideZ);
            outline(third, p + 6, insideX, insideY, insideZ);
            count++;
        }

        private void outline(float[] v, int at, float insideX, float insideY, float insideZ) {
            float dx = v[0] - insideX;
            float dy = v[1] - insideY;
            float dz = v[2] - insideZ;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            outlines[at] = dx / length;
            outlines[at + 1] = dy / length;
            outlines[at + 2] = dz / length;
        }

        CrystalMesh build() {
            if (count != expected) {
                throw new IllegalStateException("a malha tem " + count + " triângulos, esperava " + expected);
            }
            return new CrystalMesh(count, positions, normals, outlines);
        }
    }
}
