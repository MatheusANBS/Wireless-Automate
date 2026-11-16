package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * As malhas facetadas do conceito Armilar, como listas de quads em px: anéis com os engastes, pinos, cristal,
 * horizonte e marcos. Portadas de {@code shared.js} e {@code partes.js} (cada método cita a função de origem).
 * Montadas uma vez, no carregamento da classe.
 */
public final class ArmilarMeshes {
    private static final QuadMesh[] RINGS = {buildRing(0), buildRing(1), buildRing(2)};
    private static final QuadMesh[] PINS = {buildPins(0), buildPins(1), buildPins(2)};
    private static final QuadMesh CRYSTAL = buildCrystal();
    private static final QuadMesh HORIZON = buildHorizon();
    private static final QuadMesh MARKERS = buildMarkers();

    private ArmilarMeshes() {
    }

    /** O anel facetado e os engastes dos nós da órbita; textura {@code anel} (32 × 10). */
    public static QuadMesh ring(int orbit) {
        return RINGS[orbit];
    }

    /** Os dois pinos da órbita; textura {@code grafite} (8 × 8). */
    public static QuadMesh pins(int orbit) {
        return PINS[orbit];
    }

    /** O cristal; textura 8 × 16. */
    public static QuadMesh crystal() {
        return CRYSTAL;
    }

    /** O anel do horizonte, em latão; textura 16 × 16. */
    public static QuadMesh horizon() {
        return HORIZON;
    }

    /** Os quatro marcos do horizonte, em coral (como no conceito); textura 16 × 16. */
    public static QuadMesh markers() {
        return MARKERS;
    }

