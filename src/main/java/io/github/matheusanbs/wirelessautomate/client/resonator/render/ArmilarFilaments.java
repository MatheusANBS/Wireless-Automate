package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.client.fx.FxRibbon;
import io.github.matheusanbs.wirelessautomate.client.fx.FxSheets;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;

/**
 * Os filamentos, o {@code // filamentos} do filme ({@code partes.js}): do cristal vivo ao sol, um traço escuro de dois
 * trilhos por baixo (tinta) e a corrente de luz correndo por cima. Ondulam fora da sintonia e ficam firmes em sintonia.
 * Só com faixa de vazão ou em sintonia; cristal vazio ou opaco não tem filamento, o rachado tem 35%.
 */
final class ArmilarFilaments {
    private static final int CONTROLS = 6;
    /** O vetor fixo do filme para achar o lado do filamento. */
    private static final double[] REF = {0.2, 1, 0.3};
    /** O eixo de reserva, para o filamento paralelo ao vetor fixo (o filme dividiria por zero). */
    private static final double[] SPARE = {1, 0, 0};
    /** Menor vetor que ainda se normaliza. */
    private static final double EPS = 1e-9;

    private static final double[] Q = new double[3];
    private static final double[] SUN = {0, ArmilarSpec.SOL_Y, 0};
    private static final float[] CTL = new float[CONTROLS * 3];
    private static final double[] D = new double[3];
    private static final double[] SIDE = new double[3];
    private static final double[] UP = new double[3];

    private ArmilarFilaments() {
    }

    /**
     * Os 6 controles do filamento (x, y, z em {@code ctl}), em px locais: de {@code from} (o cristal) a {@code sun},
     * com {@code wv = sin(fπ) × amp} de ondulação para o lado e para o alto, que depende do tempo, da órbita e do
     * lugar do cristal nela. {@code amp = 0,4 + 1,5 × (1 − u)}: firme em sintonia.
     */
    static void path(double[] from, double[] sun, double t, double u, int orbit, int slot, float[] ctl) {
        for (int k = 0; k < 3; k++) {
            D[k] = sun[k] - from[k];
        }
        cross(D, REF, SIDE);
        if (!normalize(SIDE)) {
            cross(D, SPARE, SIDE);
            normalize(SIDE);
        }
        cross(SIDE, D, UP);
        normalize(UP);
        double amp = 0.4 + 1.5 * (1 - u);
        for (int j = 0; j < CONTROLS; j++) {
            double f = j / (double) (CONTROLS - 1);
            double wv = Math.sin(f * Math.PI) * amp;
            double sw = wv * Math.sin(t * (1.1 + orbit * 0.2) + f * 3 + slot * 2.1);
            double uw = 0.5 * wv * Math.cos(t * 0.9 + f * 2.5 + slot);
            for (int k = 0; k < 3; k++) {
                ctl[j * 3 + k] = (float) (from[k] + D[k] * f + SIDE[k] * sw + UP[k] * uw);
            }
        }
    }

    static void draw(ArmilarPass p) {
        ArmilarScene s = p.scene;
        ChamberView view = s.view();
        if (!(view.band() > 0 || view.tuned())) {
            return;
        }
        byte[] chambers = s.chambers();
        double t = p.t;
        double u = p.u;
        FxRibbon.Buffer b = p.local;
        for (int node = 0; node < chambers.length; node++) {
            byte code = chambers[node];
            double f = CrystalLook.filament(code);
            if (CrystalLook.empty(code) || f <= 0) {
                continue;
            }
            double ck = ArmilarAssembly.crystal(p.a, ArmilarNodes.popIndex(node));
            if (ck <= 0.001) {
                continue;
            }
            int orbit = ArmilarNodes.orbit(node);
            int slot = ArmilarNodes.slot(node);
            ArmilarNodes.center(node, p.ring[orbit], p.prec[orbit], p.tilt[orbit], p.spin[orbit], Q);
            path(Q, SUN, t, u, orbit, slot, CTL);
            int n = FxRibbon.catmullRom(CTL, CONTROLS, 1f, b);
            double al = ck * (0.3 + 0.6 * u) * p.eff * f;
            // a tinta: dois trilhos escuros por baixo, mais forte no meio
            for (int i = 0; i < n; i++) {
                double fi = i / (double) (n - 1);
                b.w[i] = 0.75f;
                b.a[i] = (float) (0.3 * al * Math.sin(fi * Math.PI));
            }
            p.ribbon(FxSheets.TRILHOS, 0, b, n, ArmilarPass.WHITE, 10, 0, 0);
            // a luz: a corrente que corre, mais forte perto do sol
            for (int i = 0; i < n; i++) {
                double fi = i / (double) (n - 1);
                b.w[i] = (float) (0.55 + 0.25 * u);
                b.a[i] = (float) (al * (0.4 + 0.6 * fi));
            }
            p.ribbon(FxSheets.FLUXO, 0, b, n, ArmilarPass.WHITE, 9, t * 1.6, 0);
            p.spr(FxSheets.BRILHO, 0, Q[0], Q[1], Q[2], 1.6, ArmilarPass.CORAL_HI, 0.45 * ck * f, 0, 0);
            if (u > 0.05 && CrystalLook.star(code)) {
                p.spr(FxSheets.ESTRELA, FxSheets.ESTRELA.frameAt(t + slot * 0.3, 6), Q[0], Q[1], Q[2], 2.6,
                        ArmilarPass.OURO, 0.45 * u * ck, t * 0.4 + slot, 0);
            }
        }
    }

    private static void cross(double[] a, double[] b, double[] out) {
        double x = a[1] * b[2] - a[2] * b[1];
        double y = a[2] * b[0] - a[0] * b[2];
        double z = a[0] * b[1] - a[1] * b[0];
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }

    /** Normaliza no lugar; com vetor pequeno demais zera e devolve {@code false}. */
    private static boolean normalize(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < EPS) {
            v[0] = 0;
            v[1] = 0;
            v[2] = 0;
            return false;
        }
        v[0] /= l;
        v[1] /= l;
        v[2] /= l;
        return true;
    }
}
