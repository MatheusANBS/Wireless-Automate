package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.client.fx.FxSheets;

/**
 * O impacto na Porta de Energia, o {@code // impacto na Porta de Energia:} do filme ({@code partes.js}): o clarão, o
 * sigilo na face da Porta, o aro de choque e a mancha de fumaça no chão, as 16 brasas e as 4 fumaças. A vazão
 * multiplica o alfa e o número de brasas (a spec): com vazão 0 não há impacto.
 */
final class ArmilarImpact {
    private static final int EMBERS = 16;
    private static final int SMOKES = 4;
    /** A meia largura da brasa por tamanho (0 a 2): a folha {@code BRASA(2 − tamanho)} vai de grande a pequena. */
    private static final double[] EMBER_HALF = {0.9, 0.7, 0.5};
    private static final double[] AXIS_X = {1, 0, 0};
    private static final double[] AXIS_Y = {0, 1, 0};
    private static final double[] AXIS_Z = {0, 0, 1};
    private static final double[] OUT = new double[5];

    private ArmilarImpact() {
    }

    static void draw(ArmilarPass p) {
        ArmilarPhase e = p.phase;
        double k = p.flowK;
        if (e.porta <= 0.002 || k <= 0) {
            return;
        }
        double[] porta = ArmilarSpec.PORTA_IMPACTO;
        double po = e.porta * k;
        double d = e.d;
        double t = p.t;
        int dis = p.disMilli();
        p.spr(FxSheets.CLARAO, FxSheets.CLARAO.frameByFraction(Ease.ss(0.55, 0.95, d)), porta[0], porta[1], porta[2],
                6.5, ArmilarPass.CLARO, e.clarao2 * k, 0.4, 0);
        // o sigilo do mostrador, na face da Porta
        p.dec(FxSheets.SIGILO, FxSheets.SIGILO.frameByFraction(Ease.ss(0.55, 1.3, d)), porta[0] + 0.2, porta[1],
                porta[2], AXIS_Z, AXIS_Y, 5.5 * Ease.eob(e.surge, 1.6), ArmilarPass.WHITE, po, t * 0.35, dis);
        // o aro de choque e a mancha de fumaça no chão
        p.dec(FxSheets.CHOQUE, FxSheets.CHOQUE.frameByFraction(e.onda2), 47 + ArmilarSpec.PORTA_DESLOC_X, 0.12, 0,
                AXIS_X, AXIS_Z, 11, ArmilarPass.OURO, po, 0, 0);
        p.dec(FxSheets.FUMACA, 1, 46 + ArmilarSpec.PORTA_DESLOC_X, 0.1, 0, AXIS_X, AXIS_Z, 6, ArmilarPass.VINHO,
                0.45 * po, 0.7, dis);
        // a força multiplica o número de brasas: as primeiras n
        int n = (int) Math.round(EMBERS * k);
        for (int i = 0; i < n; i++) {
            if (!ArmilarEmbers.impact(i, d, OUT)) {
                continue;
            }
            double fv = OUT[3];
            int size = (int) OUT[4];
            p.spr(ArmilarEmbers.sheet(2 - size), 0, porta[0] + OUT[0], porta[1] + OUT[1], porta[2] + OUT[2],
                    EMBER_HALF[size], fv < 0.33 ? ArmilarPass.CLARO : fv < 0.66 ? ArmilarPass.OURO : ArmilarPass.CORAL,
                    po * (1 - fv * 0.5), 0, 0);
        }
        for (int i = 0; i < SMOKES; i++) {
            double age = d - 0.7 - i * 0.18;
            if (age < 0 || age > 1.6) {
                continue;
            }
            p.spr(FxSheets.FUMACA, FxSheets.FUMACA.frameByFraction(age / 1.6), porta[0] + 1.5 + i,
                    porta[1] + 2 + age * 6, porta[2] + (i - 1.5) * 1.5, 1.5 + age * 2, ArmilarPass.VINHO, 0.45,
                    i + age * 0.5, 0);
        }
    }
}
