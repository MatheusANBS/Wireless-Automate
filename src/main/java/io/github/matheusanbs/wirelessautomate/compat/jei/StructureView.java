package io.github.matheusanbs.wirelessautomate.compat.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBill;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorFormation;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Member;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * A vista 3D da Estrutura do Ressonador no JEI: os membros da montagem de exemplo nos estados montados, com os
 * modelos do jogo. Arrastar gira (yaw e pitch), a roda dá zoom e a camada mostra só até aquela altura. A lista dos
 * estados é montada uma vez, no primeiro desenho; por quadro só a matriz e a ordem dos Vidros mudam.
 */
final class StructureView {
    /** Camadas da caixa; a camada {@code LAYERS + 1} é o Tudo (e a {@code LAYERS} também mostra a caixa inteira). */
    static final int LAYERS = 5;

    private static final float DEPTH = 150f;
    private static final float CENTER = LAYERS / 2f;
    /** Quantos blocos cabem no lado da vista com zoom 1 (a caixa 5 × 5 × 5 vista de quina). */
    private static final float FIT = 8.2f;
    private static final float MIN_PITCH = -10f;
    private static final float MAX_PITCH = 80f;
    private static final float MIN_ZOOM = 0.6f;
    private static final float MAX_ZOOM = 2.5f;
    /** Graus por pixel arrastado. */
    private static final float DRAG_DEGREES = 1.5f;
    private static final Vector3f WORLD_LIGHT_0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize();
    private static final Vector3f WORLD_LIGHT_1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();

    private final ResonatorBill bill;
    private BlockState[] states;
    private int[] xs;
    private int[] ys;
    private int[] zs;
    /** Os índices dos Vidros, reordenados de trás para a frente a cada quadro. */
    private int[] glass;
    private boolean[] isGlass;
    private float[] glassDepth;

    /** O Núcleo de frente para quem olha, um pouco de cima. */
    private float yaw = 45f;
    private float pitch = 30f;
    private float zoom = 1f;
    private int layer = LAYERS + 1;

    private final Matrix4f matrix = new Matrix4f();
    private final Vector3f light0 = new Vector3f();
    private final Vector3f light1 = new Vector3f();
    private final Vector3f probe = new Vector3f();

    StructureView(ResonatorBill bill) {
        this.bill = bill;
    }

    int layer() {
        return layer;
    }

    void setLayer(int layer) {
        this.layer = Mth.clamp(layer, 1, LAYERS + 1);
    }

    void drag(double dx, double dy) {
        yaw = (yaw + (float) dx * DRAG_DEGREES) % 360f;
        pitch = Mth.clamp(pitch + (float) dy * DRAG_DEGREES, MIN_PITCH, MAX_PITCH);
    }

    void scroll(double delta) {
        zoom = Mth.clamp(zoom * (float) Math.pow(1.1, delta), MIN_ZOOM, MAX_ZOOM);
    }

    /**
     * Desenha a vista no retângulo ({@code x}, {@code y}, {@code width}, {@code height}) das coordenadas da receita.
     * O scissor do {@link GuiGraphics} não segue a pose, por isso o recorte soma a translação dela.
     */
    void draw(GuiGraphics g, int x, int y, int width, int height) {
        if (states == null) {
            build();
        }
        Matrix4f current = g.pose().last().pose();
        int left = Math.round(current.m30()) + x;
        int top = Math.round(current.m31()) + y;
        g.flush();
        g.enableScissor(left, top, left + width, top + height);
        PoseStack pose = g.pose();
        pose.pushPose();
        float scale = Math.min(width, height) / FIT * zoom;
        matrix.translation(x + width / 2f, y + height / 2f, DEPTH)
                .scale(scale, -scale, scale)
                .rotateX(rad(pitch))
                .rotateY(rad(yaw))
                .translate(-CENTER, -CENTER, -CENTER);
        pose.mulPose(matrix);

        // luz fixa no mundo: gira com a estrutura, como a sombra das faces de um bloco colocado
        light0.set(WORLD_LIGHT_0).rotateY(rad(yaw)).rotateX(rad(pitch)).mul(1f, -1f, 1f);
        light1.set(WORLD_LIGHT_1).rotateY(rad(yaw)).rotateX(rad(pitch)).mul(1f, -1f, 1f);
        RenderSystem.setShaderLights(light0, light1);

        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        for (int i = 0; i < states.length; i++) {
            if (ys[i] < layer && !isGlass[i]) {
                render(blocks, pose, buffers, i);
            }
        }
        buffers.endBatch();
        // o Vidro por último e de trás para a frente, para um vidro deixar ver o outro
        sortGlass(pose.last().pose());
        for (int i : glass) {
            if (ys[i] < layer) {
                render(blocks, pose, buffers, i);
                buffers.endBatch();
            }
        }
        pose.popPose();
        Lighting.setupFor3DItems();
        // o que vier depois (slots, dicas) não fica atrás dos blocos; o scissor limita a limpeza à vista
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        g.disableScissor();
    }

