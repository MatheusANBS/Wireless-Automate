package io.github.matheusanbs.wirelessautomate.client.tv.image;

import io.github.matheusanbs.wirelessautomate.client.tv.render.TvPremultiply;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHeader;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * O leitor das imagens enviadas no cliente: o {@code STBImage} do LWJGL, o mesmo que o Minecraft usa. Antes de
 * decodificar, o cabeçalho é conferido duas vezes: pelo {@link TvImageHeader} (formato, lado até
 * {@link TvPicture#HARD_SIZE}, quadros) e pelo {@code stbi_info_from_memory}, que tem de dar o mesmo tamanho; assim um
 * arquivo que mente no cabeçalho é recusado aqui, sem alocar nada grande. Sai em RGBA de 8 bits com o alfa
 * pré-multiplicado ({@link TvPremultiply}, para os mipmaps não escurecerem a borda transparente). Roda fora da thread
 * de render (quem chama usa um executor).
 * <p>
 * O GIF animado sai de um de dois jeitos, conforme {@code ramRoom}, a memória reservada para ele no teto dos GIFs do
 * cliente: com todos os quadros montados por {@code stbi_load_gif_from_memory}, um atrás do outro e com os atrasos,
 * se couberem; senão só o primeiro quadro e um {@link TvGifReader}, que guarda o arquivo comprimido e abre os próximos
 * quadros um de cada vez.
 */
public final class TvImageDecoder {
    private TvImageDecoder() {
    }

    /**
     * Os pixels decodificados, na memória nativa do STB: o primeiro quadro (ou todos, um atrás do outro, num GIF
     * aberto inteiro) e, num GIF animado, os atrasos e talvez o leitor dos próximos. Solte com {@link #close}.
     */
    public static final class Decoded implements AutoCloseable {
        public final int width;
        public final int height;
        public final long nanos;
        /** Quantos quadros estão em {@link #address()}: 1, ou todos os do GIF aberto inteiro. */
        public final int frames;
        /** Os atrasos de cada quadro em ms (vazio fora do GIF animado). */
        public final int[] delays;
        /** O leitor dos próximos quadros, quando o GIF não coube inteiro (os pixels são só o primeiro). */
        public final @Nullable TvGifReader stream;
        private @Nullable ByteBuffer pixels;

        Decoded(int width, int height, ByteBuffer pixels, long nanos, int frames, int[] delays,
                @Nullable TvGifReader stream) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
            this.nanos = nanos;
            this.frames = frames;
            this.delays = delays;
            this.stream = stream;
        }

        /** O endereço dos pixels (RGBA, linha a linha, quadro a quadro), ou 0 depois de soltos. */
        public long address() {
            return pixels == null ? 0 : MemoryUtil.memAddress(pixels);
        }

        /** Se é um GIF animado (vários quadros na memória ou um leitor para os próximos). */
        public boolean animated() {
            return delays.length > 1;
        }

        /** Os bytes de todos os quadros guardados aqui. */
        public long bytes() {
            return (long) width * height * 4 * frames;
        }

        @Override
        public void close() {
            if (pixels != null) {
                STBImage.stbi_image_free(pixels);
                pixels = null;
            }
        }
    }

    /** Decodifica o primeiro quadro, ou {@code null} se o cabeçalho não vale, mente no tamanho ou o STB não abre. */
    public static @Nullable Decoded decode(byte[] data) {
        return decode(data, 0);
    }

    /**
     * Decodifica a imagem; um GIF animado vem inteiro se os quadros somam até {@code ramRoom} bytes, senão com o
     * leitor dos próximos. {@code null} se o cabeçalho não vale, mente no tamanho ou o STB não abre.
     */
    public static @Nullable Decoded decode(byte[] data, long ramRoom) {
        TvImageHeader.Result header = TvImageHeader.read(data, TvPicture.HARD_SIZE, TvPicture.HARD_FRAMES);
        if (!header.ok()) {
            return null;
        }
        TvImageHeader.Info info = header.info();
        long start = System.nanoTime();
        ByteBuffer in = MemoryUtil.memAlloc(data.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            in.put(data).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(in, w, h, comp) || w.get(0) != info.width()
                    || h.get(0) != info.height()) {
                return null;
            }
            long all = (long) info.width() * info.height() * 4 * info.frames();
            if (info.frames() > 1 && all <= ramRoom) {
                return allFrames(in, info, start, stack);
            }
            ByteBuffer pixels = STBImage.stbi_load_from_memory(in, w, h, comp, 4);
            if (pixels == null) {
                return null;
            }
            if (w.get(0) != info.width() || h.get(0) != info.height()) {
                STBImage.stbi_image_free(pixels);
                return null;
            }
            TvPremultiply.rgba(MemoryUtil.memAddress(pixels), (long) info.width() * info.height());
            int[] delays = new int[0];
            TvGifReader stream = null;
            if (info.frames() > 1) {
                delays = TvGifReader.delays(data, info.frames());
                stream = delays.length > 1 ? new TvGifReader(data) : null;
                if (stream != null) {
                    // O primeiro quadro já está aqui: o leitor fica pronto para o segundo.
                    stream.next();
                }
            }
            return new Decoded(info.width(), info.height(), pixels, System.nanoTime() - start, 1,
                    stream == null ? new int[0] : delays, stream);
        } finally {
            MemoryUtil.memFree(in);
        }
    }

    /** Todos os quadros do GIF pelo STB, com os atrasos; confere o tamanho e o número de quadros contra o cabeçalho. */
    private static @Nullable Decoded allFrames(ByteBuffer in, TvImageHeader.Info info, long start, MemoryStack stack) {
        PointerBuffer delaysOut = stack.mallocPointer(1);
        delaysOut.put(0, 0L);
        IntBuffer w = stack.mallocInt(1);
        IntBuffer h = stack.mallocInt(1);
        IntBuffer z = stack.mallocInt(1);
        IntBuffer comp = stack.mallocInt(1);
        ByteBuffer pixels = STBImage.stbi_load_gif_from_memory(in, delaysOut, w, h, z, comp, 4);
        long delaysAddress = delaysOut.get(0);
        int frames = z.get(0);
        int[] delays = new int[Math.max(0, frames)];
        if (delaysAddress != 0) {
            MemoryUtil.memIntBuffer(delaysAddress, delays.length).get(delays);
            STBImage.nstbi_image_free(delaysAddress);
        }
        if (pixels == null) {
            return null;
        }
        if (w.get(0) != info.width() || h.get(0) != info.height() || frames < 1 || frames > info.frames()) {
            STBImage.stbi_image_free(pixels);
            return null;
        }
        TvPremultiply.rgba(MemoryUtil.memAddress(pixels), (long) info.width() * info.height() * frames);
        return new Decoded(info.width(), info.height(), pixels, System.nanoTime() - start, frames,
                frames > 1 ? delays : new int[0], null);
    }
}
