package io.github.matheusanbs.wirelessautomate.client.tv.image;

import io.github.matheusanbs.wirelessautomate.tv.TvImageHeader;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * O leitor das imagens enviadas no cliente: o {@code STBImage} do LWJGL, o mesmo que o Minecraft usa. Antes de
 * decodificar, o cabeçalho é conferido duas vezes: pelo {@link TvImageHeader} (formato, lado até
 * {@link TvPicture#HARD_SIZE}, quadros) e pelo {@code stbi_info_from_memory}, que tem de dar o mesmo tamanho; assim um
 * arquivo que mente no cabeçalho é recusado aqui, sem alocar nada grande. Sai em RGBA de 8 bits (no GIF, o primeiro
 * quadro). Roda fora da thread de render (quem chama usa um executor).
 */
public final class TvImageDecoder {
    private TvImageDecoder() {
    }

    /** Os pixels decodificados, na memória nativa do STB: solte com {@link #close}. */
    public static final class Decoded implements AutoCloseable {
        public final int width;
        public final int height;
        public final long nanos;
        private @Nullable ByteBuffer pixels;

        Decoded(int width, int height, ByteBuffer pixels, long nanos) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
            this.nanos = nanos;
        }

        /** O endereço dos pixels (RGBA, linha a linha), ou 0 depois de soltos. */
        public long address() {
            return pixels == null ? 0 : MemoryUtil.memAddress(pixels);
        }

        @Override
        public void close() {
            if (pixels != null) {
                STBImage.stbi_image_free(pixels);
                pixels = null;
            }
        }
    }

    /** Decodifica, ou {@code null} se o cabeçalho não vale, mente no tamanho ou o STB não abre. */
    public static @Nullable Decoded decode(byte[] data) {
        TvImageHeader.Result header = TvImageHeader.read(data, TvPicture.HARD_SIZE, TvPicture.HARD_FRAMES);
        if (!header.ok()) {
            return null;
        }
        long start = System.nanoTime();
        ByteBuffer in = MemoryUtil.memAlloc(data.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            in.put(data).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(in, w, h, comp) || w.get(0) != header.info().width()
                    || h.get(0) != header.info().height()) {
                return null;
            }
            ByteBuffer pixels = STBImage.stbi_load_from_memory(in, w, h, comp, 4);
            if (pixels == null) {
                return null;
            }
            if (w.get(0) != header.info().width() || h.get(0) != header.info().height()) {
                STBImage.stbi_image_free(pixels);
                return null;
            }
            return new Decoded(w.get(0), h.get(0), pixels, System.nanoTime() - start);
        } finally {
            MemoryUtil.memFree(in);
        }
    }
}
