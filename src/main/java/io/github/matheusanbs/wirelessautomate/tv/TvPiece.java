package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * Uma peça do layout: tipo, caixa em pixels de desenho (origem em cima à esquerda), ordem de agrupamento, nome,
 * ligação (só o Valor), formato e estilo.
 */
public record TvPiece(TvPieceType type, int x, int y, int width, int height, int group, String name,
        Optional<TvBinding> binding, TvFormat format, TvStyle style) {
    public static final int MAX_NAME = 32;

    public static final Codec<TvPiece> CODEC = RecordCodecBuilder.create(i -> i.group(
            TvPieceType.CODEC.fieldOf("type").forGetter(TvPiece::type),
            Codec.INT.optionalFieldOf("x", 0).forGetter(TvPiece::x),
            Codec.INT.optionalFieldOf("y", 0).forGetter(TvPiece::y),
            Codec.INT.optionalFieldOf("w", 1).forGetter(TvPiece::width),
            Codec.INT.optionalFieldOf("h", 1).forGetter(TvPiece::height),
            Codec.INT.optionalFieldOf("group", 0).forGetter(TvPiece::group),
            Codec.STRING.optionalFieldOf("name", "").forGetter(TvPiece::name),
            TvBinding.CODEC.optionalFieldOf("bind").forGetter(TvPiece::binding),
            TvFormat.CODEC.optionalFieldOf("format", TvFormat.DEFAULT).forGetter(TvPiece::format),
            TvStyle.CODEC.optionalFieldOf("style", TvStyle.DEFAULT).forGetter(TvPiece::style)).apply(i, TvPiece::new));

    public TvPiece {
        width = Math.max(1, width);
        height = Math.max(1, height);
        name = TvText.cut(name, MAX_NAME);
    }

    /** A mesma peça noutra caixa. */
    public TvPiece withBox(int x, int y, int width, int height) {
        return new TvPiece(type, x, y, width, height, group, name, binding, format, style);
    }
}
