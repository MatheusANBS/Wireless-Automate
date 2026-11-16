package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * As constantes do conceito Armilar, em px (1 px = 1/16 de bloco), tiradas de {@code W.armilar} (partes.js) e de
 * {@code armilar()} (gerar_conceitos.py). Lógica pura.
 */
public final class ArmilarSpec {
    /** O raio de cada órbita ({@code raios}). */
    public static final double[] RAIOS = {13, 19.5, 26};
    /** Os nós (engastes) de cada órbita ({@code nos}). */
    public static final int[] NOS = {2, 3, 3};
    /** Os segmentos do anel facetado de cada órbita ({@code malhas}). */
    public static final int[] SEGMENTOS = {24, 32, 40};
    /** A altura do sol ({@code sol}). */
    public static final double SOL_Y = 47.5;
    /** A volta dos anéis travados, em rad/s ({@code S}). */
    public static final double S = 1.2;
    /** A conjunção de referência, em s ({@code tc}). */
    public static final double TC = 11.0;
    /** A volta dos anéis travados, em s ({@code P} do {@code tau}). */
    public static final double PERIODO = 2 * Math.PI / S;

    /** A meia altura da seção do anel ({@code anelFacetado}: y de −1 a 1). */
    public static final double RING_HALF_Y = 1.0;
    /** A meia largura da seção do anel ({@code ri = R − 1,5}, {@code ro = R + 1,5}). */
    public static final double RING_HALF_W = 1.5;

    /** O engaste ({@code engaste}): a meia largura da base. */
    public static final double BASE = 1.4;
    public static final double TOPO_BASE = 1.6;
    public static final double GARRA_DE = 0.9;
    public static final double GARRA_ATE = 1.4;
    public static final double TOPO_GARRA = 3.0;
    public static final double CENTRO = 5.0;

    /** O cristal ({@code cristal(1.5, 1.5, 1.9)}). */
    public static final double CRISTAL_R = 1.5;
    public static final double CRISTAL_H = 1.5;
    public static final double CRISTAL_PONTA = 1.9;

    /** Os pinos ({@code pinos}): caixa de r + 1,5 a r + 3,5, meia seção 0,75, e o espelho em −x. */
    public static final double PINO_DE = 1.5;
    public static final double PINO_ATE = 3.5;
    public static final double PINO_MEIO = 0.75;

    /** O anel do horizonte ({@code anel16((0,0,0), "y", 30, 35, 46, 49, ...)}). */
    public static final double HORIZ_R_IN = 30;
    public static final double HORIZ_R_OUT = 35;
    public static final double HORIZ_Y0 = 46;
    public static final double HORIZ_Y1 = 49;
    public static final int HORIZ_SEGS = 16;

    /** Os marcos coral do horizonte ({@code marco_NN}, a cada 90°). */
    public static final double MARCO_X0 = 31.5;
    public static final double MARCO_X1 = 33.5;
    public static final double MARCO_Y0 = 49;
    public static final double MARCO_Y1 = 50;
    public static final double MARCO_Z = 1;

    /**
     * O cristal opaco inclinado, em graus: decisão do controlador, o maior ângulo medido em que o cristal encosta na
     * base sem atravessar as garras (a 20° elas entram nele).
     */
    public static final double OPACO_TILT_DEG = 8;
    /** A subida do cristal opaco inclinado, em px: nenhuma, subir o soltaria da base. */
    public static final double OPACO_LIFT_PX = 0;

    private ArmilarSpec() {
    }

    /** Tempo desde a última conjunção, de −P/2 a P/2 (o {@code tau} do filme). */
    public static double tau(double t) {
        double d = (t - TC) % PERIODO;
        return ((d + 1.5 * PERIODO) % PERIODO) - 0.5 * PERIODO;
    }
}
