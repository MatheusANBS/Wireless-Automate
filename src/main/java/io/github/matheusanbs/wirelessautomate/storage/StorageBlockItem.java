package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Item de um armazenamento do mod. O tier viaja no {@code BlockStateTag}, como no roteador ({@link RouterBlockItem#setTier}); um bloco
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
        RouterBlockItem.setTier(stack, tier);
        return stack;
    }

    public static RouterTier tierOf(ItemStack stack) {
        return RouterBlockItem.tierOf(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        RouterTier tier = tierOf(stack);
        tooltip.add(Component.translatable(tier.translationKey()).withStyle(ChatFormatting.GRAY));
        StorageContents contents = ModDataComponents.STORAGE_CONTENTS.get(stack);
        long capacity = Config.storageCapacity(kind, tier);
        if (contents != null) {
            tooltip.add(StorageBlock.summary(kind, contents.total(), contents.types(), capacity).copy()
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("block.wirelessautomate.storage.empty", StorageBlock.capacity(kind, capacity))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (ModDataComponents.STORAGE_FILTER.has(stack)) {
            tooltip.add(Component.translatable("block.wirelessautomate.storage.filtered").withStyle(ChatFormatting.DARK_AQUA));
        }
        if (!kind.loaded()) {
            tooltip.add(Component.translatable("block.wirelessautomate." + kind.id + ".needs_mod")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
