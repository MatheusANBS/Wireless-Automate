package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A aparência de uma peça: cores (ARGB ou papel do tema, ver {@link TvColor}), borda, tamanho do texto,
 * alinhamento, ajuste ao quadro, o texto fixo, o raio dos cantos, a opacidade e a fonte.
 */
public record TvStyle(int color, int background, int border, int borderWidth, int size, TvAlign align, boolean fit,
        String text, int radius, int opacity, TvFont font) {
    public static final int MAX_RADIUS = 8;
    public static final int MAX_BORDER = 4;
    public static final int MAX_SIZE = 128;
    public static final int MAX_TEXT = 128;
    public static final TvStyle DEFAULT = new TvStyle(0xFFECE5D6, 0, 0, 0, 8, TvAlign.LEFT, true, "", 0, 100,
            TvFont.MINECRAFT);

    public static final Codec<TvStyle> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("color", DEFAULT.color).forGetter(TvStyle::color),
            Codec.INT.optionalFieldOf("background", DEFAULT.background).forGetter(TvStyle::background),
            Codec.INT.optionalFieldOf("border", DEFAULT.border).forGetter(TvStyle::border),
            Codec.INT.optionalFieldOf("border_width", DEFAULT.borderWidth).forGetter(TvStyle::borderWidth),
            Codec.INT.optionalFieldOf("size", DEFAULT.size).forGetter(TvStyle::size),
            TvAlign.CODEC.optionalFieldOf("align", DEFAULT.align).forGetter(TvStyle::align),
            Codec.BOOL.optionalFieldOf("fit", DEFAULT.fit).forGetter(TvStyle::fit),
            Codec.STRING.optionalFieldOf("text", DEFAULT.text).forGetter(TvStyle::text),
            Codec.INT.lenientOptionalFieldOf("radius", DEFAULT.radius).forGetter(TvStyle::radius),
            Codec.INT.lenientOptionalFieldOf("opacity", DEFAULT.opacity).forGetter(TvStyle::opacity),
            TvFont.CODEC.lenientOptionalFieldOf("font", DEFAULT.font).forGetter(TvStyle::font)).apply(i, TvStyle::new));

    public TvStyle {
        borderWidth = TvText.clamp(borderWidth, 0, MAX_BORDER);
        size = TvText.clamp(size, 1, MAX_SIZE);
        text = TvText.cut(text, MAX_TEXT);
        radius = TvText.clamp(radius, 0, MAX_RADIUS);
        opacity = TvText.clamp(opacity, 0, 100);
    }

    /** O estilo da etapa 1: sem raio, opacidade cheia e a fonte do jogo. */
    public TvStyle(int color, int background, int border, int borderWidth, int size, TvAlign align, boolean fit,
            String text) {
        this(color, background, border, borderWidth, size, align, fit, text, 0, 100, TvFont.MINECRAFT);
    }

    public TvStyle withColor(int color) {
        return new TvStyle(color, background, border, borderWidth, size, align, fit, text, radius, opacity, font);
    }

    public TvStyle withBackground(int background) {
        return new TvStyle(color, background, border, borderWidth, size, align, fit, text, radius, opacity, font);
    }

    public TvStyle withSize(int size) {
        return new TvStyle(color, background, border, borderWidth, size, align, fit, text, radius, opacity, font);
    }

    public TvStyle withAlign(TvAlign align) {
        return new TvStyle(color, background, border, borderWidth, size, align, fit, text, radius, opacity, font);
    }

    public TvStyle withFit(boolean fit) {
        return new TvStyle(color, background, border, borderWidth, size, align, fit, text, radius, opacity, font);
    }

    public TvStyle withText(String text) {
        return new TvStyle(color, background, border, borderWidth, size, align, fit, text, radius, opacity, font);
    }

    public TvStyle withRadius(int radius) {
        return new TvStyle(color, background, border, borderWidth, size, align, fit, text, radius, opacity, font);
    }

    public TvStyle withOpacity(int opacity) {
        return new TvStyle(color, background, border, borderWidth, size, align, fit, text, radius, opacity, font);
    }

    public TvStyle withFont(TvFont font) {
        return new TvStyle(color, background, border, borderWidth, size, align, fit, text, radius, opacity, font);
    }

    public TvStyle withBorder(int border, int borderWidth) {
        return new TvStyle(color, background, border, borderWidth, size, align, fit, text, radius, opacity, font);
    }
}