    /** {@code W.caixa} de shared.js: seis faces (+z, −z, +x, −x, +y, −y), giradas em y por {@code yaw} ({@code M.ry}). */
    private static void box(QuadMesh m, double x0, double y0, double z0, double x1, double y1, double z1,
            double yaw) {
        double c = Math.cos(yaw);
        double s = Math.sin(yaw);
        double dx = Math.min(x1 - x0, m.texW());
        double dy = Math.min(y1 - y0, m.texH());
        double dzw = Math.min(z1 - z0, m.texW());
        double dzh = Math.min(z1 - z0, m.texH());
        face(m, new double[][] {{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}}, c, s, dx, dy);
        face(m, new double[][] {{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}}, c, s, dx, dy);
        face(m, new double[][] {{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}}, c, s, dzw, dy);
        face(m, new double[][] {{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}}, c, s, dzw, dy);
        face(m, new double[][] {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}, c, s, dx, dzh);
        face(m, new double[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}}, c, s, dx, dzh);
    }

    private static void face(QuadMesh m, double[][] p, double c, double s, double uw, double vh) {
        for (double[] v : p) {
            double x = v[0] * c + v[2] * s;
            double z = -v[0] * s + v[2] * c;
            v[0] = x;
            v[2] = z;
        }
        m.add(p, 0, 0, uw, vh, 1);
    }

    private static double[] pt(double rr, double a, double y) {
        return new double[] {rr * Math.cos(a), y, -rr * Math.sin(a)};
    }

    /** {@code anelFacetado(R, n)} de shared.js mais os engastes do {@code malhas} de partes.js. */
    private static QuadMesh buildRing(int i) {
        QuadMesh m = new QuadMesh(32, 10);
        double r = ArmilarSpec.RAIOS[i];
        int n = ArmilarSpec.SEGMENTOS[i];
        double ri = r - ArmilarSpec.RING_HALF_W;
        double ro = r + ArmilarSpec.RING_HALF_W;
        double seg = 2 * Math.PI * r / n;
        for (int k = 0; k < n; k++) {
            double a0 = k * 2 * Math.PI / n;
            double a1 = (k + 1) * 2 * Math.PI / n;
            double am = (a0 + a1) / 2;
            double u0 = (k * seg) % 16;
            double u1 = u0 + seg;
            double[] fora = {Math.cos(am), 0, -Math.sin(am)};
            double[] dentro = {-fora[0], 0, -fora[2]};
            m.addToward(new double[][] {pt(ri, a0, 1), pt(ri, a1, 1), pt(ro, a1, 1), pt(ro, a0, 1)},
                    u0, 0, u1, 3, new double[] {0, 1, 0}, 1);
            m.addToward(new double[][] {pt(ro, a0, 1), pt(ro, a1, 1), pt(ro, a1, -1), pt(ro, a0, -1)},
                    u0, 3, u1, 5, fora, 1);
            m.addToward(new double[][] {pt(ri, a1, 1), pt(ri, a0, 1), pt(ri, a0, -1), pt(ri, a1, -1)},
                    u0, 5, u1, 7, dentro, 1);
            m.addToward(new double[][] {pt(ro, a0, -1), pt(ro, a1, -1), pt(ri, a1, -1), pt(ri, a0, -1)},
                    u0, 7, u1, 10, new double[] {0, -1, 0}, 1);
        }
        int nos = ArmilarSpec.NOS[i];
        for (int k = 0; k < nos; k++) {
            double yaw = k * 2 * Math.PI / nos;
            box(m, r - ArmilarSpec.BASE, ArmilarSpec.RING_HALF_Y, -ArmilarSpec.BASE, r + ArmilarSpec.BASE,
                    ArmilarSpec.TOPO_BASE, ArmilarSpec.BASE, yaw);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    double x0 = r + sx * ArmilarSpec.GARRA_DE;
                    double x1 = r + sx * ArmilarSpec.GARRA_ATE;
                    double z0 = sz * ArmilarSpec.GARRA_DE;
                    double z1 = sz * ArmilarSpec.GARRA_ATE;
                    box(m, Math.min(x0, x1), ArmilarSpec.TOPO_BASE, Math.min(z0, z1), Math.max(x0, x1),
                            ArmilarSpec.TOPO_GARRA, Math.max(z0, z1), yaw);
                }
            }
        }
        return m;
    }

    /** {@code pinos} de partes.js: a caixa de r + 1,5 a r + 3,5 e o espelho em −x. */
    private static QuadMesh buildPins(int i) {
        QuadMesh m = new QuadMesh(8, 8);
        double r = ArmilarSpec.RAIOS[i];
        double h = ArmilarSpec.PINO_MEIO;
        box(m, r + ArmilarSpec.PINO_DE, -h, -h, r + ArmilarSpec.PINO_ATE, h, h, 0);
        box(m, -r - ArmilarSpec.PINO_ATE, -h, -h, -r - ArmilarSpec.PINO_DE, h, h, 0);
        return m;
    }

    private static double[] cv(double r, int k, double y) {
        return new double[] {r * Math.cos(k * Math.PI / 3), y, r * Math.sin(k * Math.PI / 3)};
    }

    /**
     * {@code W.cristal(1.5, 1.5, 1.9)} de shared.js. Os tons do filme ({@code 1,08; 1; 0,9; 0,8}) vão divididos por
     * 1,08: o filme passa de 1 no shader, e no vértice o máximo é 1.
     */
    private static QuadMesh buildCrystal() {
        QuadMesh m = new QuadMesh(8, 16);
        double r = ArmilarSpec.CRISTAL_R;
        double h = ArmilarSpec.CRISTAL_H;
        double[] tons = {1.08 / 1.08, 1 / 1.08, 0.9 / 1.08, 0.8 / 1.08};
        double[] top = {0, h + ArmilarSpec.CRISTAL_PONTA, 0};
        double[] bot = {0, -h - ArmilarSpec.CRISTAL_PONTA, 0};
        for (int k = 0; k < 6; k++) {
            double c0 = (k % 3) * 2;
            double a = (k + 0.5) * Math.PI / 3;
            double[] fora = {Math.cos(a), 0, Math.sin(a)};
            m.addToward(new double[][] {cv(r, k + 1, h), cv(r, k, h), cv(r, k, -h), cv(r, k + 1, -h)},
                    c0, 2, c0 + 2, 2 + Math.min(12, 4 * h), fora, tons[k % 4]);
            m.addToward(new double[][] {cv(r, k + 1, h), top, top, cv(r, k, h)},
                    c0, 0, c0 + 2, 2, new double[] {fora[0], fora[1] + 1, fora[2]}, tons[(k + 1) % 4]);
            m.addToward(new double[][] {cv(r, k, -h), bot, bot, cv(r, k + 1, -h)},
                    c0, 12, c0 + 2, 15, new double[] {fora[0], fora[1] - 1, fora[2]}, tons[(k + 2) % 4]);
        }
        return m;
    }

    /** {@code anel16} de gerar_conceitos.py: 16 caixas de latão em volta do eixo y. */
    private static QuadMesh buildHorizon() {
        QuadMesh m = new QuadMesh(16, 16);
        double l = ArmilarSpec.HORIZ_R_IN * Math.tan(Math.PI / ArmilarSpec.HORIZ_SEGS);
        for (int k = 0; k < ArmilarSpec.HORIZ_SEGS; k++) {
            box(m, ArmilarSpec.HORIZ_R_IN, ArmilarSpec.HORIZ_Y0, -l, ArmilarSpec.HORIZ_R_OUT, ArmilarSpec.HORIZ_Y1,
                    l, Math.toRadians(k * 22.5));
        }
        return m;
    }

    /** Os {@code marco_NN} de gerar_conceitos.py: quatro caixas coral sobre o topo do horizonte, a cada 90°. */
    private static QuadMesh buildMarkers() {
        QuadMesh m = new QuadMesh(16, 16);
        for (int k = 0; k < 4; k++) {
            box(m, ArmilarSpec.MARCO_X0, ArmilarSpec.MARCO_Y0, -ArmilarSpec.MARCO_Z, ArmilarSpec.MARCO_X1,
                    ArmilarSpec.MARCO_Y1, ArmilarSpec.MARCO_Z, Math.toRadians(k * 90.0));
        }
        return m;
    }
}
