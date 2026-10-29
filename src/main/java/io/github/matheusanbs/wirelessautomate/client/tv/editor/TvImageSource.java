package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.tv.render.TvArtThumbs;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvPictureThumbs;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvSavedLayouts;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * O que a peça Imagem e o fundo em mosaico mostram, escolhido no editor: uma pixel art ou uma imagem enviada,
 * exatamente um dos dois. As duas entram pelo mesmo id na peça ({@link TvPicture#id} nunca colide com o de uma arte),
 * então arrastar, a paleta de Peças e as grades tratam as duas pelo mesmo caminho ({@link TvImageOps}); muda só a
 * cópia que vai ao layout ({@link #addTo}) e a miniatura ({@link #drawFitted}).
 */
record TvImageSource(@Nullable TvPixelArt art, @Nullable TvPicture picture) {
    static TvImageSource of(TvPixelArt art) {
        return new TvImageSource(art, null);
    }

    static TvImageSource of(TvPicture picture) {
        return new TvImageSource(null, picture);
    }

    UUID id() {
        return art != null ? art.id() : picture.id();
    }

    int width() {
        return art != null ? art.width() : picture.width();
    }

    int height() {
        return art != null ? art.height() : picture.height();
    }

    /** O layout com a cópia da arte ({@link TvImageOps#withArt}) ou com a referência da imagem. */
    TvLayout addTo(TvLayout layout) {
        return art != null ? TvImageOps.withArt(layout, art) : TvSavedLayouts.withPictures(layout, List.of(picture));
    }

    /**
     * A miniatura no maior tamanho que cabe em {@code side} px, entre o {@code begin} e o {@code end} do
     * {@link TvArtThumbs}.
     */
    void drawFitted(GuiGraphics g, int x, int y, int side) {
        if (art != null) {
            TvArtThumbs.drawFitted(g, art, x, y, side);
        } else {
            TvPictureThumbs.drawFitted(g, picture, x, y, side);
        }
    }

    /** O desenho esticado em {@code w} × {@code h}, sozinho (o fantasma do arrastar). */
    void draw(GuiGraphics g, int x, int y, int w, int h) {
        if (art != null) {
            TvArtThumbs.draw(g, art, x, y, w, h);
        } else {
            TvArtThumbs.begin(g);
            TvPictureThumbs.draw(g, picture, x, y, w, h);
            TvArtThumbs.end(g);
        }
    }
}
