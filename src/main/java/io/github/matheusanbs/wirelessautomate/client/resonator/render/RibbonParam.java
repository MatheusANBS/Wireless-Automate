package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * O parâmetro de vértice das fitas (UV2.y, um {@code short}, então tudo fica abaixo de 32768) e os modos do
 * {@code resonator_ribbon} (UV2.x). Feixe do Reparador ({@link #weave}): semente da linha nos bits 0 a 3, a força da
 * convergência de 0 a 7 nos bits 4 a 6 e o comprimento sobre a meia largura, em décimos, nos bits 8 a 14. Disco
 * ({@link #disc}): raio de 0 a 1 em 1/127 nos bits 0 a 6, anel no bit 7 e a espessura do anel de 0 a 1 em 1/127 nos bits
 * 8 a 14. Pura, com JUnit.
 */
public final class RibbonParam {
    /** O raio da câmara do Ressonador, aprovado: não muda. */
    public static final int MODE_BOLT = 0;
    /** O feixe mágico do Reparador: três fios em espiral com contas de luz. */
    public static final int MODE_WEAVE = 1;
    public static final int MODE_TENDRIL = 2;
    /** Um disco de luz: brilho redondo ou anel. */
    public static final int MODE_DISC = 3;

    private RibbonParam() {
    }

    private static int clamp(long value, int min, int max) {
        return (int) Math.max(min, Math.min(max, value));
    }

    /**
     * O parâmetro do feixe; {@code surge} é a força da convergência (0 a 7) e {@code aspectLength} o comprimento sobre a
     * meia largura vezes 0,35 (o shader divide por 0,35).
     */
    public static int weave(int seed, int surge, double aspectLength) {
        return (seed & 15) | clamp(surge, 0, 7) << 4 | clamp(Math.round(aspectLength * 10), 1, 127) << 8;
    }

    /** O parâmetro do disco: {@code radius} e {@code width} de 0 a 1. */
    public static int disc(double radius, boolean ring, double width) {
        return clamp(Math.round(radius * 127), 0, 127) | (ring ? 128 : 0) | clamp(Math.round(width * 127), 0, 127) << 8;
    }
}
