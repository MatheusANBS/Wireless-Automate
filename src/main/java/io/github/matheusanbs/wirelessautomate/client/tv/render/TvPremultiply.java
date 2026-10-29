package io.github.matheusanbs.wirelessautomate.client.tv.render;

import org.lwjgl.system.MemoryUtil;

/**
 * O alfa pré-multiplicado das texturas das pixel arts e das imagens enviadas: cada canal de cor vezes o alfa, com
 * arredondamento. Com a cor assim, os mipmaps ({@code glGenerateMipmap}, que tira a média dos canais) somam a cor só
 * na proporção do alfa, e a borda de uma imagem transparente muito reduzida não puxa o preto dos pixels
 * transparentes. Roda na subida da arte e, na imagem enviada, no executor da decodificação, nunca por quadro.
 */
public final class TvPremultiply {
    private TvPremultiply() {
    }

    /**
     * Uma cor com o alfa no byte de cima e os três canais nos outros, em qualquer ordem (o ARGB da paleta ou o RGBA
     * do STB lido como {@code int} de ordem baixa primeiro), com o alfa pré-multiplicado; a opaca volta igual e a
     * transparente vira 0.
     */
    public static int color(int color) {
        int a = color >>> 24;
        if (a == 255) {
            return color;
        }
        if (a == 0) {
            return 0;
        }
        return a << 24 | times(color >> 16 & 0xFF, a) << 16 | times(color >> 8 & 0xFF, a) << 8 | times(color & 0xFF, a);
    }

    /** {@code count} pixels RGBA na memória nativa a partir de {@code address}, trocados no lugar. */
    public static void rgba(long address, long count) {
        for (long i = 0; i < count; i++) {
            long at = address + i * 4;
            int pixel = MemoryUtil.memGetInt(at);
            if (pixel >>> 24 != 255) {
                MemoryUtil.memPutInt(at, color(pixel));
            }
        }
    }

    private static int times(int channel, int alpha) {
        return (channel * alpha + 127) / 255;
    }
}
