package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.client.fx.FxRibbon;
import io.github.matheusanbs.wirelessautomate.client.fx.FxSheets;

/**
 * O estouro da conjunção, o {@code // estouro:} do filme ({@code partes.js}): o feixe de dois trilhos que sai do sol
 * até o anel do horizonte, o clarão no fim dele e o aro de choque. Não depende da vazão (a spec): com vazão 0 o
 * estouro e o clarão no horizonte acontecem.
 */
final class ArmilarBeam {
    private static final int POINTS = 25;
    private static final int STEPS = POINTS - 1;
    /** Os eixos do aro de choque no plano da face do horizonte: z e y, unitários. */
    private static final double[] AXIS_Z = {0, 0, 1};
    private static final double[] AXIS_Y = {0, 1, 0};

    private ArmilarBeam() {
    }

    static void draw(ArmilarPass p) {
        ArmilarPhase e = p.phase;
        if (e.raio <= 0.002) {
            return;
        }
        double[] pa = ArmilarSpec.HORIZ_BURST;
        double t = p.t;
        int dis = p.disMilli();
        FxRibbon.Buffer b = p.local;
        // do sol ao horizonte, crescendo com {@code cresce}
        for (int j = 0; j < POINTS; j++) {
            double f = e.cresce * j / STEPS;
            b.pts[j * 3] = (float) (pa[0] * f);
            b.pts[j * 3 + 1] = (float) (ArmilarSpec.SOL_Y + (pa[1] - ArmilarSpec.SOL_Y) * f);
            b.pts[j * 3 + 2] = (float) (pa[2] * f);
        }
        fill(b, 1.6, e.raio);
        p.ribbon(FxSheets.TRILHOS, 0, b, POINTS, ArmilarPass.WHITE, 12, t * 2, dis);
        fill(b, 1.6, 0.8 * e.raio);
        p.ribbon(FxSheets.TRILHOS_RIM, 0, b, POINTS, ArmilarPass.WHITE, 12, t * 2, dis);
        fill(b, 1.1, e.raio);
        p.ribbon(FxSheets.FLUXO, 0, b, POINTS, ArmilarPass.WHITE, 10, t * 3.2, dis);
        p.spr(FxSheets.CLARAO, FxSheets.CLARAO.frameByFraction(Ease.ss(0.1, 0.5, e.d)), pa[0], pa[1], pa[2], 6,
                ArmilarPass.CLARO, e.clarao, 0.2, 0);
        p.dec(FxSheets.CHOQUE, FxSheets.CHOQUE.frameByFraction(e.onda), pa[0], pa[1], pa[2], AXIS_Z, AXIS_Y, 8,
                ArmilarPass.OURO, e.raio, 0, 0);
    }

    /** A meia largura e o alfa iguais em todas as amostras. */
    private static void fill(FxRibbon.Buffer b, double halfWidth, double alpha) {
        for (int j = 0; j < POINTS; j++) {
            b.w[j] = (float) halfWidth;
            b.a[j] = (float) alpha;
        }
    }
}
