package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A câmara animada do Núcleo montado: a figura de Lissajous e a lente, desenhadas a partir do centro da estrutura.
 * O estado de cada câmara fica numa {@link ChamberScene} por posição, atualizada uma vez por tick novo; as que não
 * são desenhadas há mais de 200 ticks saem numa varredura a cada 200 ticks.
 */
public final class ResonatorRenderer implements BlockEntityRenderer<ResonanceCoreBlockEntity> {
    private static final int VIEW_DISTANCE = 64;
    private static final long FORGET_TICKS = 200;
    private static final Map<BlockPos, ChamberScene> SCENES = new HashMap<>();
    private static long lastSweep;

    public ResonatorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ResonanceCoreBlockEntity core, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int light, int overlay) {
        BlockState state = core.getBlockState();
        ChamberView view = core.clientView();
        if (!state.getValue(ResonatorPartBlock.FORMED) || view == ChamberView.EMPTY
                || !(core.getLevel() instanceof ClientLevel level)) {
            return;
        }
        long gameTime = level.getGameTime();
        sweep(gameTime);
        ChamberScene scene = SCENES.computeIfAbsent(core.getBlockPos(), ChamberScene::new);
        if (scene.lastUsed != gameTime) {
            scene.update(view, core.tunedAt(), gameTime, level);
        }
        if (scene.chambers().length == 0) {
            return;
        }
        int tierColor = GuiPaint.tierColor(state.getValue(RouterBlock.TIER));
        Vec3 center = scene.center();
        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        LissajousTrace.render(poseStack, buffer, scene, partialTick, tierColor);
        ChamberLens.render(poseStack, buffer, scene, gameTime, partialTick, tierColor,
                state.getValue(ResonatorPartBlock.FACING));
        poseStack.popPose();
    }

    private static void sweep(long gameTime) {
        if (Math.abs(gameTime - lastSweep) < FORGET_TICKS) {
            return;
        }
        lastSweep = gameTime;
        SCENES.values().removeIf(scene -> Math.abs(gameTime - scene.lastUsed) > FORGET_TICKS);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public boolean shouldRenderOffScreen(ResonanceCoreBlockEntity core) {
        return false;
    }

    /** A caixa 5 × 5 × 5 da estrutura, para a câmara não sumir quando só o Núcleo sai da tela. */
    @Override
    public AABB getRenderBoundingBox(ResonanceCoreBlockEntity core) {
        ChamberScene scene = SCENES.get(core.getBlockPos());
        if (scene != null && scene.view() != ChamberView.EMPTY) {
            return scene.box();
        }
        ChamberView view = core.clientView();
        if (view == ChamberView.EMPTY) {
            return new AABB(core.getBlockPos());
        }
        int[] origin = view.origin();
        return new AABB(origin[0], origin[1], origin[2], origin[0] + 5, origin[1] + 5, origin[2] + 5);
    }
}
