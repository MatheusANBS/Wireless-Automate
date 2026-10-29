package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/**
 * As miniaturas das pixel arts da biblioteca na GUI do editor (a aba Pixel art): um quadrilátero com a textura da
 * arte do {@link TvArtTextures}, a mesma que a Tela usa, sem nada pintado pixel a pixel. Uma lista de miniaturas
 * desenha entre {@link #begin} e {@link #end}, que fecham o lote da GUI uma vez antes da primeira e outra depois da
 * última (o tipo de desenho de cada arte já fecha o anterior ao trocar). Enquanto a biblioteca está no cliente (só com
 * o editor aberto, {@link TvLibraryClient}), as texturas dela ficam na varredura.
 */
public final class TvArtThumbs {
    private static final int WHITE = 0xFFFFFFFF;

    private TvArtThumbs() {
    }

    /** A arte esticada em ({@code x}, {@code y}), {@code w} × {@code h}, sozinha (a escala inteira é de quem chama). */
    public static void draw(GuiGraphics g, TvPixelArt art, int x, int y, int w, int h) {
        begin(g);
        quad(g, art, x, y, w, h);
        end(g);
    }

    /** Fecha o lote da GUI antes da primeira miniatura de uma lista (o que veio antes desenha antes). */
    public static void begin(GuiGraphics g) {
        g.flush();
    }

    /** Fecha o lote depois da última miniatura (o que vier depois, mesmo desenhado na hora, fica por cima). */
    public static void end(GuiGraphics g) {
        g.flush();
    }

    /**
     * Entre {@link #begin} e {@link #end}: a arte na maior escala que cabe num quadrado de {@code side} px (inteira
     * quando ela é menor), centrada nele; a miniatura da aba Pixel art e da grade de artes.
     */
    public static void drawFitted(GuiGraphics g, TvPixelArt art, int x, int y, int side) {
        int longest = Math.max(art.width(), art.height());
        float scale = longest <= side ? side / longest : side / (float) longest;
        int w = Math.max(1, Math.round(art.width() * scale));
        int h = Math.max(1, Math.round(art.height() * scale));
        quad(g, art, x + (side - w) / 2, y + (side - h) / 2, w, h);
    }

    private static void quad(GuiGraphics g, TvPixelArt art, int x, int y, int w, int h) {
        Matrix4f matrix = g.pose().last().pose();
        VertexConsumer buffer = g.bufferSource().getBuffer(TvArtTextures.renderType(art));
        buffer.addVertex(matrix, x + w, y + h, 0).setUv(1, 1).setColor(WHITE);
        buffer.addVertex(matrix, x + w, y, 0).setUv(1, 0).setColor(WHITE);
        buffer.addVertex(matrix, x, y, 0).setUv(0, 0).setColor(WHITE);
        buffer.addVertex(matrix, x, y + h, 0).setUv(0, 1).setColor(WHITE);
    }

    /** Na varredura do {@link TvArtTextures}: marca as artes da biblioteca que está no cliente. */
    static void keepArts() {
        List<TvPixelArt> arts = TvLibraryClient.arts();
        for (int i = 0; i < arts.size(); i++) {
            TvArtTextures.keep(arts.get(i));
        }
    }
}
