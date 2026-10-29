package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * As miniaturas das imagens enviadas na GUI do editor (a aba Imagens e as grades da Imagem e do mosaico): um
 * quadrilátero com a textura da imagem do {@link TvPictureTextures}, a mesma que a Tela usa, reduzida pelos mipmaps.
 * Um GIF desenhado aqui conta como visto e toca na miniatura. Enquanto a imagem não está pronta (chegando, subindo ou
 * desligada no servidor), o quadro neutro no lugar. Como as do {@link TvArtThumbs}, uma lista de miniaturas desenha
 * entre o {@code begin} e o {@code end} dele. Enquanto a biblioteca está no cliente, as imagens dela ficam na varredura.
 */
public final class TvPictureThumbs {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int NEUTRAL = 0xFF24272C;
    private static final int NEUTRAL_EDGE = 0xFF3C424B;

    private TvPictureThumbs() {
    }

    /**
     * A imagem no maior tamanho que cabe num quadrado de {@code side} px, sem deformar, centrada nele (entre o
     * {@link TvArtThumbs#begin} e o {@link TvArtThumbs#end}).
     */
    public static void drawFitted(GuiGraphics g, TvPicture picture, int x, int y, int side) {
        float scale = side / (float) Math.max(picture.width(), picture.height());
        int w = Math.max(1, Math.round(picture.width() * scale));
        int h = Math.max(1, Math.round(picture.height() * scale));
        draw(g, picture, x + (side - w) / 2, y + (side - h) / 2, w, h);
    }

    /** A imagem esticada em ({@code x}, {@code y}), {@code w} × {@code h} (o fantasma do arrastar). */
    public static void draw(GuiGraphics g, TvPicture picture, int x, int y, int w, int h) {
        RenderType type = TvPictureTextures.renderType(picture);
        if (type == null) {
            g.fill(x, y, x + w, y + h, NEUTRAL_EDGE);
            if (w > 2 && h > 2) {
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, NEUTRAL);
            }
            return;
        }
        if (picture.animated()) {
            TvPictureTextures.seen(picture.hash());
        }
        Matrix4f matrix = g.pose().last().pose();
        VertexConsumer buffer = g.bufferSource().getBuffer(type);
        buffer.addVertex(matrix, x + w, y + h, 0).setUv(1, 1).setColor(WHITE);
        buffer.addVertex(matrix, x + w, y, 0).setUv(1, 0).setColor(WHITE);
        buffer.addVertex(matrix, x, y, 0).setUv(0, 0).setColor(WHITE);
        buffer.addVertex(matrix, x, y + h, 0).setUv(0, 1).setColor(WHITE);
    }

    /** Na varredura do {@link TvPictureTextures}: marca as imagens da biblioteca que está no cliente. */
    static void keepPictures() {
        List<TvLibraryPicture> pictures = TvLibraryClient.pictures();
        for (int i = 0; i < pictures.size(); i++) {
            TvPictureTextures.keep(pictures.get(i).picture());
        }
    }
}
