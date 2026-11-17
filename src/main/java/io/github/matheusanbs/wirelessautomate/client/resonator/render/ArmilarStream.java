package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.client.fx.FxRibbon;
import io.github.matheusanbs.wirelessautomate.client.fx.FxSheets;

/**
 * O fluxo base: o caminho da energia aceso sem parar enquanto sai energia da Porta de Energia, com ou sem sintonia e
 * fora da janela da conjunção. É o feixe do sol ao horizonte, o arco do horizonte até a Porta (com a ondulação do
 * filme) e o brilho no pé da Porta: o sigilo, o aro que se abre e um fio de brasas. A força {@code k}
 * ({@link ArmilarFlow#strength}, da faixa suavizada) escala a largura e o alfa das fitas, a velocidade da UV
 * ({@link ArmilarPass#fc}) e quantas brasas nascem. Acende e apaga com a faixa suavizada, por alfa: nenhum corte. A
 * conjunção ({@link ArmilarBeam}, {@link ArmilarArc}, {@link ArmilarImpact}) é desenhada depois, por cima.
 */
final class ArmilarStream {
    private static final int BEAM_POINTS = 25;
    private static final int BEAM_STEPS = BEAM_POINTS - 1;
    /** O sigilo e o aro do pé da Porta, no alfa baixo: fração do alfa da base. */
    private static final double FOOT_SIGIL = 0.45;
    private static final double FOOT_RING = 0.4;
    /** O aro do pé se abre e some a cada {@link #RING_SECONDS} do relógio do fluxo (menos de 1 Hz: nada pisca). */
    private static final double RING_SECONDS = 1.8;
    /** A fração do ciclo em que o aro entra. */
    private static final double RING_FADE_IN = 0.1;
    /** As brasas do pé: o máximo, o ciclo de cada uma (cobre os 2,13 s da vida) e o passo que as espalha (razão áurea). */
    static final int EMBERS = 16;
    private static final double EMBER_CYCLE = 2.2;
    private static final double SPREAD = 0.6180339887;
    /** As brasas da base sorteiam com índices depois das 16 do pulso, para não repetir o desenho delas. */
    private static final int EMBER_SEED = 16;
    private static final double[] EMBER_HALF = {0.9, 0.7, 0.5};
    private static final double[] AXIS_X = {1, 0, 0};
    private static final double[] AXIS_Y = {0, 1, 0};
    private static final double[] AXIS_Z = {0, 0, 1};
    private static final double[] OUT = new double[5];

    private ArmilarStream() {
    }

    static void draw(ArmilarPass p) {
        double k = p.flowK;
        if (k <= 0) {
            return;
        }
        double alpha = ArmilarFlow.baseAlpha(k);
        double width = ArmilarFlow.baseWidth(k);
        if (alpha <= 0) {
            return;
        }
        beam(p, alpha, width);
        arc(p, alpha, width);
        foot(p, k, alpha);
    }

    /** O feixe do sol ao horizonte, inteiro: os dois trilhos e o fluxo, correndo pelo relógio do fluxo. */
    private static void beam(ArmilarPass p, double alpha, double width) {
        double[] pa = ArmilarSpec.HORIZ_BURST;
        FxRibbon.Buffer b = p.local;
        for (int j = 0; j < BEAM_POINTS; j++) {
            double f = j / (double) BEAM_STEPS;
            b.pts[j * 3] = (float) (pa[0] * f);
            b.pts[j * 3 + 1] = (float) (ArmilarSpec.SOL_Y + (pa[1] - ArmilarSpec.SOL_Y) * f);
            b.pts[j * 3 + 2] = (float) (pa[2] * f);
        }
        double fc = p.fc;
        fill(b, BEAM_POINTS, 1.6 * width, alpha);
        p.ribbon(FxSheets.TRILHOS, 0, b, BEAM_POINTS, ArmilarPass.WHITE, 12, fc * 2, 0);
        fill(b, BEAM_POINTS, 1.6 * width, 0.8 * alpha);
        p.ribbon(FxSheets.TRILHOS_RIM, 0, b, BEAM_POINTS, ArmilarPass.WHITE, 12, fc * 2, 0);
        fill(b, BEAM_POINTS, 1.1 * width, alpha);
        p.ribbon(FxSheets.FLUXO, 0, b, BEAM_POINTS, ArmilarPass.WHITE, 10, fc * 3.2, 0);
    }

