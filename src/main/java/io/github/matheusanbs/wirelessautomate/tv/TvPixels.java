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
 * <p>
 * Uma arte animada tem de 1 a {@link #MAX_FRAMES} quadros do mesmo tamanho, um depois do outro no mesmo array, que
 * tocam a {@link #MIN_FPS}–{@link #MAX_FPS} quadros por segundo em {@link Mode#LOOP} ou {@link Mode#PINGPONG}; o
 * quadro da vez sai do relógio ({@link #frameAt}), o mesmo zero para todas as Telas.
 */
public final class TvPixels {
    public static final int MIN_SIDE = 8;
    public static final int MAX_SIDE = 128;
    public static final int COLORS = 16;
    /** O índice transparente: a cor 0 da paleta nunca pinta. */
    public static final int TRANSPARENT = 0;
    public static final int MAX_FRAMES = 64;
    public static final int MIN_FPS = 1;
    public static final int MAX_FPS = 20;
    public static final int DEFAULT_FPS = 6;

    /** Como a animação recomeça: do primeiro quadro, ou voltando pelo caminho (vai e volta). */
    public enum Mode {
        LOOP, PINGPONG;

        private static final Mode[] ALL = values();

        /** O modo gravado ({@link #ordinal}), ou {@code null} fora da lista. */
        public static Mode of(int stored) {
            return stored >= 0 && stored < ALL.length ? ALL[stored] : null;
        }
    }

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
        return hash(width, height, palette, pixels, 1, DEFAULT_FPS, Mode.LOOP);
    }

    /**
     * O {@link #hash} de uma arte com quadros: os pixels já trazem todos; o número de quadros, a velocidade e o modo
     * entram no fim só numa arte animada, então o resumo de uma arte parada é o mesmo de antes dos quadros.
     */
    public static long hash(int width, int height, int[] palette, byte[] pixels, int frames, int fps, Mode mode) {
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
        if (frames > 1) {
            sha.update(ByteBuffer.allocate(12).putInt(frames).putInt(fps).putInt(mode.ordinal()).array());
        }
        return ByteBuffer.wrap(sha.digest()).getLong();
    }

    /**
     * O quadro da vez no instante {@code nowMs} do relógio do cliente: um passo a cada {@code 1000 / fps} ms; no
     * {@link Mode#LOOP} volta ao primeiro, no {@link Mode#PINGPONG} volta pelo caminho sem repetir as pontas
     * (0 1 2 1 0 1 2...).
     */
    public static int frameAt(long nowMs, int frames, int fps, Mode mode) {
        if (frames <= 1) {
            return 0;
        }
        long step = Math.floorDiv(nowMs * fps, 1000L);
        if (mode == Mode.LOOP) {
            return (int) Math.floorMod(step, (long) frames);
        }
        int period = 2 * frames - 2;
        int k = (int) Math.floorMod(step, (long) period);
        return k < frames ? k : period - k;
    }

    /** O instante, em ms, em que o quadro de uma animação a {@code fps} muda depois de {@code nowMs}. */
    public static long nextChange(long nowMs, int fps) {
        long step = Math.floorDiv(nowMs * fps, 1000L) + 1;
        return Math.floorDiv(step * 1000L + fps - 1, (long) fps);
    }

    /**
     * O problema de uma arte na memória, ou {@code null} se ela vale: o tamanho, a paleta de {@link #COLORS} cores e
     * um índice de 0 a 15 por pixel.
     */
    public static String problem(int width, int height, int[] palette, byte[] pixels) {
        return problem(width, height, palette, pixels, 1, DEFAULT_FPS);
    }

    /**
     * O {@link #problem} de uma arte com {@code frames} quadros a {@code fps}: de 1 a {@link #MAX_FRAMES} quadros, de
     * {@link #MIN_FPS} a {@link #MAX_FPS} por segundo e os pixels de todos os quadros, um depois do outro.
     */
    public static String problem(int width, int height, int[] palette, byte[] pixels, int frames, int fps) {
        if (frames < 1 || frames > MAX_FRAMES) {
            return "quadros fora de 1 a " + MAX_FRAMES + ": " + frames;
        }
        if (fps < MIN_FPS || fps > MAX_FPS) {
            return "velocidade fora de " + MIN_FPS + " a " + MAX_FPS + ": " + fps;
        }
        if (!validSize(width, height)) {
            return "tamanho fora de " + MIN_SIDE + " a " + MAX_SIDE + ": " + width + " × " + height;
        }
        if (palette == null || palette.length != COLORS) {
            return "a paleta precisa de " + COLORS + " cores";
        }
        if (pixels == null || pixels.length != width * height * frames) {
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
