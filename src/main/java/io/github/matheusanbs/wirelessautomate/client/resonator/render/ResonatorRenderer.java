package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A câmara animada do Núcleo montado, desenhada a partir do centro da estrutura: a placa de Chladni, os cristais e
 * os feixes, as faíscas e o Núcleo de plasma, ou a figura de Lissajous e a lente sem o shader (os cristais são
 * opacos e vão antes dos aditivos). Com um shaderpack do Iris em uso, o plasma também sai e a figura de Lissajous volta.
 * O estado de cada câmara fica numa {@link ChamberScene} por dimensão e posição, atualizada uma vez por tick novo; as
 * que não são desenhadas há mais de 200 ticks saem numa varredura a cada 200 ticks. Cada block entity guarda a cena
 * que achou na primeira vez, para o quadro não criar uma chave nova; a cena por posição sobrevive à recarga do
 * chunk.
 */
public final class ResonatorRenderer implements BlockEntityRenderer<ResonanceCoreBlockEntity> {
    private static final int VIEW_DISTANCE = 64;
    private static final long FORGET_TICKS = 200;
    private static final Map<SceneKey, ChamberScene> SCENES = new HashMap<>();
    /** A cena já achada de cada Núcleo; a chave é fraca e some com o block entity. */
    private static final Map<ResonanceCoreBlockEntity, ChamberScene> BY_CORE = new WeakHashMap<>();
    private static long lastSweep;
    /** Só do e2e: força o desenho sem o shader (a figura de Lissajous e a lente), para medir o custo dos dois na mesma rodada. */
    public static boolean forceLegacyForTests;

    private record SceneKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

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
        ChamberScene scene = BY_CORE.get(core);
        if (scene == null) {
            scene = SCENES.computeIfAbsent(new SceneKey(level.dimension(), core.getBlockPos()),
                    key -> new ChamberScene(key.pos()));
            BY_CORE.put(core, scene);
        }
        boolean shader = !forceLegacyForTests && PlasmaShader.get() != null && RibbonShader.get() != null
                && !ShaderPackCheck.active();
        scene.useConduit(shader);
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
        ChladniPlate.render(poseStack, buffer, scene, partialTick);
        ChamberCrystals.render(poseStack, buffer, scene, gameTime, partialTick, level);
        if (shader) {
            Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 world = scene.worldCenter();
            double dx = eye.x - world.x;
            double dy = eye.y - world.y;
            double dz = eye.z - world.z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            PlasmaCore.render(poseStack, buffer, scene, partialTick, tierColor, distanceSq);
            if (distanceSq < FlowConduit.RANGE_SQ) {
                FlowConduit.render(poseStack, buffer, scene, partialTick, tierColor);
            }
        } else {
            ChamberSparks.render(poseStack, buffer, scene, partialTick);
            LissajousTrace.render(poseStack, buffer, scene, partialTick, tierColor);
            ChamberLens.render(poseStack, buffer, scene, gameTime, partialTick, tierColor,
                    state.getValue(ResonatorPartBlock.FACING));
        }
        poseStack.popPose();
    }

    private static void sweep(long gameTime) {
        if (Math.abs(gameTime - lastSweep) < FORGET_TICKS) {
            return;
        }
        lastSweep = gameTime;
        SCENES.values().removeIf(scene -> Math.abs(gameTime - scene.lastUsed) > FORGET_TICKS);
        BY_CORE.values().removeIf(scene -> Math.abs(gameTime - scene.lastUsed) > FORGET_TICKS);
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
        ChamberScene scene = BY_CORE.get(core);
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
