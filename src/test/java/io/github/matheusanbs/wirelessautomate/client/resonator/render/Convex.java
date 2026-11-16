package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import java.util.ArrayList;
import java.util.List;

/** Só de teste: um sólido convexo como um conjunto de planos com a normal para fora. */
final class Convex {
    private final List<double[]> planes = new ArrayList<>();

    /** O plano que passa por (px, py, pz) com a normal (nx, ny, nz); a normal é normalizada aqui. */
    void add(double nx, double ny, double nz, double px, double py, double pz) {
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= len;
        ny /= len;
        nz /= len;
        planes.add(new double[] {nx, ny, nz, nx * px + ny * py + nz * pz});
    }

    /** O ponto está estritamente dentro (a distância ao plano mais próximo é menor que −eps em todos). */
    boolean strictlyInside(double x, double y, double z, double eps) {
        for (double[] p : planes) {
            if (p[0] * x + p[1] * y + p[2] * z - p[3] >= -eps) {
                return false;
            }
        }
        return true;
    }

    /**
     * Os quads {@code first} a {@code first + count − 1} da malha como planos, depois de {@code p' = r·p + t}
     * (o {@code r} é uma rotação 3 × 3). Serve para um sólido convexo (o cristal; uma caixa).
     */
    static Convex of(QuadMesh m, int first, int count, double[][] r, double[] t) {
        Convex c = new Convex();
        for (int q = first; q < first + count; q++) {
            double[] p = apply(r, t, m.x(q, 0), m.y(q, 0), m.z(q, 0), 1);
            double[] n = apply(r, new double[3], m.nx(q), m.ny(q), m.nz(q), 0);
            c.add(n[0], n[1], n[2], p[0], p[1], p[2]);
        }
        return c;
    }

    static final double[][] IDENTITY = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

    private static double[] apply(double[][] r, double[] t, double x, double y, double z, double w) {
        return new double[] {r[0][0] * x + r[0][1] * y + r[0][2] * z + t[0] * w,
                r[1][0] * x + r[1][1] * y + r[1][2] * z + t[1] * w,
                r[2][0] * x + r[2][1] * y + r[2][2] * z + t[2] * w};
    }

    /** Quantos pontos de uma malha regular (passo 0,05) da caixa estão estritamente dentro deste sólido. */
    int countInside(double[] box, double eps) {
        int n = 0;
        for (double x = box[0]; x <= box[3] + 1e-9; x += 0.05) {
            for (double y = box[1]; y <= box[4] + 1e-9; y += 0.05) {
                for (double z = box[2]; z <= box[5] + 1e-9; z += 0.05) {
                    if (strictlyInside(x, y, z, eps)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }
}
