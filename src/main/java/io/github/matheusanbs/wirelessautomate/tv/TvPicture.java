package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Uma imagem enviada (PNG, JPG ou GIF) como o layout e a biblioteca a apontam: o resumo ({@link TvImageHash}), o
 * formato, o tamanho, os quadros (1 fora do GIF animado) e quem a enviou. Os bytes ficam no armazém do mundo
 * ({@link TvImageStore}) e vão ao cliente à parte; o layout leva só isto, umas dezenas de bytes. O servidor nunca
 * confia no que o cliente manda aqui: ao salvar, troca pelo que o armazém tem ({@link TvImageStore#picture}). O id na
 * peça Imagem e no fundo em mosaico é {@link #id()}, que nunca colide com o de uma pixel art.
 */
public record TvPicture(String hash, TvImageFormat format, int width, int height, int frames,
        Optional<UUID> author) {
    /** Teto de lado de qualquer imagem, o máximo da config {@code tv.images.maxSize}. */
    public static final int HARD_SIZE = 2048;
    /** Teto de quadros de qualquer GIF, o máximo da config {@code tv.images.maxFrames}. */
    public static final int HARD_FRAMES = 1000;

    private static final Codec<String> HASH = Codec.STRING.validate(hash -> TvImageHash.valid(hash)
            ? DataResult.success(hash) : DataResult.error(() -> "resumo de imagem inválido"));
    private static final Codec<TvImageFormat> FORMAT = Codec.STRING.comapFlatMap(ext -> {
        TvImageFormat format = TvImageFormat.byExt(ext);
        return format == null ? DataResult.error(() -> "formato de imagem desconhecido: " + ext)
                : DataResult.success(format);
    }, TvImageFormat::ext);

    public static final Codec<TvPicture> CODEC = RecordCodecBuilder.<TvPicture>create(i -> i.group(
            HASH.fieldOf("hash").forGetter(TvPicture::hash),
            FORMAT.fieldOf("format").forGetter(TvPicture::format),
            Codec.INT.fieldOf("w").forGetter(TvPicture::width),
            Codec.INT.fieldOf("h").forGetter(TvPicture::height),
            Codec.INT.optionalFieldOf("frames", 1).forGetter(TvPicture::frames),
            UUIDUtil.CODEC.optionalFieldOf("author").forGetter(TvPicture::author))
            .apply(i, TvPicture::new)).validate(picture -> picture.valid() ? DataResult.success(picture)
                    : DataResult.error(() -> "imagem com tamanho inválido"));

    /** O id que a peça Imagem e o mosaico guardam. */
    public UUID id() {
        return TvImageHash.idOf(hash);
    }

    public boolean animated() {
        return frames > 1;
    }

    private boolean valid() {
        return width >= 1 && height >= 1 && width <= HARD_SIZE && height <= HARD_SIZE && frames >= 1
                && frames <= HARD_FRAMES;
    }
}
