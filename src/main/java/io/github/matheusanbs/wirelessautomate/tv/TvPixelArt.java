package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.UUIDUtil;

/**
 * Uma pixel art da Tela de Sinal: de 8 × 8 a 128 × 128 ({@link TvPixels#MIN_SIDE} e {@link TvPixels#MAX_SIDE}),
 * paleta de 16 cores ARGB (a cor 0 é sempre transparente) e um índice da paleta por pixel, linha a linha a partir de
 * cima à esquerda. Fica na biblioteca do jogador e, copiada, no layout da Tela que a usa ({@link TvLayout#images()});
 * o update tag da Tela leva só a referência dela ({@link TvArtRef}). Gravada, os pixels vão em meio byte cada
 * ({@link TvPixels#pack}) e a paleta como {@code int[]}; uma arte gravada que não vale (tamanho, paleta ou pixels) não
 * decodifica, e quem lê uma lista a larga sem derrubar as outras.
 *
 * <p>Uma arte animada tem {@code frames} quadros (até {@link TvPixels#MAX_FRAMES}), todos do mesmo tamanho e com a
 * mesma paleta, um depois do outro em {@code pixels} ({@link #frameOffset}), que tocam a {@code fps} quadros por
 * segundo no {@code mode}. As chaves {@code frames}, {@code fps} e {@code mode} são opcionais e só são gravadas fora
 * do padrão: a arte parada (um quadro) grava e resume ({@link #contentHash}) como antes dos quadros, e por isso um
 * quadro só fica sempre com a velocidade e o modo padrão.
 *
 * <p>Os arrays são copiados ao construir e os acessores devolvem os da arte, sem copiar (o desenho e o editor os
 * leem muitas vezes): quem os recebe não os muda. {@code equals} e {@code hashCode} são pelo conteúdo.
 */
public record TvPixelArt(UUID id, String name, int width, int height, int[] palette, byte[] pixels, int frames,
        int fps, TvPixels.Mode mode) {
    public static final int MAX_NAME = 32;

    /** A forma gravada: os pixels ainda em meio byte, validados antes de virar arte. */
    private record Stored(UUID id, String name, int width, int height, IntStream palette, ByteBuffer pixels,
            int frames, int fps, int mode) {
        DataResult<TvPixelArt> art() {
            int[] colors = palette.toArray();
            byte[] packed = new byte[pixels.remaining()];
            pixels.duplicate().get(packed);
            if (!TvPixels.validSize(width, height)) {
                return DataResult.error(() -> "pixel art de tamanho inválido: " + width + " × " + height);
            }
            TvPixels.Mode loop = TvPixels.Mode.of(mode);
            if (frames < 1 || frames > TvPixels.MAX_FRAMES || loop == null) {
                return DataResult.error(() -> "pixel art com quadros ou modo inválidos: " + frames + ", " + mode);
            }
            byte[] indices = TvPixels.unpack(packed, width * height * frames);
            String problem = indices == null ? "pixels gravados a mais ou a menos"
                    : TvPixels.problem(width, height, colors, indices, frames, fps);
            if (problem != null) {
                return DataResult.error(() -> "pixel art inválida: " + problem);
            }
            return DataResult.success(new TvPixelArt(id, name, width, height, colors, indices, frames, fps, loop));
        }

        static Stored of(TvPixelArt art) {
            return new Stored(art.id, art.name, art.width, art.height, IntStream.of(art.palette),
                    ByteBuffer.wrap(TvPixels.pack(art.pixels)), art.frames, art.fps, art.mode.ordinal());
        }
    }

    private static final Codec<Stored> STORED = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Stored::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Stored::name),
            Codec.INT.fieldOf("w").forGetter(Stored::width),
            Codec.INT.fieldOf("h").forGetter(Stored::height),
            Codec.INT_STREAM.fieldOf("palette").forGetter(Stored::palette),
            Codec.BYTE_BUFFER.fieldOf("pixels").forGetter(Stored::pixels),
            Codec.INT.optionalFieldOf("frames", 1).forGetter(Stored::frames),
            Codec.INT.optionalFieldOf("fps", TvPixels.DEFAULT_FPS).forGetter(Stored::fps),
            Codec.INT.optionalFieldOf("mode", 0).forGetter(Stored::mode)).apply(i, Stored::new));

    public static final Codec<TvPixelArt> CODEC = STORED.comapFlatMap(Stored::art, Stored::of);

    /**
     * Lança {@code IllegalArgumentException} se o tamanho, a paleta, os pixels, os quadros ou a velocidade não valem
     * ({@link TvPixels#problem}).
     */
    public TvPixelArt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mode, "mode");
        name = TvText.cut(name == null ? "" : name, MAX_NAME);
        String problem = TvPixels.problem(width, height, palette, pixels, frames, fps);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        palette = palette.clone();
        palette[TvPixels.TRANSPARENT] = 0;
        pixels = pixels.clone();
        if (frames == 1) {
            fps = TvPixels.DEFAULT_FPS;
            mode = TvPixels.Mode.LOOP;
        }
    }

    /** Uma arte parada, de um quadro. */
    public TvPixelArt(UUID id, String name, int width, int height, int[] palette, byte[] pixels) {
        this(id, name, width, height, palette, pixels, 1, TvPixels.DEFAULT_FPS, TvPixels.Mode.LOOP);
    }

    /** O índice da paleta no pixel ({@code x}, {@code y}) do primeiro quadro. */
    public int pixel(int x, int y) {
        return pixels[y * width + x];
    }

    /** Os pixels de todos os quadros: o que conta no teto {@code tv.maxImagePixels}. */
    public int pixelCount() {
        return width * height * frames;
    }

    /** Se tem mais de um quadro. */
    public boolean animated() {
        return frames > 1;
    }

    /** Onde o quadro {@code frame} começa em {@link #pixels}. */
    public int frameOffset(int frame) {
        return frame * width * height;
    }

    /** O quadro da vez no instante {@code nowMs} do relógio do cliente ({@link TvPixels#frameAt}). */
    public int frameAt(long nowMs) {
        return TvPixels.frameAt(nowMs, frames, fps, mode);
    }

    /** O resumo do conteúdo ({@link TvPixels#hash}), calculado a cada chamada: guarde-o quando for usá-lo de novo. */
    public long contentHash() {
        return TvPixels.hash(width, height, palette, pixels, frames, fps, mode);
    }

    public TvPixelArt withName(String name) {
        return new TvPixelArt(id, name, width, height, palette, pixels, frames, fps, mode);
    }

    /** A mesma arte, com os quadros, com outro id e outro nome (a cópia, o importar). */
    public TvPixelArt copy(UUID id, String name) {
        return new TvPixelArt(id, name, width, height, palette, pixels, frames, fps, mode);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TvPixelArt art && art.width == width && art.height == height && art.id.equals(id)
                && art.frames == frames && art.fps == fps && art.mode == mode && art.name.equals(name)
                && Arrays.equals(art.palette, palette) && Arrays.equals(art.pixels, pixels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, width, height, frames, fps, mode) * 31 * 31 + Arrays.hashCode(palette) * 31
                + Arrays.hashCode(pixels);
    }

    @Override
    public String toString() {
        return "TvPixelArt[" + id + ", " + name + ", " + width + " × " + height
                + (frames > 1 ? ", " + frames + " quadros" : "") + "]";
    }
}
