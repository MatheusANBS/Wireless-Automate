package io.github.matheusanbs.wirelessautomate.compat.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBill;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorFormation;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;
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
 * A vista 3D da Armilar no JEI: os 35 membros da montagem de exemplo nos estados montados, com os modelos do
 * jogo. Arrastar gira (yaw e pitch), a roda dá zoom e a camada mostra só até aquela altura, com os blocos soltos: o
 * corte do modelo montado (um pedaço da escultura) não se leria. As duas listas de estados são montadas uma vez, no
 * primeiro desenho; por quadro só a matriz muda.
 */
final class StructureView {
    /** Camadas com bloco (y de 0 a 2); a camada {@code LAYERS} é o Tudo, a Armilar inteira montada. */
    static final int LAYERS = 3;

    private static final float DEPTH = 150f;
    private static final float CENTER = ResonatorLayout.SIZE / 2f;
    /** O centro da altura: a Armilar tem 3 camadas, do chão ao alto do Berço. */
    private static final float CENTER_Y = LAYERS / 2f;
    /** Quantos blocos cabem no lado da vista com zoom 1 (a base 5 × 5 vista de quina). */
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
    /** Os mesmos membros soltos, para o corte por camada. */
    private BlockState[] looseStates;
    private int[] xs;
    private int[] ys;
    private int[] zs;

    /** O Núcleo de frente para quem olha, um pouco de cima. */
    private float yaw = 45f;
    private float pitch = 30f;
    private float zoom = 1f;
    private int layer = LAYERS;

    private final Matrix4f matrix = new Matrix4f();
    private final Vector3f light0 = new Vector3f();
    private final Vector3f light1 = new Vector3f();

    StructureView(ResonatorBill bill) {
        this.bill = bill;
    }

    int layer() {
        return layer;
    }

    void setLayer(int layer) {
        this.layer = Mth.clamp(layer, 1, LAYERS);
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
                .translate(-CENTER, -CENTER_Y, -CENTER);
        pose.mulPose(matrix);

        // luz fixa no mundo: gira com a estrutura, como a sombra das faces de um bloco colocado
        light0.set(WORLD_LIGHT_0).rotateY(rad(yaw)).rotateX(rad(pitch)).mul(1f, -1f, 1f);
        light1.set(WORLD_LIGHT_1).rotateY(rad(yaw)).rotateX(rad(pitch)).mul(1f, -1f, 1f);
        RenderSystem.setShaderLights(light0, light1);

        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        BlockState[] shown = layer < LAYERS ? looseStates : states;
        for (int i = 0; i < states.length; i++) {
            if (ys[i] < layer) {
                render(blocks, pose, buffers, shown, i);
            }
        }
        buffers.endBatch();
        pose.popPose();
        Lighting.setupFor3DItems();
        // o que vier depois (slots, dicas) não fica atrás dos blocos; o scissor limita a limpeza à vista
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        g.disableScissor();
    }

    private void render(BlockRenderDispatcher blocks, PoseStack pose, MultiBufferSource buffers,
            BlockState[] shown, int i) {
        pose.pushPose();
        pose.translate(xs[i], ys[i], zs[i]);
        blocks.renderSingleBlock(shown[i], pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        pose.popPose();
    }

    /** Os estados montados e soltos de cada membro, pelas mesmas regras do mundo; o Núcleo Esmeralda. */
    private void build() {
        List<Member> members = bill.members();
        int size = members.size();
        states = new BlockState[size];
        looseStates = new BlockState[size];
        xs = new int[size];
        ys = new int[size];
        zs = new int[size];
        for (int i = 0; i < size; i++) {
            Member member = members.get(i);
            states[i] = ResonatorFormation.formedState(loose(member.part()), member);
            looseStates[i] = ResonatorFormation.looseState(states[i]);
            xs[i] = member.x();
            ys[i] = member.y();
            zs[i] = member.z();
        }
    }

    private static BlockState loose(Part part) {
        Block block = switch (part) {
            case CASING -> ModBlocks.RESONATOR_CASING.get();
            case COLUMN -> ModBlocks.RESONATOR_COLUMN.get();
            case CRADLE -> ModBlocks.RESONATOR_CRADLE.get();
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
