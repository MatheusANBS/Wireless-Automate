package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A aparência de uma peça: cores ARGB, borda, tamanho do texto, alinhamento, ajuste ao quadro e o texto fixo. */
public record TvStyle(int color, int background, int border, int borderWidth, int size, TvAlign align, boolean fit,
        String text) {
    public static final int MAX_BORDER = 4;
    public static final int MAX_SIZE = 128;
    public static final int MAX_TEXT = 128;
    public static final TvStyle DEFAULT = new TvStyle(0xFFECE5D6, 0, 0, 0, 8, TvAlign.LEFT, true, "");

    public static final Codec<TvStyle> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("color", DEFAULT.color).forGetter(TvStyle::color),
            Codec.INT.optionalFieldOf("background", DEFAULT.background).forGetter(TvStyle::background),
            Codec.INT.optionalFieldOf("border", DEFAULT.border).forGetter(TvStyle::border),
            Codec.INT.optionalFieldOf("border_width", DEFAULT.borderWidth).forGetter(TvStyle::borderWidth),
            Codec.INT.optionalFieldOf("size", DEFAULT.size).forGetter(TvStyle::size),
            TvAlign.CODEC.optionalFieldOf("align", DEFAULT.align).forGetter(TvStyle::align),
            Codec.BOOL.optionalFieldOf("fit", DEFAULT.fit).forGetter(TvStyle::fit),
            Codec.STRING.optionalFieldOf("text", DEFAULT.text).forGetter(TvStyle::text)).apply(i, TvStyle::new));

    public TvStyle {
        borderWidth = TvText.clamp(borderWidth, 0, MAX_BORDER);
        size = TvText.clamp(size, 1, MAX_SIZE);
        text = TvText.cut(text, MAX_TEXT);
    }
}
