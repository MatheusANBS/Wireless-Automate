package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.tv.TvClientValues;
import io.github.matheusanbs.wirelessautomate.client.tv.TvPreviewValues;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import java.nio.ByteBuffer;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/**
 * A miniatura do editor: o rascunho desenhado pelo mesmo {@link TvPainter} da parede, num framebuffer só do editor
 * (há um editor aberto por vez), no tamanho em que a miniatura aparece na janela. O editor pede a cada quadro
 * ({@link #request}: comparação de referências e números); o redesenho só acontece no começo do quadro seguinte
 * ({@link #onFrame}) e só com o rascunho, a base dos números, os números, o tamanho, a fase do piscar ou uma imagem
 * enviada que ficou pronta ({@link TvPictureTextures#version}) mudados. Os números vêm da prévia do rascunho
 * ({@link TvPreviewValues}) e, sem ela, das ligações do layout salvo ({@code values}). Fechar o editor solta o framebuffer ({@link #release}). Só na thread de render.
 */
public final class TvEditorImage {
    private static final ResourceLocation ID = WirelessAutomate.id("tv/editor");
    private static @Nullable TextureTarget target;
    private static @Nullable TvLayout draft;
    private static @Nullable TvLayout values;
    private static TvArea area = TvArea.SINGLE;
    private static long pos;
    private static int revision;
    private static int width;
    private static int height;
    private static int valuesVersion;
    private static int previewVersion;
    private static long blinkSlot;
    private static boolean blinking;
    private static int timedVersion;
    private static int picturesVersion;
    private static boolean dirty;
    private static boolean painted;
    private static String language = "";
    private static char decimalSep = '.';
    private static char groupSep = ',';
    /** Quantos redesenhos a miniatura já teve (o e2e confere que parado não redesenha). */
    public static int paintsForTests;

    private TvEditorImage() {
    }

    /**
     * O que a miniatura deve mostrar neste quadro: o rascunho, o layout salvo (os números), a área, a Tela e o tamanho
     * da textura em pixels da janela. Só marca suja se algo mudou.
     */
    public static void request(TvLayout draft, TvLayout values, TvArea area, long pos, int revision, int width,
            int height) {
        long gameTime = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        long slot = TvPaintMath.blinkSlot(gameTime);
        if (draft != TvEditorImage.draft || values != TvEditorImage.values || !area.equals(TvEditorImage.area)
                || pos != TvEditorImage.pos || revision != TvEditorImage.revision || width != TvEditorImage.width
                || height != TvEditorImage.height || valuesVersion != TvClientValues.version()
                || previewVersion != TvPreviewValues.version()
                || timedVersion != TvTimed.editorVersion()
                || picturesVersion != TvPictureTextures.version()
                || blinking && slot != blinkSlot) {
            TvEditorImage.draft = draft;
            TvEditorImage.values = values;
            TvEditorImage.area = area;
            TvEditorImage.pos = pos;
            TvEditorImage.revision = revision;
            TvEditorImage.width = width;
            TvEditorImage.height = height;
            valuesVersion = TvClientValues.version();
            previewVersion = TvPreviewValues.version();
            blinkSlot = slot;
            timedVersion = TvTimed.editorVersion();
            picturesVersion = TvPictureTextures.version();
            dirty = true;
        }
    }

    /** No começo do quadro: redesenha se o editor pediu algo novo. Sem pedido, só a checagem do campo. */
    public static void onFrame() {
        if (!dirty || draft == null || width <= 0 || height <= 0) {
            return;
        }
        dirty = false;
        Minecraft minecraft = Minecraft.getInstance();
        separators(minecraft);
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorting sorting = RenderSystem.getVertexSorting();
        TextureTarget before = target;
        target = TvTargets.ensure(target, width, height, TvPaintIcon.has(draft));
        if (target != before) {
            minecraft.getTextureManager().register(ID, new TvTargetTexture(target));
        }
        float scale = width / (float) area.pixelWidth();
        long gameTime = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        GuiGraphics graphics = TvTargets.begin(minecraft, target, width, height, scale);
        int flags = TvPainter.paint(graphics, minecraft.font, draft, values, area, pos, revision, decimalSep, groupSep,
                TvPaintMath.blinkOff(gameTime), scale, height, true);
        blinking = (flags & TvPainter.FLAG_BLINK) != 0;
        TvTimed.editor((flags & TvPainter.FLAG_GRAPH) != 0, (flags & TvPainter.FLAG_CLOCK) != 0,
                (flags & TvPainter.FLAG_ICON) != 0, (flags & TvPainter.FLAG_GIF) != 0,
                (flags & TvPainter.FLAG_ART) != 0 ? TvPaintImage.artNext : Long.MAX_VALUE);
        TvTargets.end(graphics);
        RenderSystem.setProjectionMatrix(projection, sorting);
        minecraft.getMainRenderTarget().bindWrite(true);
        painted = true;
        paintsForTests++;
    }

