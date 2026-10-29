package io.github.matheusanbs.wirelessautomate.tv;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Os layouts salvos do jogador ({@link TvComponent#layout()}): montar um a partir da Tela e aplicá-lo numa Tela de
 * outro tamanho. Lógica pura. As caixas escalam por eixo, a fonte e o raio pelo menor fator, para as proporções do
 * layout valerem em qualquer área.
 */
public final class TvSavedLayouts {
    private TvSavedLayouts() {
    }

    /** O layout salvo com o nome, as peças, as imagens enviadas que elas usam e o que a Tela tem além delas. */
    public static TvComponent of(String name, TvLayout layout, TvArea area) {
        return new TvComponent(name, layout.pieces(), Optional.of(new TvComponent.Layout(area.pixelWidth(),
                area.pixelHeight(), layout.background(), layout.scanlines())),
                TvComponent.picturesOf(layout.pieces(), layout.background().image(), layout.pictures()));
    }

    /** As peças do layout salvo escaladas para a área; sem layout (um componente comum), as mesmas peças. */
    public static List<TvPiece> scale(TvComponent saved, TvArea area) {
        if (saved.layout().isEmpty()) {
            return saved.pieces();
        }
        TvComponent.Layout from = saved.layout().get();
        return scale(saved.pieces(), from.width(), from.height(), area.pixelWidth(), area.pixelHeight());
    }

    public static List<TvPiece> scale(List<TvPiece> pieces, int fromW, int fromH, int toW, int toH) {
        double sx = (double) toW / fromW;
        double sy = (double) toH / fromH;
        double sm = Math.min(sx, sy);
        List<TvPiece> out = new ArrayList<>(pieces.size());
        for (TvPiece piece : pieces) {
            int x = (int) Math.round(piece.x() * sx);
            int y = (int) Math.round(piece.y() * sy);
            int w = Math.max(1, (int) Math.round(piece.width() * sx));
            int h = Math.max(1, (int) Math.round(piece.height() * sy));
            TvStyle style = piece.style();
            out.add(piece.withBox(x, y, w, h).withStyle(style
                    .withSize(Math.max(1, (int) Math.round(style.size() * sm)))
                    .withRadius((int) Math.round(style.radius() * sm))));
        }
        return out;
    }

    /**
     * O layout da Tela com as peças do layout salvo escaladas, o fundo, as linhas e as imagens enviadas dele; o tema
     * da Tela fica.
     */
    public static TvLayout apply(TvComponent saved, TvLayout current, TvArea area) {
        TvLayout next = withPictures(current.withPieces(scale(saved, area)), saved.pictures());
        if (saved.layout().isPresent()) {
            next = next.withBackground(saved.layout().get().background())
                    .withScanlines(saved.layout().get().scanlines());
        }
        return next.clampedTo(area);
    }

    /** O layout com as imagens de {@code pictures} que ele ainda não tem (as que sobrarem saem ao salvar). */
    public static TvLayout withPictures(TvLayout layout, List<TvPicture> pictures) {
        if (pictures.isEmpty()) {
            return layout;
        }
        List<TvPicture> merged = new ArrayList<>(layout.pictures());
        for (TvPicture picture : pictures) {
            if (layout.picture(picture.id()) == null) {
                merged.add(picture);
            }
        }
        return merged.size() == layout.pictures().size() ? layout : layout.withPictures(merged);
    }
}
