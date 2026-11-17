package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.client.fx.FxRibbon;
import io.github.matheusanbs.wirelessautomate.client.fx.FxSheets;

/**
 * O arco da conjunção, o {@code // o arco:} do filme ({@code partes.js}): do horizonte até a Porta de Energia, uma
 * tira de raio com trilhos escuros por baixo (tinta) e pacotes de luz correndo, com uma cabeça que avança e uma cauda
 * que se apaga atrás dela. A vazão multiplica o alfa e a largura: com vazão 0 não há arco.
 */
final class ArmilarArc {
    static final int POINTS = 61;
    private static final int STEPS = POINTS - 1;
    /** Depois disso a cabeça já chegou ao fim e some (o {@code h < 0,995} do filme). */
    private static final double HEAD_END = 0.995;
    /** Os quatro controles da bezier, em px locais; o último é o fim do arco e depende do deslocamento da Porta. */
    private static final float[] CTL = {33.6f, 46.6f, 0, 46, 44, 0, 49, 17, 0, 0, 0, 0};
    /** Quadros do raio por segundo (o {@code qd('raio', 8, t, 12)} do filme) e da estrela da cabeça. */
    private static final double RAIO_FPS = 12;
    private static final double HEAD_FPS = 10;
    /** A cabeça e o ponto quatro amostras atrás; rascunhos só da thread de render. */
    private static final double[] HEAD = new double[3];
    private static final double[] TAIL = new double[3];
    private static final double[] MID = new double[3];

    private ArmilarArc() {
    }

    /**
     * Os 61 pontos do arco em px locais, com a ondulação {@code (0,3 w sin(f·6 + t·1,7), 0,2 w sin(f·5 − t·1,3),
     * 0,7 w sin(f·5 + t·1,9))}, {@code w = sin(fπ)}: some nas pontas. O último controle é o fim do arco na Porta
     * ({@link ArmilarSpec#PORTA_ARCO_FIM}; sem o deslocamento do jogo é o número do filme, 42,8). Devolve {@link #POINTS}.
     */
    static int fill(double t, FxRibbon.Buffer b) {
        CTL[9] = (float) ArmilarSpec.PORTA_ARCO_FIM[0];
        CTL[10] = (float) ArmilarSpec.PORTA_ARCO_FIM[1];
        CTL[11] = (float) ArmilarSpec.PORTA_ARCO_FIM[2];
        FxRibbon.bezier(CTL, STEPS, b);
        for (int j = 0; j <= STEPS; j++) {
            double f = j / (double) STEPS;
            double w = Math.sin(f * Math.PI);
            b.pts[j * 3] += (float) (0.3 * w * Math.sin(f * 6 + t * 1.7));
            b.pts[j * 3 + 1] += (float) (0.2 * w * Math.sin(f * 5 - t * 1.3));
            b.pts[j * 3 + 2] += (float) (0.7 * w * Math.sin(f * 5 + t * 1.9));
        }
        return POINTS;
    }

    /** O {@code ate(f)} do filme: 1 bem antes da cabeça {@code head}, 0 daí em diante, a rampa nos 0,05 antes dela. */
    static double tail(double head, double f) {
        return 1 - Ease.ss(head - 0.05, head, f);
    }

    static void draw(ArmilarPass p) {
        double arco = p.phase.arco;
        double k = p.flowK;
        if (arco <= 0.002 || k <= 0) {
            return;
        }
        double t = p.t;
        int dis = p.disMilli();
        FxRibbon.Buffer b = p.local;
        fill(t, b);
        double h = p.phase.cabeca;
        // a tinta: dois trilhos escuros por baixo
        shape(b, 1.7 * k, arco * k, h, 1);
        p.ribbon(FxSheets.TRILHOS, 0, b, POINTS, ArmilarPass.WHITE, 12, t * 1.5, dis);
        // a luz: a borda dos trilhos, o raio desenhado e o fluxo de pacotes
        shape(b, 1.7 * k, arco * k, h, 0.8);
        p.ribbon(FxSheets.TRILHOS_RIM, 0, b, POINTS, ArmilarPass.WHITE, 12, t * 1.5, dis);
        shape(b, 1.5 * k, arco * k, h, 1);
        p.ribbon(FxSheets.RAIO, FxSheets.RAIO.frameAt(t, RAIO_FPS), b, POINTS, ArmilarPass.WHITE, 14, t * 0.6, dis);
        shape(b, 0.9 * k, arco * k, h, 1);
        p.ribbon(FxSheets.FLUXO, 0, b, POINTS, ArmilarPass.WHITE, 10, t * 4, dis);
        if (h < HEAD_END) {
            head(p, b, h, arco);
        }
    }

    /** A meia largura e o alfa de cada amostra: {@code alfa × tail(h, f) × mult}. */
    private static void shape(FxRibbon.Buffer b, double halfWidth, double alpha, double h, double mult) {
        for (int j = 0; j < POINTS; j++) {
            b.w[j] = (float) halfWidth;
            b.a[j] = (float) (alpha * mult * tail(h, j / (double) STEPS));
        }
    }

    /** A cabeça do raio: a estrela, três brasas na cauda e o risco que aponta o movimento na tela. */
    private static void head(ArmilarPass p, FxRibbon.Buffer b, double h, double arco) {
        int ih = (int) Math.round(h * STEPS);
        int it = Math.max(0, ih - 4);
        for (int c = 0; c < 3; c++) {
            HEAD[c] = b.pts[ih * 3 + c];
            TAIL[c] = b.pts[it * 3 + c];
        }
        p.spr(FxSheets.ESTRELA, FxSheets.ESTRELA.frameAt(p.t, HEAD_FPS), HEAD[0], HEAD[1], HEAD[2], 3.2,
                ArmilarPass.CLARO, arco, p.t, 0);
        for (int j = 1; j <= 3; j++) {
            mix(j / 3.0);
            p.spr(ArmilarEmbers.sheet(j), 0, MID[0], MID[1], MID[2], 0.6, ArmilarPass.OURO, arco * (1 - j / 4.0), 0, 0);
        }
        mix(0.5);
        p.spr(FxSheets.RISCO_L, 0, MID[0], MID[1], MID[2], 2, ArmilarPass.CLARO, 0.8 * arco,
                p.screenAngle(HEAD, TAIL), 0);
    }

    /** O ponto entre a cabeça e a cauda ({@code mix(hp, tr, f)} do filme) em {@link #MID}. */
    private static void mix(double f) {
        for (int c = 0; c < 3; c++) {
            MID[c] = HEAD[c] + (TAIL[c] - HEAD[c]) * f;
        }
    }
}
