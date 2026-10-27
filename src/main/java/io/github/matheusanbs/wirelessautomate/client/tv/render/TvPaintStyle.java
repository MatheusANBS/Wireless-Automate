package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvRule;
import io.github.matheusanbs.wirelessautomate.tv.TvRules;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * As cores e o estado de uma peça na hora de desenhar: o tema aplicado aos papéis, a regra que bate (cor, fundo,
 * esconder, piscar) e a opacidade. Um objeto só, reusado a cada peça pelo {@link TvPainter} (a thread de render é
 * uma); só no redesenho da Tela. Peça sem regra não avalia nada.
 */
final class TvPaintStyle implements Function<TvBinding, OptionalDouble> {
    int color;
    int background;
    int border;
    /** A regra mandou esconder, ou mandou piscar e a fase é a apagada: a peça não é desenhada. */
    boolean skip;
    /** Alguma regra que bate manda piscar (a Tela entra no conjunto do {@link TvCanvases}). */
    boolean blinks;

    private TvLayout values;
    private long pos;
    private int revision;
    private boolean preview;

    /**
     * Resolve a peça: {@code values} é o layout cujas ligações indexam os números (ver {@link TvPainter#paint}) e
     * {@code blinkOff} a fase do piscar no instante do desenho e {@code preview} se é a miniatura do editor (os
     * números vêm primeiro da prévia, ver {@link TvPaintNumbers}).
     */
    void resolve(TvPiece piece, TvLayout layout, TvLayout values, long pos, int revision, boolean blinkOff,
            boolean preview) {
        TvStyle style = piece.style();
        int textColor = style.color();
        int fill = style.background();
        skip = false;
        blinks = false;
        this.values = values;
        this.pos = pos;
        this.revision = revision;
        this.preview = preview;
        if (!piece.rules().isEmpty()) {
            Optional<TvRule> hit = TvRules.first(piece.rules(), own(piece), this);
            if (hit.isPresent()) {
                TvRule rule = hit.get();
                textColor = rule.color().orElse(textColor);
                fill = rule.background().orElse(fill);
                blinks = rule.blink();
                skip = rule.hide() || blinks && blinkOff;
            }
        }
        if (piece.type() == TvPieceType.VALUE && piece.binding().isPresent()
                && TvPaintNumbers.stale(piece.binding().get(), values, pos, revision, preview)) {
            textColor = TvColor.theme(TvColor.MUTED);
        }
        color = TvPaintMath.withOpacity(TvColor.resolve(textColor, layout.theme()), style.opacity());
        background = TvPaintMath.withOpacity(TvColor.resolve(fill, layout.theme()), style.opacity());
        border = TvPaintMath.withOpacity(TvColor.resolve(style.border(), layout.theme()), style.opacity());
    }

    /** O dado da própria peça: o número da ligação do Valor; as outras não têm. */
    private OptionalDouble own(TvPiece piece) {
        if (piece.type() != TvPieceType.VALUE || piece.binding().isEmpty()) {
            return OptionalDouble.empty();
        }
        return number(piece.binding().get());
    }

    /** O número de uma ligação (o do Valor ou o de uma regra com ligação própria); sem número, vazio. */
    @Override
    public OptionalDouble apply(TvBinding binding) {
        return number(binding);
    }

    private OptionalDouble number(TvBinding binding) {
        double value = TvPaintNumbers.of(binding, values, pos, revision, preview);
        return Double.isNaN(value) ? OptionalDouble.empty() : OptionalDouble.of(value);
    }
}
