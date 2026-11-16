package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.client.fx.FxQueue;
import io.github.matheusanbs.wirelessautomate.client.fx.FxShaders;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A Armilar do Núcleo montado: as peças sólidas (órbitas, pinos, engastes, cristais, horizonte e marcos) pelo tipo de
 * entidade do vanilla, que vale com e sem shaderpack e faz sombra. O estado de cada Armilar fica numa
 * {@link ArmilarScene} por dimensão e posição, atualizada uma vez por tick novo; as que não são desenhadas há mais de
 * 200 ticks saem numa varredura a cada 200 ticks. Cada block entity guarda a cena que achou na primeira vez, para o
 * quadro não criar uma chave nova; a cena por posição sobrevive à recarga do chunk.
 */
public final class ArmilarRenderer implements BlockEntityRenderer<ResonanceCoreBlockEntity> {
    private static final int VIEW_DISTANCE = 64;
    private static final long FORGET_TICKS = 200;
    private static final Map<SceneKey, ArmilarScene> SCENES = new HashMap<>();
    /** A cena já achada de cada Núcleo; a chave é fraca e some com o block entity. */
    private static final Map<ResonanceCoreBlockEntity, ArmilarScene> BY_CORE = new WeakHashMap<>();
    private static long lastSweep;
    /** Só do e2e: força a faixa de vazão (0 a 7) da Porta de Energia, ou -1 para a vazão real. */
    public static int forceBandForTests = -1;
    /** Só do e2e: congela o relógio da Armilar nesses segundos (para a captura cair na mesma conjunção), ou NaN para o relógio do mundo. */
    public static double freezeSecondsForTests = Double.NaN;

    /**
     * Só do e2e: os sprites e as fitas que o último quadro da Armilar do Núcleo em {@code core} pediu, em
     * {@code out[0]} e {@code out[1]}; devolve falso se não há cena (ainda não desenhada).
     */
    public static boolean countsForTests(BlockPos core, int[] out) {
        for (Map.Entry<SceneKey, ArmilarScene> entry : SCENES.entrySet()) {
            if (entry.getKey().pos().equals(core)) {
                out[0] = entry.getValue().spriteCount();
                out[1] = entry.getValue().ribbonCount();
                return true;
            }
        }
        return false;
    }

    private record SceneKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public ArmilarRenderer(BlockEntityRendererProvider.Context context) {
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
        ArmilarScene scene = BY_CORE.get(core);
        if (scene == null) {
            scene = SCENES.computeIfAbsent(new SceneKey(level.dimension(), core.getBlockPos()),
                    key -> new ArmilarScene(key.pos()));
            BY_CORE.put(core, scene);
        }
        if (scene.lastUsed != gameTime) {
            scene.update(view, ArmilarScene.faceOf(state.getValue(ResonatorPartBlock.FACING)), core.tunedAt(),
                    gameTime, level);
        }
        if (scene.chambers().length == 0) {
            return;
        }
        scene.setFrame(partialTick, gameTime);
        ArmilarSolids.draw(scene, poseStack, buffer, partialTick);
        // os efeitos esperam o estágio deles; o FxQueue já recusa o job na passada de sombra
        if (FxShaders.ready()) {
            FxQueue.submit(scene);
        }
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

    /** A caixa 5 × 5 × 5 da estrutura mais 1 bloco na horizontal, para a Armilar não sumir quando só o Núcleo sai da tela. */
    @Override
    public AABB getRenderBoundingBox(ResonanceCoreBlockEntity core) {
        ArmilarScene scene = BY_CORE.get(core);
        if (scene != null && scene.view() != ChamberView.EMPTY) {
            return scene.box();
        }
        ChamberView view = core.clientView();
        if (view == ChamberView.EMPTY) {
            return new AABB(core.getBlockPos());
        }
        int[] origin = view.origin();
        return new AABB(origin[0] - 1, origin[1], origin[2] - 1, origin[0] + 6, origin[1] + 5, origin[2] + 6);
    }
}
