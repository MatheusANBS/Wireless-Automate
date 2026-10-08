package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
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
 * Item do Baú. O tier viaja no {@code block_state}, como no roteador; um Baú quebrado cheio leva
 * também o {@link StorageContents} (só a referência ao conteúdo e o resumo do tooltip).
 */
public class StorageChestBlockItem extends BlockItem {
    public StorageChestBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack withTier(StorageChestBlockItem item, RouterTier tier) {
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
        tooltip.add(Component.translatable(tier.translationKey()).withStyle(ChatFormatting.GRAY));
        StorageContents contents = stack.get(ModDataComponents.STORAGE_CONTENTS.get());
        long capacity = Config.chestCapacity(tier);
        if (contents != null) {
            tooltip.add(StorageChestBlock.summary(contents.total(), contents.types(), capacity).copy()
                    .withStyle(ChatFormatting.AQUA));
        } else {
            Component limit = capacity <= 0
                    ? Component.translatable("block.wirelessautomate.storage_chest.unlimited")
                    : Component.literal(TierCoreItem.grouped(capacity));
            tooltip.add(Component.translatable("block.wirelessautomate.storage_chest.empty", limit)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
