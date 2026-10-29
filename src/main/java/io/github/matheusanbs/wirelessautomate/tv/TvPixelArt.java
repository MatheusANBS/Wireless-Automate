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
 * <p>Os arrays são copiados ao construir e os acessores devolvem os da arte, sem copiar (o desenho e o editor os
 * leem muitas vezes): quem os recebe não os muda. {@code equals} e {@code hashCode} são pelo conteúdo.
 */
public record TvPixelArt(UUID id, String name, int width, int height, int[] palette, byte[] pixels) {
    public static final int MAX_NAME = 32;

    /** A forma gravada: os pixels ainda em meio byte, validados antes de virar arte. */
    private record Stored(UUID id, String name, int width, int height, IntStream palette, ByteBuffer pixels) {
        DataResult<TvPixelArt> art() {
            int[] colors = palette.toArray();
            byte[] packed = new byte[pixels.remaining()];
            pixels.duplicate().get(packed);
            if (!TvPixels.validSize(width, height)) {
                return DataResult.error(() -> "pixel art de tamanho inválido: " + width + " × " + height);
            }
            byte[] indices = TvPixels.unpack(packed, width * height);
            String problem = indices == null ? "pixels gravados a mais ou a menos"
                    : TvPixels.problem(width, height, colors, indices);
            if (problem != null) {
                return DataResult.error(() -> "pixel art inválida: " + problem);
            }
            return DataResult.success(new TvPixelArt(id, name, width, height, colors, indices));
        }

        static Stored of(TvPixelArt art) {
            return new Stored(art.id, art.name, art.width, art.height, IntStream.of(art.palette),
                    ByteBuffer.wrap(TvPixels.pack(art.pixels)));
        }
    }

    private static final Codec<Stored> STORED = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Stored::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Stored::name),
            Codec.INT.fieldOf("w").forGetter(Stored::width),
            Codec.INT.fieldOf("h").forGetter(Stored::height),
            Codec.INT_STREAM.fieldOf("palette").forGetter(Stored::palette),
            Codec.BYTE_BUFFER.fieldOf("pixels").forGetter(Stored::pixels)).apply(i, Stored::new));

    public static final Codec<TvPixelArt> CODEC = STORED.comapFlatMap(Stored::art, Stored::of);

    /** Lança {@code IllegalArgumentException} se o tamanho, a paleta ou os pixels não valem ({@link TvPixels#problem}). */
    public TvPixelArt {
        Objects.requireNonNull(id, "id");
        name = TvText.cut(name == null ? "" : name, MAX_NAME);
        String problem = TvPixels.problem(width, height, palette, pixels);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        palette = palette.clone();
        palette[TvPixels.TRANSPARENT] = 0;
        pixels = pixels.clone();
    }

    /** O índice da paleta no pixel ({@code x}, {@code y}). */
    public int pixel(int x, int y) {
        return pixels[y * width + x];
    }

    public int pixelCount() {
        return width * height;
    }

    /** O resumo do conteúdo ({@link TvPixels#hash}), calculado a cada chamada: guarde-o quando for usá-lo de novo. */
    public long contentHash() {
        return TvPixels.hash(width, height, palette, pixels);
    }

    public TvPixelArt withName(String name) {
        return new TvPixelArt(id, name, width, height, palette, pixels);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TvPixelArt art && art.width == width && art.height == height && art.id.equals(id)
                && art.name.equals(name) && Arrays.equals(art.palette, palette) && Arrays.equals(art.pixels, pixels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, width, height) * 31 * 31 + Arrays.hashCode(palette) * 31
                + Arrays.hashCode(pixels);
    }

    @Override
    public String toString() {
        return "TvPixelArt[" + id + ", " + name + ", " + width + " × " + height + "]";
    }
}
