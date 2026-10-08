package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;

/**
 * Item de um armazenamento do mod. O tier viaja no {@code block_state}, como no roteador; um bloco
 * quebrado cheio leva também o {@link StorageContents} (só a referência ao conteúdo e o resumo do
 * tooltip) e o filtro de entrada.
 */
public class StorageBlockItem extends BlockItem {
    private final StorageKind kind;

    public StorageBlockItem(StorageBlock block, Properties properties) {
        super(block, properties);
        this.kind = block.kind();
    }

    public StorageKind kind() {
        return kind;
    }

    /** O item do bloco no tier dado ({@code item} é o item de um {@link StorageBlock}). */
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
        tooltip.add(Component.translatable(tier.translationKey()).withStyle(ChatFormatting.GRAY));
        StorageContents contents = stack.get(ModDataComponents.STORAGE_CONTENTS.get());
        long capacity = Config.storageCapacity(kind, tier);
        if (contents != null) {
            tooltip.add(StorageBlock.summary(kind, contents.total(), contents.types(), capacity).copy()
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("block.wirelessautomate.storage.empty", StorageBlock.capacity(kind, capacity))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (stack.has(ModDataComponents.STORAGE_FILTER.get())) {
            tooltip.add(Component.translatable("block.wirelessautomate.storage.filtered").withStyle(ChatFormatting.DARK_AQUA));
        }
        if (kind == StorageKind.CHEMICAL_TANK && !Chemicals.LOADED) {
            tooltip.add(Component.translatable("block.wirelessautomate.storage_chemical_tank.needs_mekanism")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
