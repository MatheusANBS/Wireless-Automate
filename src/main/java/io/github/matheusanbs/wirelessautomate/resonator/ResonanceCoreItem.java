package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;

/** O item do Núcleo de Ressonância; o tier viaja no {@code block_state}, como no roteador e nos armazenamentos. */
public class ResonanceCoreItem extends BlockItem {
    public ResonanceCoreItem(ResonanceCoreBlock block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack withTier(Item item, RouterTier tier) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(RouterBlock.TIER, tier));
        return stack;
    }

    public static RouterTier tierOf(ItemStack stack) {
        RouterTier tier = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
                .get(RouterBlock.TIER);
        return tier != null ? tier : RouterTier.BASIC;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        RouterTier tier = tierOf(stack);
        tooltip.add(Component.translatable("block.wirelessautomate.resonance_core.tooltip",
                Component.translatable(tier.translationKey()),
                TierCoreItem.grouped(CoreMath.chambers(tier.ordinal()))).withStyle(ChatFormatting.GRAY));
    }
}
