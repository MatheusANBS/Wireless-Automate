package io.github.matheusanbs.wirelessautomate.client.resonator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.resonator.ProjectionDiff;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * O desenho da projeção do Ressonador: o fantasma translúcido de cada peça que falta (já no estado montado, com
 * o alfa respirando entre 0,3 e 0,4), o contorno coral com o fantasma do certo em cada bloco errado da casca e um
 * cubo coral pequeno em cada posição ocupada do interior. Sem projeção ligada, nada.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class ProjectionRenderer {
    /** Coral da marca, o mesmo do contorno do Vinculador; no mundo, não segue o tema das telas. */
    private static final float CORAL_R = 0xF0 / 255f;
    private static final float CORAL_G = 0x60 / 255f;
    private static final float CORAL_B = 0x3E / 255f;
    private static final long BREATH_MS = 2400;
    private static final float ALPHA_MIN = 0.3f;
    private static final float ALPHA_MAX = 0.4f;
    /** O fantasma num bloco errado sai um pouco para fora dele, para não sumir dentro do bloco. */
    private static final float WRONG_GHOST_SCALE = 1.01f;
    private static final double OUTLINE_INFLATE = 0.02;
    private static final double INTERIOR_HALF = 0.15;
    private static final int SIZE = ResonatorLayout.SIZE;

    private ProjectionRenderer() {
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        ResonatorProjection.Active projection = ResonatorProjection.active();
        if (projection == null || projection.result() == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ProjectionDiff.State[][][] states = projection.result().states();
        int[] o = projection.origin();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(o[0] - camera.x, o[1] - camera.y, o[2] - camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        VertexConsumer ghost = new AlphaVertexConsumer(buffers.getBuffer(RenderType.translucent()), breathingAlpha());
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        List<int[]> wrongShell = new ArrayList<>();
        List<int[]> wrongInterior = new ArrayList<>();
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    ProjectionDiff.State state = states[x][y][z];
                    BlockState expected = projection.ghosts()[x][y][z];
                    if (state == ProjectionDiff.State.MISSING && expected != null) {
                        drawGhost(dispatcher, pose, ghost, expected, x, y, z, 1f);
                    } else if (state == ProjectionDiff.State.WRONG) {
                        if (projection.expected()[x][y][z] == Part.AIR) {
                            wrongInterior.add(new int[] {x, y, z});
                        } else {
                            wrongShell.add(new int[] {x, y, z});
                            if (expected != null) {
                                drawGhost(dispatcher, pose, ghost, expected, x, y, z, WRONG_GHOST_SCALE);
                            }
                        }
                    }
                }
            }
        }
        buffers.endBatch(RenderType.translucent());

        if (!wrongInterior.isEmpty()) {
            VertexConsumer filled = buffers.getBuffer(RenderType.debugFilledBox());
            for (int[] p : wrongInterior) {
                AABB box = interiorBox(p);
                LevelRenderer.addChainedFilledBoxVertices(pose, filled, box.minX, box.minY, box.minZ, box.maxX,
                        box.maxY, box.maxZ, CORAL_R, CORAL_G, CORAL_B, 0.8f);
            }
            buffers.endBatch(RenderType.debugFilledBox());
        }
        if (!wrongShell.isEmpty() || !wrongInterior.isEmpty()) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (int[] p : wrongShell) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(p[0], p[1], p[2], p[0] + 1, p[1] + 1, p[2] + 1)
                        .inflate(OUTLINE_INFLATE), CORAL_R, CORAL_G, CORAL_B, 1f);
            }
            for (int[] p : wrongInterior) {
                LevelRenderer.renderLineBox(pose, lines, interiorBox(p), CORAL_R, CORAL_G, CORAL_B, 1f);
            }
            buffers.endBatch(RenderType.lines());
        }
        pose.popPose();
    }

    /** O alfa do fantasma num ciclo de 2,4 s, entre 0,3 e 0,4. */
    private static float breathingAlpha() {
        double phase = (Util.getMillis() % BREATH_MS) / (double) BREATH_MS;
        float wave = (float) (0.5 + 0.5 * Math.sin(phase * 2 * Math.PI));
        return ALPHA_MIN + (ALPHA_MAX - ALPHA_MIN) * wave;
    }

    private static void drawGhost(BlockRenderDispatcher dispatcher, PoseStack pose, VertexConsumer consumer,
                                  BlockState state, int x, int y, int z, float scale) {
        pose.pushPose();
        pose.translate(x + 0.5f, y + 0.5f, z + 0.5f);
        pose.scale(scale, scale, scale);
        pose.translate(-0.5f, -0.5f, -0.5f);
        dispatcher.getModelRenderer().renderModel(pose.last(), consumer, state, dispatcher.getBlockModel(state),
                1f, 1f, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }

    private static AABB interiorBox(int[] p) {
        double cx = p[0] + 0.5;
        double cy = p[1] + 0.5;
        double cz = p[2] + 0.5;
        return new AABB(cx - INTERIOR_HALF, cy - INTERIOR_HALF, cz - INTERIOR_HALF,
                cx + INTERIOR_HALF, cy + INTERIOR_HALF, cz + INTERIOR_HALF);
    }

    /** Repassa os vértices trocando o alfa da cor: é assim que o modelo do bloco sai translúcido. */
    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int ignored) {
            delegate.setColor(red, green, blue, Math.round(alpha * 255));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
