package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Uma imagem enviada na biblioteca do jogador como vai ao cliente: o que o armazém sabe dela ({@link TvPicture}) e o
 * nome que o jogador deu, até {@link #MAX_NAME} letras. A biblioteca guarda só o resumo e o nome; o resto vem do
 * armazém na hora de mandar.
 */
public record TvLibraryPicture(TvPicture picture, String name) {
    public static final int MAX_NAME = 32;

    public static final Codec<TvLibraryPicture> CODEC = RecordCodecBuilder.create(i -> i.group(
            TvPicture.CODEC.fieldOf("picture").forGetter(TvLibraryPicture::picture),
            Codec.STRING.fieldOf("name").forGetter(TvLibraryPicture::name)).apply(i, TvLibraryPicture::new));
}
