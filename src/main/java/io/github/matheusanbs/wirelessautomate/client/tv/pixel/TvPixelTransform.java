package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

/**
 * Virar na horizontal, na vertical e girar 90° no sentido horário uma grade de índices (a arte inteira ou o pedaço
 * solto da seleção). Girar troca a largura e a altura. Lógica pura, com JUnit.
 */
public enum TvPixelTransform {
    FLIP_H,
    FLIP_V,
    ROTATE;

    /** A grade {@code width} × {@code height} transformada, num array novo. */
    public byte[] apply(byte[] src, int width, int height) {
        byte[] out = new byte[src.length];
        int outW = this == ROTATE ? height : width;
        int outH = this == ROTATE ? width : height;
        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                out[y * outW + x] = switch (this) {
                    case FLIP_H -> src[y * width + (width - 1 - x)];
                    case FLIP_V -> src[(height - 1 - y) * width + x];
                    case ROTATE -> src[(height - 1 - x) * width + y];
                };
            }
        }
        return out;
    }
}
