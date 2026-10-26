package io.github.matheusanbs.wirelessautomate.client.repairer.render;

/**
 * Onde ficam as peças do Reparador, em pixels do modelo (0 a 16), lidas do .bbmodel aprovado (o modelo olha para o
 * norte). O facing gira os pontos em quartos de volta no sentido horário visto de cima, em torno de (8, 8), como o
 * "y" do blockstate; as tabelas por quarto são montadas uma vez. A linha i é o pedestal ped_i, a 22,5° + 45°·i; a runa
 * r fica a 45°·r e o ponto d a 22,5° + 45°·d (ângulos de atan2(z − 8, x − 8)). Puro, com JUnit.
 */
public final class RepairerGeometry {
    public static final float CENTER = 8f;
    public static final float EMITTER_Y = 11.5f;
    public static final float EMITTER_RADIUS = 1.1f;
    public static final float PEDESTAL_TOP_Y = 7.2f;
    public static final float RUNE_Y = 6.62f;
    public static final float DOT_Y = 6.64f;
    public static final float PEDESTAL_GLOW_Y = 7.22f;
    public static final float FLOOR_Y = 6.70f;
    private static final float[] PEDESTALS = {11.69552f, 9.53073f, 9.53073f, 11.69552f, 6.46927f, 11.69552f,
        4.30448f, 9.53073f, 4.30448f, 6.46927f, 6.46927f, 4.30448f, 9.53073f, 4.30448f, 11.69552f, 6.46927f};
    private static final float[] RUNES = {13.2f, 8f, 11.67696f, 11.67696f, 8f, 13.2f, 4.32304f, 11.67696f, 2.8f, 8f,
        4.32304f, 4.32304f, 8f, 2.8f, 11.67696f, 4.32304f};
    private static final float[] DOTS = {12.89656f, 10.02822f, 10.02822f, 12.89656f, 5.97178f, 12.89656f, 3.10344f,
        10.02822f, 3.10344f, 5.97178f, 5.97178f, 3.10344f, 10.02822f, 3.10344f, 12.89656f, 5.97178f};
    private static final float[][] PED_BY_QUARTER = byQuarter(PEDESTALS);
    private static final float[][] RUNE_BY_QUARTER = byQuarter(RUNES);
    private static final float[][] DOT_BY_QUARTER = byQuarter(DOTS);

    private RepairerGeometry() {
    }

    /** O quarto de volta pelo {@code Direction.get2DDataValue()} (sul 0, oeste 1, norte 2, leste 3): norte 0, leste 1, sul 2, oeste 3. */
    public static int quarterFrom2D(int data2d) {
        return (data2d + 2) & 3;
    }

    /** Gira (x, z) {@code quarter} quartos de volta em torno do centro; escreve em {@code out}. */
    public static void rotate(float x, float z, int quarter, float[] out) {
        float dx = x - CENTER;
        float dz = z - CENTER;
        for (int q = 0; q < (quarter & 3); q++) {
            float t = dx;
            dx = -dz;
            dz = t;
        }
        out[0] = CENTER + dx;
        out[1] = CENTER + dz;
    }

    private static float[][] byQuarter(float[] north) {
        float[][] table = new float[4][north.length];
        float[] p = new float[2];
        for (int q = 0; q < 4; q++) {
            for (int i = 0; i < north.length; i += 2) {
                rotate(north[i], north[i + 1], q, p);
                table[q][i] = p[0];
                table[q][i + 1] = p[1];
            }
        }
        return table;
    }

    public static float pedestalX(int quarter, int line) {
        return PED_BY_QUARTER[quarter & 3][2 * line];
    }

    public static float pedestalZ(int quarter, int line) {
        return PED_BY_QUARTER[quarter & 3][2 * line + 1];
    }

    public static float runeX(int quarter, int rune) {
        return RUNE_BY_QUARTER[quarter & 3][2 * rune];
    }

    public static float runeZ(int quarter, int rune) {
        return RUNE_BY_QUARTER[quarter & 3][2 * rune + 1];
    }

    public static float dotX(int quarter, int dot) {
        return DOT_BY_QUARTER[quarter & 3][2 * dot];
    }

    public static float dotZ(int quarter, int dot) {
        return DOT_BY_QUARTER[quarter & 3][2 * dot + 1];
    }

    /** A distância angular (0 a π) da runa {@code rune} ao pedestal da linha {@code line}. */
    public static double runeDistance(int rune, int line) {
        return angle(45.0 * rune, 22.5 + 45.0 * line);
    }

    /** A distância angular (0 a π) do ponto {@code dot} ao pedestal da linha {@code line}. */
    public static double dotDistance(int dot, int line) {
        return angle(22.5 + 45.0 * dot, 22.5 + 45.0 * line);
    }

    private static double angle(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return Math.toRadians(d > 180 ? 360 - d : d);
    }
}
