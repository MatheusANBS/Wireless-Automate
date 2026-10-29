package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvPixels;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * A pixel art na textura da Tela, só no redesenho: a peça Imagem e o fundo em mosaico. Pinta por faixas horizontais da
 * mesma cor ({@link TvPixelRuns}, calculadas uma vez por arte e guardadas enquanto a arte existe), um retângulo por
 * faixa. Os retângulos vão direto no buffer do {@code RenderType.gui()}, num lote só (o {@code GuiGraphics.fill}
 * manda um lote ao GL a cada retângulo), dividido a cada {@link #FLUSH_QUADS} para o buffer não crescer sem limite
 * num mosaico grande. Só a thread de render desenha.
 */
final class TvPaintImage {
    /** Quantos retângulos vão antes de mandar o lote ao GL (16 bytes por vértice: 1 MB por lote). */
    private static final int FLUSH_QUADS = 16_384;
    /** As faixas de cada arte; a chave é a arte (pelo conteúdo), solta quando nenhum layout a guarda mais. */
    private static final Map<TvPixelArt, int[]> RUNS = new WeakHashMap<>();
    /** A paleta da arte da vez com a opacidade aplicada, reusada. */
    private static final int[] COLORS = new int[TvPixels.COLORS];

    private static GuiGraphics graphics;
    private static VertexConsumer buffer;
    private static Matrix4f matrix;
    private static int quads;

    private TvPaintImage() {
    }

    /**
     * A Imagem na caixa da peça: na escala inteira, alinhada pelo {@code align} do estilo e pelo {@code valign} e
     * cortada pela caixa; com a escala 0, esticada até a caixa. Sem arte no layout, nada.
     */
    static void piece(GuiGraphics g, TvPiece piece, TvLayout layout) {
        TvPartStyle part = piece.style().part();
        TvPixelArt art = part.image().isPresent() ? layout.image(part.image().get()) : null;
        if (art == null || !colors(art, piece.style().opacity())) {
            return;
        }
        int[] runs = runs(art);
        int x = piece.x();
        int y = piece.y();
        int w = piece.width();
        int h = piece.height();
        begin(g);
        if (part.scale() == 0) {
            float kx = w / (float) art.width();
            float ky = h / (float) art.height();
            for (int run : runs) {
                int color = COLORS[TvPixelRuns.index(run)];
                if (color >>> 24 != 0) {
                    int row = TvPixelRuns.y(run);
                    quad(x + TvPixelRuns.x0(run) * kx, y + row * ky, x + TvPixelRuns.x1(run) * kx, y + (row + 1) * ky,
                            color);
                }
            }
        } else {
            int s = part.scale();
            // A ordem do TvAlign (esquerda, centro, direita) e a do VAlign (cima, meio, baixo) são 0, 1 e 2: a
            // metade da sobra vezes a posição.
            int ox = x + Math.floorDiv((w - art.width() * s) * piece.style().align().ordinal(), 2);
            int oy = y + Math.floorDiv((h - art.height() * s) * part.valign().ordinal(), 2);
            draw(runs, ox, oy, s, x, y, x + w, y + h);
        }
        end();
    }

    /** O mosaico do fundo: a arte repetida a partir do canto de cima à esquerda, cortada pela Tela. */
    static void tiled(GuiGraphics g, TvLayout layout, int width, int height) {
        TvBackground background = layout.background();
        TvPixelArt art = background.image().isPresent() ? layout.image(background.image().get()) : null;
        if (art == null || !colors(art, background.opacity())) {
            return;
        }
        int[] runs = runs(art);
        int s = background.scale();
        int tileW = art.width() * s;
        int tileH = art.height() * s;
        begin(g);
        for (int ty = 0; ty < height; ty += tileH) {
            for (int tx = 0; tx < width; tx += tileW) {
                draw(runs, tx, ty, s, 0, 0, width, height);
            }
        }
        end();
    }

    /** As faixas na escala {@code s} a partir de ({@code ox}, {@code oy}), cortadas pelo retângulo dado. */
    private static void draw(int[] runs, int ox, int oy, int s, int minX, int minY, int maxX, int maxY) {
        for (int run : runs) {
            int y0 = oy + TvPixelRuns.y(run) * s;
            if (y0 >= maxY) {
                return;
            }
            int color = COLORS[TvPixelRuns.index(run)];
            int y1 = Math.min(y0 + s, maxY);
            int x0 = Math.max(ox + TvPixelRuns.x0(run) * s, minX);
            int x1 = Math.min(ox + TvPixelRuns.x1(run) * s, maxX);
            y0 = Math.max(y0, minY);
            if (x0 < x1 && y0 < y1 && color >>> 24 != 0) {
                quad(x0, y0, x1, y1, color);
            }
        }
    }

    /** A paleta com a opacidade em {@link #COLORS}; falso se nenhuma cor aparece (tudo transparente). */
    private static boolean colors(TvPixelArt art, int opacity) {
        boolean any = false;
        int[] palette = art.palette();
        for (int i = 0; i < COLORS.length; i++) {
            COLORS[i] = TvPaintMath.withOpacity(palette[i], opacity);
            any |= COLORS[i] >>> 24 != 0;
        }
        return any;
    }

    private static int[] runs(TvPixelArt art) {
        int[] runs = RUNS.get(art);
        if (runs == null) {
            runs = TvPixelRuns.of(art.width(), art.height(), art.pixels());
            RUNS.put(art, runs);
        }
        return runs;
    }

    private static void begin(GuiGraphics g) {
        g.flush();
        graphics = g;
        matrix = g.pose().last().pose();
        buffer = g.bufferSource().getBuffer(RenderType.gui());
        quads = 0;
    }

    private static void end() {
        graphics.flush();
        graphics = null;
        buffer = null;
        matrix = null;
    }

    /** Um retângulo na ordem de vértices do {@code GuiGraphics.fill} (a mesma face para o descarte de faces). */
    private static void quad(float x0, float y0, float x1, float y1, int color) {
        buffer.addVertex(matrix, x1, y1, 0).setColor(color);
        buffer.addVertex(matrix, x1, y0, 0).setColor(color);
        buffer.addVertex(matrix, x0, y0, 0).setColor(color);
        buffer.addVertex(matrix, x0, y1, 0).setColor(color);
        if (++quads >= FLUSH_QUADS) {
            graphics.flush();
            buffer = graphics.bufferSource().getBuffer(RenderType.gui());
            quads = 0;
        }
    }
}
