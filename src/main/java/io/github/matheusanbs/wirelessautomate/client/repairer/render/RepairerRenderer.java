package io.github.matheusanbs.wirelessautomate.client.repairer.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.CrystalShader;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.PlasmaShader;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.RibbonShader;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.ShaderPackCheck;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlock;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlockEntity;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A animação do Reparador no mundo, sem estado próprio: tudo sai da {@link RepairerView} do block entity, do
 * {@code lit} do blockstate e do relógio do mundo (o progresso de cada linha pela {@link RepairTimeline}). As luzes
 * fixas do modelo, acesas ou apagadas, são do próprio chunk (o {@code lit}); aqui ficam os cristais nos pedestais, o
 * emissor e as fitas (as runas e os pedestais acesos). Apagado e vazio, sai na primeira linha. O desenho é agrupado por
 * tipo de render (cristais, depois o plasma, depois as fitas), porque trocar de {@code RenderType} no buffer fecha o
 * lote, e em pixels do modelo ({@code scale(1/16)}), com o facing aplicado nos pontos pela {@link RepairerGeometry},
 * para os billboards usarem os eixos da câmera do mundo.
 */
public final class RepairerRenderer implements BlockEntityRenderer<RepairerBlockEntity> {
    private static final int VIEW_DISTANCE = 64;
    private static final float PIXEL = 1 / 16f;
    private static final double TICKS_PER_SECOND = 20;
    /** Só do e2e: força o desenho sem os shaders. */
    public static boolean forceLegacyForTests;
    /** Só do e2e: quantos cristais e quads o último {@code render} pôs. */
    public static int lastPrimitivesForTests;

    public RepairerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RepairerBlockEntity repairer, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int light, int overlay) {
        RepairerView view = repairer.clientView();
        BlockState state = repairer.getBlockState();
        boolean lit = state.getValue(RepairerBlock.LIT);
        Level level = repairer.getLevel();
        if ((!lit && view.empty()) || level == null) {
            lastPrimitivesForTests = 0;
            return;
        }
        boolean shader = !forceLegacyForTests && PlasmaShader.get() != null && RibbonShader.get() != null
                && CrystalShader.get() != null && !ShaderPackCheck.active();
        if (!shader) {
            lastPrimitivesForTests = RepairerFallback.render(poseStack, buffer, view, state, level);
            return;
        }
        RepairFrame frame = fill(repairer.getBlockPos(), state, view, lit, level.getGameTime(), partialTick, light);
        poseStack.pushPose();
        poseStack.scale(PIXEL, PIXEL, PIXEL);
        int drawn = RepairerCrystals.render(poseStack, buffer, view, frame);
        drawn += RepairerLights.emitter(poseStack, buffer, frame);
        drawn += RepairerLights.ribbons(poseStack.last(), buffer, frame);
        poseStack.popPose();
        lastPrimitivesForTests = drawn;
    }

    private static RepairFrame fill(BlockPos pos, BlockState state, RepairerView view, boolean lit, long gameTime,
            float partialTick, int light) {
        RepairFrame frame = RepairFrame.INSTANCE;
        frame.lit = lit;
        frame.light = light;
        frame.time = (gameTime % 24000 + partialTick) / TICKS_PER_SECOND;
        boolean running = false;
        double emitter = 0;
        for (int line = 0; line < RepairMath.LINES; line++) {
            if (view.running(line)) {
                double s = RepairClock.seconds(view.end(line), view.duration(), gameTime, partialTick);
                frame.seconds[line] = s;
                emitter = Math.max(emitter, RepairTimeline.emitter(s));
                running = true;
            } else {
                frame.seconds[line] = -1;
            }
        }
        frame.running = running;
        frame.emitter = running ? emitter : RepairTimeline.idleEmitter(frame.time);
        frame.quarter = RepairerGeometry.quarterFrom2D(state.getValue(RepairerBlock.FACING).get2DDataValue());
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = eye.x - pos.getX();
        double dy = eye.y - pos.getY();
        double dz = eye.z - pos.getZ();
        frame.ex = (float) (dx * 16);
        frame.ey = (float) (dy * 16);
        frame.ez = (float) (dz * 16);
        frame.distanceSq = (dx - 0.5) * (dx - 0.5) + (dy - 0.5) * (dy - 0.5) + (dz - 0.5) * (dz - 0.5);
        return frame;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public boolean shouldRenderOffScreen(RepairerBlockEntity repairer) {
        return false;
    }

    /** Tudo cabe no bloco: o vidro vai de 2,6 a 13,4 px. */
    @Override
    public AABB getRenderBoundingBox(RepairerBlockEntity repairer) {
        return new AABB(repairer.getBlockPos());
    }
}
