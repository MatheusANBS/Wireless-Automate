package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import java.util.Optional;

/**
 * As peças do layout pelas operações do {@link TvEditOps}, e as peças novas da paleta com as medidas do mockup: o
 * Valor 40 × 12 sem ligação ("—" até ligar), o Texto 48 × 10 e a Forma 40 × 24 no Cartão do tema com cantos de 2 px.
 * Os textos e o fundo pelos papéis do tema, para acompanhar a troca de tema.
 */
final class TvPieceAccess implements TvEditOps.Access<TvPiece> {
    static final TvPieceAccess INSTANCE = new TvPieceAccess();

    private TvPieceAccess() {
    }

    @Override
    public int x(TvPiece piece) {
        return piece.x();
    }

    @Override
    public int y(TvPiece piece) {
        return piece.y();
    }

    @Override
    public int width(TvPiece piece) {
        return piece.width();
    }

    @Override
    public int height(TvPiece piece) {
        return piece.height();
    }

    @Override
    public int group(TvPiece piece) {
        return piece.group();
    }

    @Override
    public boolean hidden(TvPiece piece) {
        return piece.hidden();
    }

    @Override
    public boolean occupies(TvPiece piece) {
        return piece.type() != TvPieceType.SHAPE;
    }

    @Override
    public TvPiece withBox(TvPiece piece, int x, int y, int width, int height) {
        return piece.withBox(x, y, width, height);
    }

    @Override
    public TvPiece withGroup(TvPiece piece, int group) {
        return piece.withGroup(group);
    }

    /** Uma peça nova do tipo, no canto; o {@code text} é o do Texto. */
    static TvPiece create(TvPieceType type, String text) {
        TvStyle textStyle = new TvStyle(TvColor.theme(TvColor.TEXT), 0, 0, 0, 8, TvAlign.LEFT, true, "");
        return switch (type) {
            case VALUE -> new TvPiece(type, 0, 0, 40, 12, 0, "", Optional.empty(), TvFormat.DEFAULT, textStyle);
            case TEXT -> new TvPiece(type, 0, 0, 48, 10, 0, "", Optional.empty(), TvFormat.DEFAULT,
                    textStyle.withText(text));
            case SHAPE -> new TvPiece(type, 0, 0, 40, 24, 0, "", Optional.empty(), TvFormat.DEFAULT,
                    textStyle.withBackground(TvColor.theme(TvColor.CARD)).withRadius(2));
        };
    }
}
