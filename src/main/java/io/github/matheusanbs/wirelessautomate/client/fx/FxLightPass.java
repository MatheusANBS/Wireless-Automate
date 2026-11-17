package io.github.matheusanbs.wirelessautomate.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.FloatBuffer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * A passada de tela da luz no mundo: lê a cor (uma cópia nossa do alvo principal) e a profundidade (sempre a guardada
 * pelo {@link FxDepth}, para não ler a textura em que o próprio alvo desenha), soma as luzes do {@link FxLights} em
 * degraus presos à grade de 1/16 e escreve o resultado de volta no alvo principal. Sem pacote roda em
 * {@code AFTER_LEVEL} e guarda a profundidade ela mesma; com pacote roda no gancho tardio, que já guardou a
 * profundidade no {@code AFTER_LEVEL} (o jogo a limpa antes dali). O estado de GL volta ao que o vanilla espera.
 * <p>
 * Fabulous: em {@code AFTER_LEVEL} a cena já está composta no alvo principal, mas a profundidade dele foi limpa pela
 * cadeia de transparência; a profundidade vem da cópia que o {@link FxStages} guardou em {@code AFTER_WEATHER}.
 * <p>
 * Limitação conhecida (Fabulous): essa cópia provavelmente não traz os translúcidos (água e vidro), que têm alvo
 * próprio e entram depois, então a luz pode ignorar um vidro ou uma água na frente de uma superfície. Não foi medido;
 * está em {@code docs/mapa/efeitos.md}.
 */
public final class FxLightPass {
    private static final Supplier<ShaderInstance> SHADER = FxShaders::lightPass;
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final float[] POS = new float[FxLights.SHADER_MAX * 4];
    private static final float[] COL = new float[FxLights.SHADER_MAX * 4];
    /** Só para o e2e: desliga a passada, para a captura sem luz ao lado da com luz. */
    public static boolean disabledForTests;
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(FxLights.SHADER_MAX * 4);
    /** Os locais dos arrays no programa de {@link #locShader}: o JSON do vanilla não declara arrays de vec4. */
    private static ShaderInstance locShader;
    private static int locPos;
    private static int locCol;
    private static TextureTarget copy;
    /** Os ids como {@code Integer}, trocados só quando o id muda: o {@code setSampler} pede um objeto. */
    private static Integer colorSampler = 0;
    private static Integer depthSampler = 0;

    private FxLightPass() {
    }

    /** Roda a passada com o que o {@link FxLights} juntou; sem luz, sem shader ou na passada de sombra, sai. */
    public static void apply(FxContext ctx) {
        ShaderInstance shader = FxShaders.lightPass();
        if (disabledForTests || FxLights.count() == 0 || shader == null || ShaderPackCheck.shadowPass()) {
            return;
        }
        // com o Fabulous o FxStages já guardou a profundidade, antes de a cadeia limpar o alvo principal
        if (!ShaderPackCheck.active() && !FxDepth.takeEarly()) {
            FxDepth.save();
        }
        int depthId = FxDepth.depthTextureId();
        if (depthId == 0) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        copyColor(main);
        int colorId = copy.getColorTextureId();
        if (colorSampler.intValue() != colorId) {
            colorSampler = colorId;
        }
        if (depthSampler.intValue() != depthId) {
            depthSampler = depthId;
        }
        shader.setSampler("DiffuseSampler", colorSampler);
        shader.setSampler("DepthSampler", depthSampler);
        INVERSE.set(ctx.projection).mul(ctx.modelView).invert();
        shader.safeGetUniform("InvViewProj").set(INVERSE);
        shader.safeGetUniform("CamFrac").set((float) (ctx.camX - Math.floor(ctx.camX)),
                (float) (ctx.camY - Math.floor(ctx.camY)), (float) (ctx.camZ - Math.floor(ctx.camZ)));
        int n = FxLights.fill(ctx.camX, ctx.camY, ctx.camZ, POS, COL);
        shader.safeGetUniform("LightCount").set(n);
        // o vanilla só sobe vec4 soltos pelo JSON: os arrays de luz sobem direto, com o programa ligado
        shader.apply();
        // a instância muda quando os recursos recarregam (o id de programa pode ser reaproveitado pelo driver)
        if (locShader != shader) {
            locShader = shader;
            locPos = GL20.glGetUniformLocation(shader.getId(), "LightPos");
            locCol = GL20.glGetUniformLocation(shader.getId(), "LightCol");
        }
        upload(locPos, POS, n);
        upload(locCol, COL, n);
        main.bindWrite(false);
        // a passada substitui a cor (cena + luz): sem blend e sem profundidade
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(SHADER);
        try {
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                    DefaultVertexFormat.POSITION);
            builder.addVertex(-1f, -1f, 0f);
            builder.addVertex(3f, -1f, 0f);
            builder.addVertex(-1f, 3f, 0f);
            BufferUploader.drawWithShader(builder.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        }
    }

    /** Libera a cópia da cor (ao sair do mundo); a próxima passada cria outra. */
    static void release() {
        if (copy != null) {
            copy.destroyBuffers();
            copy = null;
        }
    }

    /** Sobe {@code n} vec4 do array para o uniform {@code location} do programa ligado. */
    private static void upload(int location, float[] values, int n) {
        if (location < 0 || n == 0) {
            return;
        }
        UPLOAD.clear();
        UPLOAD.put(values, 0, n * 4);
        UPLOAD.flip();
        GL20.glUniform4fv(location, UPLOAD);
    }

    /** Copia a cor do alvo principal para o nosso, recriando-o se o tamanho mudou. */
    private static void copyColor(RenderTarget main) {
        if (copy == null) {
            copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        } else if (copy.width != main.width || copy.height != main.height) {
            copy.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(false);
    }
}
