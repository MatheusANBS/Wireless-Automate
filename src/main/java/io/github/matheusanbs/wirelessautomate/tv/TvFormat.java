package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Como uma peça Valor escreve o número: casas, abreviação, sinal, prefixo, sufixo e unidade de tempo. */
public record TvFormat(int decimals, boolean abbreviate, boolean sign, String prefix, String suffix, TvUnit unit) {
    public static final int MAX_DECIMALS = 3;
    public static final int MAX_AFFIX = 32;
    public static final TvFormat DEFAULT = new TvFormat(1, true, false, "", "", TvUnit.SECOND);

    public static final Codec<TvFormat> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("decimals", DEFAULT.decimals).forGetter(TvFormat::decimals),
            Codec.BOOL.optionalFieldOf("abbreviate", DEFAULT.abbreviate).forGetter(TvFormat::abbreviate),
            Codec.BOOL.optionalFieldOf("sign", DEFAULT.sign).forGetter(TvFormat::sign),
            Codec.STRING.optionalFieldOf("prefix", DEFAULT.prefix).forGetter(TvFormat::prefix),
            Codec.STRING.optionalFieldOf("suffix", DEFAULT.suffix).forGetter(TvFormat::suffix),
            TvUnit.CODEC.optionalFieldOf("unit", DEFAULT.unit).forGetter(TvFormat::unit)).apply(i, TvFormat::new));

    public TvFormat {
        decimals = TvText.clamp(decimals, 0, MAX_DECIMALS);
        prefix = TvText.cut(prefix, MAX_AFFIX);
        suffix = TvText.cut(suffix, MAX_AFFIX);
    }
}
