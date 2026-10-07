package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerBox;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Contorno da área do Vinculador no mundo, enquanto ele está na mão: a caixa entre os dois cantos
 * ou, só com o canto 1, o bloco dele e, em modo Área, uma prévia até o bloco mirado. Uma caixa de
 * linhas por quadro, só com o item na mão e na dimensão da área; nada de busca no mundo.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class LinkerAreaRenderer {
    private LinkerAreaRenderer() {
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ItemStack stack = linkerInHand(player);
        LinkerArea area = stack == null ? null : LinkerItem.area(stack);
        if (area == null || !area.dimension().equals(minecraft.level.dimension())) {
            return;
        }
        float r = ((LinkerScreen.ACCENT >> 16) & 0xFF) / 255f;
        float g = ((LinkerScreen.ACCENT >> 8) & 0xFF) / 255f;
        float b = (LinkerScreen.ACCENT & 0xFF) / 255f;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LinkerBox box = area.box();
        if (box != null) {
            LevelRenderer.renderLineBox(pose, lines, aabb(box).inflate(0.002), r, g, b, 1f);
        } else {
            LevelRenderer.renderLineBox(pose, lines, new AABB(area.first()).inflate(0.002), r, g, b, 1f);
            BlockPos aimed = aimed(minecraft);
            if (aimed != null && LinkerItem.mode(stack) == LinkerMode.AREA) {
                LinkerBox next = area.withSecond(aimed).box();
                LevelRenderer.renderLineBox(pose, lines, aabb(next).inflate(0.002), r, g, b, 0.35f);
            }
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static @Nullable ItemStack linkerInHand(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof LinkerItem) {
                return stack;
            }
        }
        return null;
    }

    private static @Nullable BlockPos aimed(Minecraft minecraft) {
        return minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos() : null;
    }

    private static AABB aabb(LinkerBox box) {
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
    }
}
