package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

/**
 * Item do roteador. O tier viaja no componente {@code block_state} (a loot table copia ao quebrar),
 * então um roteador Elite quebrado volta a ser Elite ao ser colocado.
 */
public class RouterBlockItem extends BlockItem {
    public RouterBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack withTier(RouterBlockItem item, RouterTier tier) {
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
        tooltip.add(Component.translatable(tierOf(stack).translationKey()).withStyle(ChatFormatting.GRAY));
    }
}
