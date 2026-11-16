package io.github.matheusanbs.wirelessautomate.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A profundidade do mundo guardada num alvo nosso. Com shaderpack, os efeitos são desenhados por cima da imagem final
 * e, até lá, o jogo já limpou a profundidade do alvo principal: {@link #save()} copia a profundidade no último estágio
 * do mundo e {@link #restore()} devolve ao alvo principal antes do desenho tardio, para os blocos esconderem o que
 * está atrás deles. O mesmo método dos mods de referência (blit só da profundidade).
 */
public final class FxDepth {
    private static TextureTarget saved;
    /** A profundidade deste quadro foi guardada antes da hora (Fabulous); a passada de luz não a guarda de novo. */
    private static boolean early;

    private FxDepth() {
    }

    /** Copia a profundidade do alvo principal para o nosso, recriando-o se o tamanho mudou. */
    public static void save() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (saved == null) {
            saved = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (saved.width != main.width || saved.height != main.height) {
            saved.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        blit(main, saved);
    }

    /**
     * Guarda a profundidade antes da cadeia de transparência do Fabulous limpar o alvo principal; a passada de luz do
     * mesmo quadro usa esta cópia ({@link #takeEarly()}).
     */
    public static void saveEarly() {
        save();
        early = true;
    }

    /** Se há cópia antecipada neste quadro; consome a marca. */
    static boolean takeEarly() {
        boolean was = early;
        early = false;
        return was;
    }

    /** Esquece a marca: o quadro novo não herda a cópia do anterior. */
    static void clearEarly() {
        early = false;
    }

    /** Libera o alvo da profundidade guardada (ao sair do mundo); o próximo {@link #save()} cria outro. */
    static void release() {
        if (saved != null) {
            saved.destroyBuffers();
            saved = null;
        }
        early = false;
    }

    /** A textura da profundidade guardada, ou 0 se nada foi guardado. */
    public static int depthTextureId() {
        return saved == null ? 0 : saved.getDepthTextureId();
    }

    /** Devolve a profundidade guardada ao alvo principal; sem nada guardado, não faz nada. */
    public static void restore() {
        if (saved == null) {
            return;
        }
        blit(saved, Minecraft.getInstance().getMainRenderTarget());
    }

    private static void blit(RenderTarget from, RenderTarget to) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        // o alvo principal volta a ser o de leitura e o de escrita
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }
}
