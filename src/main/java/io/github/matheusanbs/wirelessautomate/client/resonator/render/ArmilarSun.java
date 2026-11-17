package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.client.fx.FxSheets;

/**
 * O sol, o {@code desenhar} do filme ({@code partes.js}, de {@code // o sol: névoa escura por baixo} até as brasas): a
 * névoa vinho por baixo (tinta), o brilho, o disco que ferve, a coroa de seis chamas, a estrela e o aro tracejado
 * (luz), e as brasas. Encolhe e escurece na preparação ({@code enc}). A tinta sai antes da luz porque o
 * {@code FxBatch} desenha por tipo, tinta primeiro.
 */
final class ArmilarSun {
    private static final int FLAMES = 6;

    private ArmilarSun() {
    }

    static void draw(ArmilarPass p) {
        double y = ArmilarSpec.SOL_Y;
        double ig = p.ig;
        double t = p.t;
        double ie = ig * p.enc;
        // a fumaça do filme toca 3 dos 4 quadros
        p.spr(FxSheets.FUMACA, FxSheets.FUMACA.frameAt(t, 4, 3), 0, y, 0, 7.5 * ig * (1 + 0.3 * p.prepEff),
                ArmilarPass.VINHO, 0.45 + 0.3 * p.prepEff, t * 0.15, 0);
        p.spr(FxSheets.FUMACA, FxSheets.FUMACA.frameAt(t + 0.5, 4, 3), 0, y, 0, 6 * ig, ArmilarPass.VINHO, 0.3,
                -t * 0.11 + 1.3, 0);
        p.spr(FxSheets.BRILHO_G, 0, 0, y, 0, 6.5 * ie, ArmilarPass.PLASMA, 0.5, 0, 0);
        p.spr(FxSheets.SOL, FxSheets.SOL.frameAt(t, 8), 0, y, 0, 4.6 * ie, ArmilarPass.WHITE, 1, 0, 0);
        // a coroa anda em eixos da tela: o deslocamento é do plano da câmera, não do referencial local
        double rr = 4.2 * ie / 16;
        for (int k = 0; k < FLAMES; k++) {
            double th = k * Math.PI / 3 + t * 0.35;
            double c = Math.cos(th) * rr;
            double s = Math.sin(th) * rr;
            p.sprCam(FxSheets.CHAMA, FxSheets.CHAMA.frameAt(t + k * 0.37, 10),
                    (float) (p.sunX + p.fx.rx * c + p.fx.ux * s), (float) (p.sunY + p.fx.ry * c + p.fx.uy * s),
                    (float) (p.sunZ + p.fx.rz * c + p.fx.uz * s), 1.6 * ie, ArmilarPass.WHITE, 0.85,
                    th - Math.PI / 2, 0);
        }
        p.spr(FxSheets.ESTRELA, FxSheets.ESTRELA.frameAt(t, 6), 0, y, 0, 6.5 * ie, ArmilarPass.OURO,
                0.35 + 0.3 * p.phase.raio, t * 0.2, 0);
        p.spr(FxSheets.ARO_TRACEJADO, 0, 0, y, 0, 7 * ie, ArmilarPass.OURO, 0.45, -t * 0.3, 0);
        ArmilarEmbers.draw(p);
    }
}
