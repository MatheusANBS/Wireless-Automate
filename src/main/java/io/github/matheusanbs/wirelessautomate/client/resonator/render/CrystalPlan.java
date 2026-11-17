package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * As fatias do cristal: uma por linha de texel do {@code cristal_*.png}, para a recarga acender em degraus de 1 texel.
 * Alturas em px locais do cristal (o centro é a origem). Lógica pura.
 */
final class CrystalPlan {
    /**
     * As 12 alturas de corte, de baixo para cima: a ponta de baixo (3 linhas, v 12 a 15) de -3,4 a -1,5; o corpo
     * (6 linhas, v 8 a 2) de -1,5 a 1,5; a ponta de cima (2 linhas, v 2 a 0) de 1,5 a 3,4.
     */
    static final double[] CUTS = {-(ArmilarSpec.CRISTAL_H + ArmilarSpec.CRISTAL_PONTA), -2.76667, -2.13333, -1.5, -1.0,
            -0.5, 0.0, 0.5, 1.0, 1.5, 2.45, ArmilarSpec.CRISTAL_H + ArmilarSpec.CRISTAL_PONTA};
    private static final int ROWS = CUTS.length - 1;
    private static final int BAND_FIRST = 5;
    private static final int BAND_LAST = 6;

    private CrystalPlan() {
    }

    static int rows() {
        return ROWS;
    }

    /** Quantas linhas já acenderam (de 0 a 11) com a recarga em {@code progress}. */
    static int litRows(double progress) {
        return Math.min(ROWS, (int) Math.floor(progress * ROWS + 1e-9));
    }

    /** A faixa (a cinta do Lapidado): as linhas 5 e 6, de -0,5 a 0,5. */
    static boolean band(int row) {
        return row >= BAND_FIRST && row <= BAND_LAST;
    }

    /** A textura da linha {@code row}; {@code lit} é o número de linhas acesas (11 no vivo, 0 no Opaco ou rachado parado). */
    static CrystalLook.Tex rowTex(int row, byte code, int tier, int lit) {
        if (row < lit) {
            return rowTinted(row, code, tier, lit) ? CrystalLook.Tex.FAIXA : CrystalLook.liveTex(code);
        }
        return CrystalLook.bodyTex(code);
    }

    /** Se a linha é a faixa acesa do Afinado (Lapidado com {@code tier > 0}), a única tingida. */
    static boolean rowTinted(int row, byte code, int tier, int lit) {
        return row < lit && CrystalLook.cut(code) && tier > 0 && band(row);
    }
}