    private void render(BlockRenderDispatcher blocks, PoseStack pose, MultiBufferSource buffers, int i) {
        pose.pushPose();
        pose.translate(xs[i], ys[i], zs[i]);
        blocks.renderSingleBlock(states[i], pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        pose.popPose();
    }

    /** Ordena os Vidros pela profundidade do centro na tela, do mais longe ao mais perto (z menor primeiro). */
    private void sortGlass(Matrix4f view) {
        for (int k = 0; k < glass.length; k++) {
            int i = glass[k];
            view.transformPosition(xs[i] + 0.5f, ys[i] + 0.5f, zs[i] + 0.5f, probe);
            glassDepth[i] = probe.z;
        }
        for (int a = 1; a < glass.length; a++) {
            int moving = glass[a];
            int b = a - 1;
            while (b >= 0 && glassDepth[glass[b]] > glassDepth[moving]) {
                glass[b + 1] = glass[b];
                b--;
            }
            glass[b + 1] = moving;
        }
    }

    /** Os estados montados de cada membro, pela mesma regra da montagem no mundo; o Núcleo Esmeralda. */
    private void build() {
        List<Member> members = bill.members();
        int size = members.size();
        states = new BlockState[size];
        xs = new int[size];
        ys = new int[size];
        zs = new int[size];
        int glassCount = 0;
        for (int i = 0; i < size; i++) {
            Member member = members.get(i);
            states[i] = ResonatorFormation.formedState(loose(member.part()), member);
            xs[i] = member.x();
            ys[i] = member.y();
            zs[i] = member.z();
            if (member.part() == Part.GLASS) {
                glassCount++;
            }
        }
        glass = new int[glassCount];
        isGlass = new boolean[size];
        glassDepth = new float[size];
        int k = 0;
        for (int i = 0; i < size; i++) {
            if (members.get(i).part() == Part.GLASS) {
                glass[k++] = i;
                isGlass[i] = true;
            }
        }
    }

    private static BlockState loose(Part part) {
        Block block = switch (part) {
            case CASING -> ModBlocks.RESONATOR_CASING.get();
            case GLASS -> ModBlocks.RESONATOR_GLASS.get();
            case CORE -> ModBlocks.RESONANCE_CORE.get();
            case ENERGY_PORT -> ModBlocks.RESONATOR_ENERGY_PORT.get();
            case CRYSTAL_PORT -> ModBlocks.RESONATOR_CRYSTAL_PORT.get();
            case TUNER -> ModBlocks.RESONATOR_TUNER.get();
            case AIR, OTHER -> throw new IllegalArgumentException("Não é peça do Ressonador: " + part);
        };
        BlockState state = block.defaultBlockState();
        return part == Part.CORE ? state.setValue(RouterBlock.TIER, RouterTier.EMERALD) : state;
    }

    private static float rad(float degrees) {
        return degrees * Mth.DEG_TO_RAD;
    }
}
