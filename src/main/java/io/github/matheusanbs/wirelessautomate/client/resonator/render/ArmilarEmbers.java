package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.client.fx.FxSheet;
import io.github.matheusanbs.wirelessautomate.client.fx.FxSheets;

/** As 12 brasas em órbita do sol, do {@code desenhar} do filme ({@code partes.js}). */
final class ArmilarEmbers {
    private static final int COUNT = 12;
    /** A folha de cada brasa: {@code 'brasa' + (k % 3)} no filme. */
    private static final FxSheet[] SHEETS = {FxSheets.BRASA0, FxSheets.BRASA1, FxSheets.BRASA2};
    private static final double[] OFF = new double[3];

    private ArmilarEmbers() {
    }

    /** A brasa {@code k} em órbita, relativa ao sol (px): sugada para perto pela preparação ({@code prep}, de 0 a 1). */
    static void orbit(int k, double t, double prep, double[] out) {
        double a0 = k * 2.399;
        double rr = (8.5 + 3.5 * ((k * 37) % 10) / 10.0) * (1 - 0.65 * prep);
        double an = a0 + t * (0.5 + (k % 3) * 0.17);
        out[0] = rr * Math.cos(an);
        out[1] = 2.2 * Math.sin(an * 1.7 + k);
        out[2] = rr * Math.sin(an);
    }

    /** O alfa (antes de multiplicar pelo {@code ig} do sol): {@code 0,55 + 0,35 sin(1,3 t + k)}. */
    static double orbitAlpha(int k, double t) {
        return 0.55 + 0.35 * Math.sin(t * 1.3 + k);
    }

    /** O primeiro sorteio da brasa do impacto, de 0 a 1: {@code ((k × 9301 + 49297) % 233280) / 233280} (inteiro, como o filme). */
    static double impactR1(int k) {
        return ((k * 9301 + 49297) % 233280) / 233280.0;
    }

    /** O segundo sorteio da brasa do impacto, de 0 a 1: {@code ((k × 4271 + 7919) % 10007) / 10007}. */
    static double impactR2(int k) {
        return ((k * 4271 + 7919) % 10007) / 10007.0;
    }

    /**
     * A brasa {@code k} do impacto na Porta de Energia em {@code d} (segundos desde a conjunção). Devolve {@code false}
     * fora da vida dela, que começa em {@code 0,58 + r1 × 0,25} e dura {@code 0,6 + r2 × 0,7}; senão escreve em
     * {@code out}: {@code {x, y, z}} relativos ao centro do impacto (px), a idade relativa {@code fv} (de 0 a 1) e o
     * tamanho (0, 1 ou 2, o terço da vida em que ela está).
     */
    static boolean impact(int k, double d, double[] out) {
        double r1 = impactR1(k);
        double r2 = impactR2(k);
        double vida = 0.6 + r2 * 0.7;
        double age = d - (0.58 + r1 * 0.25);
        if (age < 0 || age > vida) {
            return false;
        }
        double an = (r1 - 0.5) * 2.4;
        double sp = 7 + r2 * 9;
        double dist = sp * (1 - Math.exp(-age * 3)) / 3;
        out[0] = Math.cos(an) * dist + 0.5;
        out[1] = dist * (0.9 + r1) - 9 * age * age;
        out[2] = Math.sin(an) * dist;
        double fv = age / vida;
        out[3] = fv;
        out[4] = Math.min(2, Math.floor(fv * 3));
        return true;
    }

    /** A folha da brasa {@code k} do filme ({@code 'brasa' + (k % 3)}). */
    static FxSheet sheet(int k) {
        return SHEETS[k % 3];
    }

    /** As 12 brasas ao redor do sol: douradas, e coral claro a cada três (o {@code k % 3 ? OURO : coralHi} do filme). */
    static void draw(ArmilarPass p) {
        for (int k = 0; k < COUNT; k++) {
            orbit(k, p.t, p.prepEff, OFF);
            p.spr(SHEETS[k % 3], 0, OFF[0], ArmilarSpec.SOL_Y + OFF[1], OFF[2], 0.55 + 0.2 * (k % 2),
                    k % 3 != 0 ? ArmilarPass.OURO : ArmilarPass.CORAL_HI, p.ig * orbitAlpha(k, p.t), 0, 0);
        }
    }
}