    /** Se já há uma imagem para mostrar. */
    public static boolean ready() {
        return painted && target != null;
    }

    /**
     * Desenha a imagem no retângulo da GUI. O framebuffer está de cabeça para baixo (o GL conta de baixo) e tem o alfa
     * pré-multiplicado pelo desenho com mistura sobre o transparente: daí o V invertido e a mistura {@code ONE}.
     */
    public static void blit(GuiGraphics g, int x, int y, int w, int h) {
        if (!ready()) {
            return;
        }
        RenderSystem.setShaderTexture(0, ID);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, x, y, 0).setUv(0, 1);
        b.addVertex(m, x, y + h, 0).setUv(0, 0);
        b.addVertex(m, x + w, y + h, 0).setUv(1, 0);
        b.addVertex(m, x + w, y, 0).setUv(1, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * A cor (ARGB, sem alfa pré-multiplicado) do pixel em ({@code u}, {@code v}), de 0 a 1 com o 0 em cima, lido do
     * framebuffer: uma leitura de um pixel por clique do conta-gotas, nunca por quadro. {@code 0} sem imagem.
     */
    public static int pixelAt(double u, double v) {
        if (!ready() || target == null) {
            return 0;
        }
        int px = Math.max(0, Math.min(width - 1, (int) (u * width)));
        int py = Math.max(0, Math.min(height - 1, height - 1 - (int) (v * height)));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixel = stack.malloc(4);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GL11.glReadPixels(px, py, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                    Minecraft.getInstance().getMainRenderTarget().frameBufferId);
            int a = pixel.get(3) & 0xFF;
            if (a == 0) {
                return 0;
            }
            int r = Math.min(255, (pixel.get(0) & 0xFF) * 255 / a);
            int g = Math.min(255, (pixel.get(1) & 0xFF) * 255 / a);
            int b = Math.min(255, (pixel.get(2) & 0xFF) * 255 / a);
            return a << 24 | r << 16 | g << 8 | b;
        }
    }

    /** Fecha o editor: solta o framebuffer e o nome, e esquece o pedido. */
    public static void release() {
        if (target != null) {
            Minecraft.getInstance().getTextureManager().release(ID);
            target.destroyBuffers();
            target = null;
        }
        draft = null;
        values = null;
        width = 0;
        height = 0;
        dirty = false;
        painted = false;
        blinking = false;
        TvTimed.editorClosed();
        TvArtTextures.sweep();
    }

    /** Na varredura do {@link TvArtTextures}: marca as artes do rascunho, com o editor aberto. */
    static void keepArts() {
        if (draft != null) {
            TvArtTextures.keep(draft);
        }
    }

    /** Na varredura do {@link TvPictureTextures}: marca as imagens enviadas do rascunho. */
    static void keepPictures() {
        if (draft != null) {
            TvPictureTextures.keep(draft);
        }
    }

    /** A cada tick com um GIF no rascunho ({@link TvTimed}): ele continua tocando na miniatura. */
    static void seenPictures() {
        if (draft != null) {
            TvPictureTextures.seen(draft);
        }
    }

    /** Os separadores do idioma atual, lidos de novo só quando ele muda. */
    private static void separators(Minecraft minecraft) {
        String code = minecraft.getLanguageManager().getSelected();
        if (!code.equals(language)) {
            language = code;
            DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(
                    Locale.forLanguageTag(code.replace('_', '-')));
            decimalSep = symbols.getDecimalSeparator();
            groupSep = symbols.getGroupingSeparator();
        }
    }
}
