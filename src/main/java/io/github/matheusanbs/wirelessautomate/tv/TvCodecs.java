package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Os codecs dos tipos puros do modelo (tema e regras), à parte para o JUnit carregar esses tipos sem o jogo. */
public final class TvCodecs {
    public static final Codec<TvTheme> THEME = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(TvTheme::name),
            Codec.INT.fieldOf("bg").forGetter(TvTheme::bg),
            Codec.INT.fieldOf("card").forGetter(TvTheme::card),
            Codec.INT.fieldOf("text").forGetter(TvTheme::text),
            Codec.INT.fieldOf("muted").forGetter(TvTheme::muted),
            Codec.INT.fieldOf("high").forGetter(TvTheme::high),
            Codec.INT.fieldOf("low").forGetter(TvTheme::low)).apply(i, TvTheme::new));

    private static final Codec<TvRuleOp> RULE_OP = Codec.STRING.comapFlatMap(id -> {
        for (TvRuleOp op : TvRuleOp.values()) {
            if (op.id().equals(id)) {
                return DataResult.success(op);
            }
        }
        return DataResult.error(() -> "operador desconhecido: " + id);
    }, TvRuleOp::id);

    public static final Codec<TvRule> RULE = RecordCodecBuilder.create(i -> i.group(
            RULE_OP.fieldOf("op").forGetter(TvRule::op),
            Codec.DOUBLE.fieldOf("value").forGetter(TvRule::value),
            TvBinding.CODEC.optionalFieldOf("bind").forGetter(TvRule::bind),
            Codec.INT.optionalFieldOf("color").forGetter(TvRule::color),
            Codec.INT.optionalFieldOf("background").forGetter(TvRule::background),
            Codec.BOOL.optionalFieldOf("blink", false).forGetter(TvRule::blink),
            Codec.BOOL.optionalFieldOf("hide", false).forGetter(TvRule::hide)).apply(i, TvRule::new));

    private TvCodecs() {
    }
}
