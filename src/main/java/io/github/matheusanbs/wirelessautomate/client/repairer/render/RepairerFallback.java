package io.github.matheusanbs.wirelessautomate.client.repairer.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlock;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Os cristais nos pedestais sem os shaders (shaderpack do Iris ou shader que não carregou): o modelo do item parado,
 * rachado, sem animação. O modelo estático do bloco não os tem, então sem isto os cristais postos ficariam invisíveis.
 */
final class RepairerFallback {
    /** O item rachado de cada tipo da {@link RepairerView}, criado uma vez e indexado por {@code kind}. */
    private static ItemStack[] stacks;

    private RepairerFallback() {
    }

    /** Desenha um item por linha com cristal e devolve quantos. */
    static int render(PoseStack poseStack, MultiBufferSource buffer, RepairerView view, BlockState state, Level level) {
        int quarter = RepairerGeometry.quarterFrom2D(state.getValue(RepairerBlock.FACING).get2DDataValue());
        int drawn = 0;
        for (int line = 0; line < RepairMath.LINES; line++) {
            byte kind = view.kind(line);
            if (kind == RepairerView.NONE) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(RepairerGeometry.pedestalX(quarter, line) / 16f,
                    (RepairerGeometry.PEDESTAL_TOP_Y + 2f) / 16f, RepairerGeometry.pedestalZ(quarter, line) / 16f);
            poseStack.scale(0.22f, 0.22f, 0.22f);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack(kind), ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, buffer, level, 0);
            poseStack.popPose();
            drawn++;
        }
        return drawn;
    }

    private static ItemStack stack(byte kind) {
        if (stacks == null) {
            RouterTier[] tiers = RouterTier.values();
            ItemStack[] built = new ItemStack[RepairerView.TUNED + tiers.length];
            built[RepairerView.SIGNAL] = cracked(new ItemStack(ModItems.SIGNAL_CRYSTAL.get()));
            built[RepairerView.CUT] = cracked(new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get()));
            for (RouterTier tier : tiers) {
                var tuned = ModItems.TUNED_CRYSTALS.get(tier);
                built[RepairerView.TUNED + tier.ordinal()] = tuned == null ? built[RepairerView.CUT]
                        : cracked(new ItemStack(tuned.get()));
            }
            stacks = built;
        }
        return kind < stacks.length && stacks[kind] != null ? stacks[kind] : stacks[RepairerView.CUT];
    }

    private static ItemStack cracked(ItemStack crystal) {
        SignalCrystalItem.setCharge(crystal, 0);
        crystal.set(ModDataComponents.CRYSTAL_INTEGRITY.get(), 0);
        return crystal;
    }
}
