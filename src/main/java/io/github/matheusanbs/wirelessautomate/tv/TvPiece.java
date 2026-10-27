package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

/**
 * Uma peça do layout: tipo, caixa em pixels de desenho (origem em cima à esquerda), ordem de agrupamento, nome,
 * ligação (só o Valor), formato, estilo, se está escondida e as regras (até {@link TvRule#MAX_PER_PIECE}).
 */
public record TvPiece(TvPieceType type, int x, int y, int width, int height, int group, String name,
        Optional<TvBinding> binding, TvFormat format, TvStyle style,
        boolean hidden, List<TvRule> rules) {
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
            TvStyle.CODEC.optionalFieldOf("style", TvStyle.DEFAULT).forGetter(TvPiece::style),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(TvPiece::hidden),
            TvCodecs.RULE.listOf().xmap(TvPiece::capRules, r -> r).optionalFieldOf("rules", List.of())
                    .forGetter(TvPiece::rules)).apply(i, TvPiece::new));

    public TvPiece {
        width = Math.max(1, width);
        height = Math.max(1, height);
        name = TvText.cut(name, MAX_NAME);
        rules = capRules(rules);
    }

    /** A peça da etapa 1: visível e sem regras. */
    public TvPiece(TvPieceType type, int x, int y, int width, int height, int group, String name,
            Optional<TvBinding> binding, TvFormat format, TvStyle style) {
        this(type, x, y, width, height, group, name, binding, format, style, false, List.of());
    }

    /** As regras além de {@link TvRule#MAX_PER_PIECE} são cortadas. */
    private static List<TvRule> capRules(List<TvRule> rules) {
        return List.copyOf(rules.size() > TvRule.MAX_PER_PIECE ? rules.subList(0, TvRule.MAX_PER_PIECE) : rules);
    }

    /** A mesma peça noutra caixa. */
    public TvPiece withBox(int x, int y, int width, int height) {
        return new TvPiece(type, x, y, width, height, group, name, binding, format, style, hidden, rules);
    }

    public TvPiece withStyle(TvStyle style) {
        return new TvPiece(type, x, y, width, height, group, name, binding, format, style, hidden, rules);
    }

    public TvPiece withFormat(TvFormat format) {
        return new TvPiece(type, x, y, width, height, group, name, binding, format, style, hidden, rules);
    }

    public TvPiece withBinding(Optional<TvBinding> binding) {
        return new TvPiece(type, x, y, width, height, group, name, binding, format, style, hidden, rules);
    }

    public TvPiece withName(String name) {
        return new TvPiece(type, x, y, width, height, group, name, binding, format, style, hidden, rules);
    }

    public TvPiece withGroup(int group) {
        return new TvPiece(type, x, y, width, height, group, name, binding, format, style, hidden, rules);
    }

    public TvPiece withHidden(boolean hidden) {
        return new TvPiece(type, x, y, width, height, group, name, binding, format, style, hidden, rules);
    }

    public TvPiece withRules(List<TvRule> rules) {
        return new TvPiece(type, x, y, width, height, group, name, binding, format, style, hidden, rules);
    }
}
