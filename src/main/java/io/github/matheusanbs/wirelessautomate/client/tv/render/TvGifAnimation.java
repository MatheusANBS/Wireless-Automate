package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.client.tv.image.TvGifClock;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvGifReader;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageDecoder;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * Os quadros de um GIF animado no cliente, de um de dois jeitos, decididos na decodificação pelo teto
 * {@link #RAM_BUDGET} somado de todos os GIFs ({@link #RAM}, reservado no pedido pelo {@link TvPictureTextures}):
 * <ul>
 * <li><b>na memória:</b> todos os quadros montados, um atrás do outro (o {@code stbi_load_gif_from_memory}); trocar
 * de quadro é só apontar para o próximo;</li>
 * <li><b>comprimidos:</b> só o arquivo e o {@link TvGifReader}, que abre um quadro de cada vez num executor, fora da
 * thread de render, para um buffer nativo de um quadro. O próximo quadro é aberto logo depois de o atual subir, para
 * estar pronto na hora dele; se o relógio pulou quadros, o leitor anda até o da vez.</li>
 * </ul>
 * O quadro da vez vem do {@link TvGifClock} pelo relógio do cliente; quem sobe para a textura é o
 * {@link TvPictureTextures}. Só a thread de render mexe nos campos; o executor só no leitor e no buffer, um quadro por
 * vez ({@link #decoding}).
 */
final class TvGifAnimation {
    /** O teto da memória dos GIFs com todos os quadros abertos, somados: acima dele, os próximos vão comprimidos. */
    static final long RAM_BUDGET = 256L << 20;
    /** A memória dos GIFs abertos inteiros e as reservas das decodificações em curso. */
    static final TvGifRam RAM = new TvGifRam(RAM_BUDGET);

    final TvGifClock clock;
    final int width;
    final int height;
    private final long frameBytes;
    /** Todos os quadros (no modo na memória), ou {@code null}. */
    private @Nullable TvImageDecoder.Decoded all;
    /** O leitor e o buffer de um quadro (no modo comprimido), ou {@code null}. */
    private final @Nullable TvGifReader stream;
    private @Nullable ByteBuffer ready;
    private int readyIndex = -1;
    private boolean decoding;
    private boolean failed;
    private boolean closed;
    /** O quadro que está na textura. */
    int shown;
    /** O último tick do cliente em que alguma Tela à vista, o editor ou uma miniatura mostrou o GIF. */
    int seenTick = Integer.MIN_VALUE;

    /** Da decodificação de um GIF animado: fica com os quadros (modo na memória) ou com o leitor. */
    TvGifAnimation(TvImageDecoder.Decoded decoded) {
        clock = new TvGifClock(decoded.delays);
        width = decoded.width;
        height = decoded.height;
        frameBytes = (long) width * height * 4;
        if (decoded.stream == null) {
            all = decoded;
            RAM.use(decoded.bytes());
            stream = null;
        } else {
            stream = decoded.stream;
            ready = MemoryUtil.memAlloc((int) frameBytes);
        }
    }

    /** Se os quadros estão todos na memória (a {@code Decoded} fica com a animação e não é solta depois de subir). */
    boolean ownsDecoded() {
        return all != null;
    }

    /**
     * O endereço dos pixels do quadro {@code index}, ou 0 se ele ainda não está aberto (no modo comprimido, começa a
     * abri-lo agora). Só na thread de render.
     */
    long pixels(int index) {
        if (all != null) {
            return index < all.frames ? all.address() + index * frameBytes : 0;
        }
        if (failed || ready == null) {
            return 0;
        }
        if (!decoding && readyIndex == index) {
            return MemoryUtil.memAddress(ready);
        }
        if (!decoding) {
            decode(index);
        }
        return 0;
    }

    /** O quadro {@code index} subiu: no modo comprimido, o próximo começa a abrir já. */
    void shown(int index) {
        shown = index;
        if (stream != null && !decoding && !failed) {
            decode((index + 1) % clock.frames());
        }
    }

    /** Abre o quadro {@code target} no executor: o leitor anda até ele (recomeça do primeiro se já passou). */
    private void decode(int target) {
        decoding = true;
        TvGifReader reader = stream;
        ByteBuffer out = ready;
        CompletableFuture.supplyAsync(() -> {
            if (reader.frame() > target) {
                reader.rewind();
            }
            while (reader.frame() < target && reader.next()) {
                // Monta os quadros até o pedido; os do meio não sobem.
            }
            if (reader.frame() != target) {
                return false;
            }
            int[] canvas = reader.canvas();
            long address = MemoryUtil.memAddress(out);
            for (int i = 0; i < canvas.length; i++) {
                MemoryUtil.memPutInt(address + i * 4L, TvPremultiply.color(canvas[i]));
            }
            return true;
        }, Util.backgroundExecutor()).whenComplete((ok, error) -> Minecraft.getInstance().execute(() -> {
            decoding = false;
            if (closed) {
                free();
                return;
            }
            if (ok != null && ok) {
                readyIndex = target;
            } else {
                // Um GIF que não abre além de um quadro fica parado no que está na textura.
                failed = true;
                readyIndex = -1;
            }
        }));
    }

    /** Solta a memória; no modo comprimido com um quadro abrindo, solta quando ele terminar. */
    void close() {
        if (all != null) {
            RAM.release(all.bytes());
            all.close();
            all = null;
        }
        closed = true;
        if (!decoding) {
            free();
        }
    }

    private void free() {
        if (ready != null) {
            MemoryUtil.memFree(ready);
            ready = null;
        }
    }

    /** A memória dos GIFs abertos inteiros e das reservas, somada (o e2e confere que volta a 0). */
    static long ramUsedForTests() {
        return RAM.used();
    }
}
