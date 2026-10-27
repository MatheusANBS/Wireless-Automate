package io.github.matheusanbs.wirelessautomate.client.tv.render;

/**
 * Os pixels por bloco da textura de uma Tela, pelo tamanho dela: {@code 4096 / maior lado} entre 64 e 256, vezes a
 * qualidade da config do cliente ({@code tv.pixelsPerBlock}: 16 é metade, 32 o normal, 64 o dobro), com o teto de
 * {@link #MAX_SIDE} px no maior lado da textura e o piso de {@link #MIN_PPB} (o piso ganha do teto). Uma Tela
 * pequena desenha com mais pixels por bloco, para a letra sair nítida; o layout continua em 32 px de desenho por
 * bloco, só a escala do redesenho muda. Lógica pura, com JUnit.
 */
public final class TvResolution {
    public static final int MAX_SIDE = 4096;
    public static final int MIN_PPB = 32;
    private static final int BASE_MIN = 64;
    private static final int BASE_MAX = 256;
    private static final int NORMAL_QUALITY = 32;

    private TvResolution() {
    }

    public static int pixelsPerBlock(int width, int height, int quality) {
        int side = Math.max(1, Math.max(width, height));
        int base = Math.max(BASE_MIN, Math.min(BASE_MAX, MAX_SIDE / side));
        int scaled = base * Math.max(1, quality) / NORMAL_QUALITY;
        return Math.max(MIN_PPB, Math.min(scaled, MAX_SIDE / side));
    }
}
