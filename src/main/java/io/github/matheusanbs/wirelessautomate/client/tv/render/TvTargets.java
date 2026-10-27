package io.github.matheusanbs.wirelessautomate.client.tv.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.ClientHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * O desenho de um layout num framebuffer próprio, comum à imagem da parede ({@link TvCanvas}) e à miniatura do editor
 * ({@link TvEditorImage}): {@link #begin} limpa e liga o framebuffer, põe a projeção dele e liga a luz dos itens do inventário (senão o bloco 3D sai apagado) e desliga a névoa (o texto
 * está em z −11000 e um mod que deixe a névoa ligada o apagaria); {@link #end} desenha o que ficou e devolve a névoa e
 * a pilha de modelo. O framebuffer e a projeção ficam ligados: quem chama devolve os do jogo. Só na thread de render.
 */
final class TvTargets {
    private static float fogStart;

    private TvTargets() {
    }

    /** Começa o desenho em {@code target} ({@code width} × {@code height}), {@code scale} pixels por pixel de desenho. */
    static GuiGraphics begin(Minecraft minecraft, TextureTarget target, int width, int height, float scale) {
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        float far = ClientHooks.getGuiFarPlane();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width, height, 0, 1000, far),
                VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.translation(0, 0, 10000 - far);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
        fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        GuiGraphics graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        graphics.pose().scale(scale, scale, 1);
        return graphics;
    }

    /**
     * Cria o framebuffer, ou ajusta o que há ao tamanho pedido (refaz se a profundidade pedida mudou, o que o chamador percebe por receber outro objeto) (só um layout com Ícone a quer: o
     * item em 3D precisa dela, senão as faces saem fora de ordem e translúcidas). Devolve o framebuffer.
     */
    static TextureTarget ensure(@Nullable TextureTarget target, int width, int height, boolean depth) {
        if (target == null) {
            return new TextureTarget(width, height, depth, Minecraft.ON_OSX);
        }
        if (target.useDepth != depth) {
            target.destroyBuffers();
            return new TextureTarget(width, height, depth, Minecraft.ON_OSX);
        }
        if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        }
        return target;
    }

    static void end(GuiGraphics graphics) {
        graphics.flush();
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
