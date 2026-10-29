package io.github.matheusanbs.wirelessautomate.tv;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * As contas puras da pixel art da Tela de Sinal (sem classes do jogo, com JUnit): os tamanhos aceitos, a validação
 * da paleta e dos índices e o empacotamento dos pixels em meio byte cada. Na memória a arte tem um índice da paleta
 * por pixel (um {@code byte} de 0 a 15); gravada, dois pixels por byte: o pixel par no meio byte de baixo e o ímpar
 * no de cima, e com um número ímpar de pixels o último meio byte fica 0. O {@link #hash} é o resumo do conteúdo que
 * o update tag da Tela leva no lugar dos pixels.
 */
public final class TvPixels {
    public static final int MIN_SIDE = 8;
    public static final int MAX_SIDE = 128;
    public static final int COLORS = 16;
    /** O índice transparente: a cor 0 da paleta nunca pinta. */
    public static final int TRANSPARENT = 0;

    private TvPixels() {
    }

    /** Se a largura e a altura estão entre {@link #MIN_SIDE} e {@link #MAX_SIDE}. */
    public static boolean validSize(int width, int height) {
        return width >= MIN_SIDE && width <= MAX_SIDE && height >= MIN_SIDE && height <= MAX_SIDE;
    }

    /** Quantos bytes ocupam {@code count} pixels gravados (dois por byte, arredondado para cima). */
    public static int packedLength(int count) {
        return (count + 1) / 2;
    }

    /** Os índices (um por byte, 0 a 15) em meio byte cada. */
    public static byte[] pack(byte[] indices) {
        byte[] packed = new byte[packedLength(indices.length)];
        for (int i = 0; i < indices.length; i++) {
            int nibble = indices[i] & 0x0F;
            packed[i >> 1] |= (byte) ((i & 1) == 0 ? nibble : nibble << 4);
        }
        return packed;
    }

    /** Os {@code count} índices de volta, ou {@code null} se o tamanho gravado não bate com a contagem. */
    public static byte[] unpack(byte[] packed, int count) {
        if (count < 0 || packed.length != packedLength(count)) {
            return null;
        }
        byte[] indices = new byte[count];
        for (int i = 0; i < count; i++) {
            int b = packed[i >> 1];
            indices[i] = (byte) ((i & 1) == 0 ? b & 0x0F : b >> 4 & 0x0F);
        }
        return indices;
    }

    /**
     * O resumo de 64 bits do conteúdo de uma arte (largura, altura, paleta e o índice de cada pixel; nem o id nem o
     * nome): os 8 primeiros bytes do SHA-256. É a chave das artes no cliente, então uma arte igual em várias Telas é
     * uma só; o SHA-256 (e não um hash rápido) para ninguém montar de propósito uma arte com o resumo de outra.
     * Calculado só quando o layout muda, nunca por tick.
     */
    public static long hash(int width, int height, int[] palette, byte[] pixels) {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        ByteBuffer head = ByteBuffer.allocate(8 + 4 * palette.length);
        head.putInt(width).putInt(height);
        for (int color : palette) {
            head.putInt(color);
        }
        sha.update(head.array());
        sha.update(pixels);
        return ByteBuffer.wrap(sha.digest()).getLong();
    }

    /**
     * O problema de uma arte na memória, ou {@code null} se ela vale: o tamanho, a paleta de {@link #COLORS} cores e
     * um índice de 0 a 15 por pixel.
     */
    public static String problem(int width, int height, int[] palette, byte[] pixels) {
        if (!validSize(width, height)) {
            return "tamanho fora de " + MIN_SIDE + " a " + MAX_SIDE + ": " + width + " × " + height;
        }
        if (palette == null || palette.length != COLORS) {
            return "a paleta precisa de " + COLORS + " cores";
        }
        if (pixels == null || pixels.length != width * height) {
            return "pixels a mais ou a menos";
        }
        for (byte pixel : pixels) {
            if (pixel < 0 || pixel >= COLORS) {
                return "índice fora da paleta: " + pixel;
            }
        }
        return null;
    }
}
