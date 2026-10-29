package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelCanvas;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSelection;
import io.github.matheusanbs.wirelessautomate.tv.TvPixels;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * As texturas do editor de pixel art: a da arte em edição (do tamanho dela, com o pedaço solto da seleção por cima),
 * refeita e subida só quando um pixel, uma cor ou o pedaço muda, nunca por quadro; e a do xadrez do transparente, 2 × 2
 * nas cores do tema da GUI. As duas são {@code NEAREST} e repetem nas bordas ({@code NativeImage.upload} sem
 * {@code clamp}), então a prévia em mosaico e o xadrez são um quadrilátero só cada. Soltas ao fechar o editor.
 * <p>
 * Os outros quadros da animação (a faixa, o papel vegetal e a prévia tocando) têm uma textura cada, refeita só quando
 * o array do quadro troca (os quadros que não estão escolhidos nunca mudam no lugar) ou a paleta muda; o quadro
 * escolhido usa a textura da arte.
 */
final class TvPixelTexture {
    private @Nullable DynamicTexture art;
    private int artW;
    private int artH;
    private int builtCanvas = -1;
    private int builtSelection = -1;
    private @Nullable DynamicTexture checker;
    private int checkerTheme = -1;
    private int builtFrame = -1;
    private DynamicTexture[] frames = new DynamicTexture[0];
    /** O array de pixels de que cada textura de quadro foi feita, e a paleta da vez dela. */
    private byte[][] framePixels = new byte[0][];
    private int[] framePalette = new int[0];
    private final int[] palette = new int[TvPixels.COLORS];
    private int paletteSerial;
    private int paletteChecked = -1;

    /** Refaz a textura da arte se algo mudou desde a última vez (o tamanho muda: uma textura nova). */
    void sync(TvPixelCanvas canvas, TvPixelSelection selection) {
        if (art == null || artW != canvas.width() || artH != canvas.height()) {
            if (art != null) {
                art.close();
            }
            artW = canvas.width();
            artH = canvas.height();
            art = new DynamicTexture(artW, artH, false);
            builtCanvas = -1;
        }
        if (builtCanvas == canvas.version() && builtSelection == selection.version()
                && builtFrame == canvas.current()) {
            return;
        }
        builtCanvas = canvas.version();
        builtSelection = selection.version();
        builtFrame = canvas.current();
        NativeImage image = art.getPixels();
        int[] palette = canvas.palette();
        for (int y = 0; y < artH; y++) {
            for (int x = 0; x < artW; x++) {
                image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(palette[canvas.get(x, y)]));
            }
        }
        if (selection.floating()) {
            for (int py = 0; py < selection.height(); py++) {
                for (int px = 0; px < selection.width(); px++) {
                    int index = selection.pieceAt(px, py);
                    int x = selection.x() + px;
                    int y = selection.y() + py;
                    if (index != 0 && canvas.inside(x, y)) {
                        image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(palette[index]));
                    }
                }
            }
        }
        art.upload();
    }

    /** A arte de ({@code u0}, {@code v0}) a ({@code u1}, {@code v1}) da textura (além de 1, repetida). */
    void drawArt(GuiGraphics g, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        if (art != null) {
            quad(g, art.getId(), x0, y0, x1, y1, u0, v0, u1, v1);
        }
    }

    /**
     * O quadro {@code index} da arte como a {@link #drawArt}, com a opacidade {@code alpha} (o papel vegetal); o
     * escolhido sai da textura da arte, que o quadro de desenho já sincronizou.
     */
    void drawFrame(GuiGraphics g, TvPixelCanvas canvas, int index, float alpha, float x0, float y0, float x1,
            float y1, float u0, float v0, float u1, float v1) {
        int texture = index == canvas.current() ? art == null ? 0 : art.getId() : frameTexture(canvas, index);
        if (texture == 0) {
            return;
        }
        if (alpha < 1) {
            RenderSystem.setShaderColor(1, 1, 1, alpha);
        }
        quad(g, texture, x0, y0, x1, y1, u0, v0, u1, v1);
        if (alpha < 1) {
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    /** A textura de um quadro que não é o escolhido, refeita se o array dele ou a paleta mudaram. */
    private int frameTexture(TvPixelCanvas canvas, int index) {
        if (frames.length < canvas.frames()) {
            frames = Arrays.copyOf(frames, canvas.frames());
            framePixels = Arrays.copyOf(framePixels, canvas.frames());
            framePalette = Arrays.copyOf(framePalette, canvas.frames());
        }
        if (paletteChecked != canvas.version()) {
            paletteChecked = canvas.version();
            if (!Arrays.equals(palette, canvas.palette())) {
                System.arraycopy(canvas.palette(), 0, palette, 0, palette.length);
                paletteSerial++;
            }
        }
        byte[] content = canvas.frame(index);
        DynamicTexture texture = frames[index];
        int w = canvas.width();
        int h = canvas.height();
        if (texture == null || texture.getPixels().getWidth() != w || texture.getPixels().getHeight() != h) {
            if (texture != null) {
                texture.close();
            }
            texture = new DynamicTexture(w, h, false);
            frames[index] = texture;
            framePixels[index] = null;
        }
        if (framePixels[index] != content || framePalette[index] != paletteSerial) {
            framePixels[index] = content;
            framePalette[index] = paletteSerial;
            NativeImage image = texture.getPixels();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(palette[content[y * w + x]]));
                }
            }
            texture.upload();
        }
        return texture.getId();
    }

    /** O xadrez com casas de {@code cell} px, alinhado em ({@code originX}, {@code originY}). */
    void drawChecker(GuiGraphics g, float x0, float y0, float x1, float y1, float originX, float originY, int cell) {
        if (checker == null || checkerTheme != GuiThemes.version()) {
            if (checker == null) {
                checker = new DynamicTexture(2, 2, false);
            }
            checkerTheme = GuiThemes.version();
            int light = FastColor.ABGR32.fromArgb32(GuiPaint.PANEL);
            int dark = FastColor.ABGR32.fromArgb32(GuiPaint.INSET);
            NativeImage image = checker.getPixels();
            image.setPixelRGBA(0, 0, light);
            image.setPixelRGBA(1, 1, light);
            image.setPixelRGBA(1, 0, dark);
            image.setPixelRGBA(0, 1, dark);
            checker.upload();
        }
        float scale = 2f * cell;
        quad(g, checker.getId(), x0, y0, x1, y1, (x0 - originX) / scale, (y0 - originY) / scale,
                (x1 - originX) / scale, (y1 - originY) / scale);
    }

    /** Um quadrilátero com a textura, na mesma ordem de vértices do {@code GuiGraphics.blit}, com mistura. */
    private static void quad(GuiGraphics g, int texture, float x0, float y0, float x1, float y1, float u0, float v0,
            float u1, float v1) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = g.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x0, y0, 0).setUv(u0, v0);
        buffer.addVertex(matrix, x0, y1, 0).setUv(u0, v1);
        buffer.addVertex(matrix, x1, y1, 0).setUv(u1, v1);
        buffer.addVertex(matrix, x1, y0, 0).setUv(u1, v0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    void release() {
        if (art != null) {
            art.close();
            art = null;
        }
        if (checker != null) {
            checker.close();
            checker = null;
        }
        for (DynamicTexture frame : frames) {
            if (frame != null) {
                frame.close();
            }
        }
        frames = new DynamicTexture[0];
        framePixels = new byte[0][];
        framePalette = new int[0];
        builtCanvas = -1;
        checkerTheme = -1;
        paletteChecked = -1;
    }
}
