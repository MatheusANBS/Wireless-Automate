package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.preset.AreaClipboard;
import io.github.matheusanbs.wirelessautomate.preset.AreaSelection;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Realce no mundo enquanto o jogador segura o Configurador: o contorno da área marcada (ou do
 * primeiro canto) e, com uma cópia de área no modo Área, uma caixa por roteador copiado colada na
 * origem escolhida ou, sem ela, no bloco sob a mira: verde onde já há um roteador (vai receber a
 * configuração), vermelha onde não há (fica de fora). Só lê o item e os blocos que o cliente já tem.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class ConfiguratorAreaRenderer {
    /** Caixas da prévia de colagem desenhadas por quadro. */
    private static final int MAX_GHOSTS = 256;

    private ConfiguratorAreaRenderer() {
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ConfiguratorItem)) {
            stack = player.getOffhandItem();
            if (!(stack.getItem() instanceof ConfiguratorItem)) {
                return;
            }
        }
        AreaSelection selection = stack.get(ModDataComponents.CONFIGURATOR_AREA.get());
        AreaClipboard clipboard = stack.get(ModDataComponents.AREA_CLIPBOARD.get());
        if (selection == null && clipboard == null) {
            return;
        }
        if (selection == null) {
            selection = AreaSelection.EMPTY;
        }

        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);

        BoundingBox box = selection.box();
        if (box != null && here(level, selection.corner1())) {
            LevelRenderer.renderLineBox(pose, lines, AABB.of(box).inflate(0.02), 0.27F, 0.84F, 0.8F, 1.0F);
        }
        if (here(level, selection.corner1())) {
            BlockPos c1 = selection.corner1().get().pos();
            LevelRenderer.renderLineBox(pose, lines, new AABB(c1).inflate(0.04), 1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (here(level, selection.corner2())) {
            BlockPos c2 = selection.corner2().get().pos();
            LevelRenderer.renderLineBox(pose, lines, new AABB(c2).inflate(0.04), 1.0F, 1.0F, 1.0F, 0.6F);
        }

        if (clipboard != null && selection.mode() == AreaSelection.Mode.AREA) {
            BlockPos anchor = here(level, selection.anchor()) ? selection.anchor().get().pos() : aimed(minecraft);
            if (anchor != null) {
                int drawn = 0;
                for (AreaClipboard.Entry entry : clipboard.entries()) {
                    if (drawn++ >= MAX_GHOSTS) {
                        break;
                    }
                    BlockPos pos = anchor.offset(entry.offset());
                    boolean router = level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof RouterBlock;
                    AABB ghost = new AABB(pos).deflate(0.2);
                    if (router) {
                        LevelRenderer.renderLineBox(pose, lines, ghost, 0.25F, 0.79F, 0.42F, 1.0F);
                    } else {
                        LevelRenderer.renderLineBox(pose, lines, ghost, 1.0F, 0.42F, 0.37F, 0.8F);
                    }
                }
                LevelRenderer.renderLineBox(pose, lines, new AABB(anchor).inflate(0.06), 1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static boolean here(ClientLevel level, Optional<GlobalPos> pos) {
        return pos.isPresent() && pos.get().dimension().equals(level.dimension());
    }

    private static BlockPos aimed(Minecraft minecraft) {
        return minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos() : null;
    }
}