    /** O arco do horizonte à Porta: os mesmos quatro desenhos do filme, com a ondulação pelo relógio do mundo. */
    private static void arc(ArmilarPass p, double alpha, double width) {
        FxRibbon.Buffer b = p.local;
        int n = ArmilarArc.fill(p.t, b);
        double fc = p.fc;
        fill(b, n, 1.7 * width, alpha);
        p.ribbon(FxSheets.TRILHOS, 0, b, n, ArmilarPass.WHITE, 12, fc * 1.5, 0);
        fill(b, n, 1.7 * width, 0.8 * alpha);
        p.ribbon(FxSheets.TRILHOS_RIM, 0, b, n, ArmilarPass.WHITE, 12, fc * 1.5, 0);
        fill(b, n, 1.5 * width, alpha);
        p.ribbon(FxSheets.RAIO, FxSheets.RAIO.frameAt(p.t, ArmilarArc.RAIO_FPS), b, n, ArmilarPass.WHITE, 14, fc * 0.6, 0);
        fill(b, n, 0.9 * width, alpha);
        p.ribbon(FxSheets.FLUXO, 0, b, n, ArmilarPass.WHITE, 10, fc * 4, 0);
    }

    /** O pé da Porta: o sigilo parado, o aro que se abre em laço e as brasas que nascem, tantas quantas a força der. */
    private static void foot(ArmilarPass p, double k, double alpha) {
        double[] porta = ArmilarSpec.PORTA_IMPACTO;
        double fc = p.fc;
        // o sigilo na face da Porta, no último quadro, girando devagar
        p.dec(FxSheets.SIGILO, FxSheets.SIGILO.frameByFraction(1), porta[0] + 0.2, porta[1], porta[2], AXIS_Z, AXIS_Y,
                5.5, ArmilarPass.WHITE, FOOT_SIGIL * alpha, fc * 0.35, 0);
        // o aro de choque no chão: abre e some em laço, com o alfa caindo até 0 no fim do ciclo
        double r = Ease.frac(fc / RING_SECONDS);
        p.dec(FxSheets.CHOQUE, FxSheets.CHOQUE.frameByFraction(r), 47 + ArmilarSpec.PORTA_DESLOC_X, 0.12, 0, AXIS_X,
                AXIS_Z, 11, ArmilarPass.OURO, FOOT_RING * alpha * ringAlpha(r), 0, 0);
        embers(p, k, alpha, porta);
    }

    /** O alfa do aro em laço na fração {@code r} do ciclo: entra suave nos primeiros 10% e cai a 0 no fim, sem estalo na volta. */
    static double ringAlpha(double r) {
        return Ease.ss(0, RING_FADE_IN, r) * (1 - r) * (1 - r);
    }

    /**
     * As brasas do pé: cada uma percorre a vida do impacto em laço ({@link ArmilarEmbers#impact}), escalonadas pela
     * razão áurea; o peso {@code 16 k − i} (de 0 a 1) acende a brasa {@code i} aos poucos conforme a força sobe, e
     * nascer e morrer são por alfa.
     */
    private static void embers(ArmilarPass p, double k, double alpha, double[] porta) {
        double want = EMBERS * k;
        for (int i = 0; i < EMBERS; i++) {
            double weight = Math.min(1, want - i);
            if (weight <= 0) {
                break;
            }
            double d = EMBER_CYCLE * Ease.frac(p.fc / EMBER_CYCLE + i * SPREAD);
            if (!ArmilarEmbers.impact(EMBER_SEED + i, d, OUT)) {
                continue;
            }
            double fv = OUT[3];
            int size = (int) OUT[4];
            double fade = Ease.ss(0, 0.12, fv) * (1 - Ease.ss(0.65, 1, fv));
            p.spr(ArmilarEmbers.sheet(2 - size), 0, porta[0] + OUT[0], porta[1] + OUT[1], porta[2] + OUT[2],
                    EMBER_HALF[size], fv < 0.33 ? ArmilarPass.CLARO : fv < 0.66 ? ArmilarPass.OURO : ArmilarPass.CORAL,
                    alpha * weight * fade, 0, 0);
        }
    }

    /** A meia largura e o alfa iguais nas {@code n} amostras. */
    private static void fill(FxRibbon.Buffer b, int n, double halfWidth, double alpha) {
        for (int j = 0; j < n; j++) {
            b.w[j] = (float) halfWidth;
            b.a[j] = (float) alpha;
        }
    }
}
