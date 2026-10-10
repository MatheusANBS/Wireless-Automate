package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Item do roteador. O tier viaja no {@code BlockStateTag} do item (a loot table copia ao quebrar, pelo
 * {@code copy_state}, e o {@link BlockItem} aplica ao colocar), então um roteador Elite quebrado volta a ser
 * Elite ao ser colocado. No {@code main} (1.21) é o componente {@code block_state}.
 */
public class RouterBlockItem extends BlockItem {
    public RouterBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack withTier(RouterBlockItem item, RouterTier tier) {
        ItemStack stack = new ItemStack(item);
        setTier(stack, tier);
        return stack;
    }

    /**
     * Grava o tier no {@code BlockStateTag} do item (o mesmo do roteador e dos armazenamentos, com a propriedade
     * {@link RouterBlock#TIER}), mantendo as outras propriedades e o resto do NBT.
     */
    public static void setTier(ItemStack stack, RouterTier tier) {
        stack.getOrCreateTagElement(BLOCK_STATE_TAG).putString(RouterBlock.TIER.getName(), tier.getSerializedName());
    }

    /** O tier gravado no {@code BlockStateTag} do item; sem ele (ou inválido), Básico. */
    public static RouterTier tierOf(ItemStack stack) {
        CompoundTag states = stack.getTagElement(BLOCK_STATE_TAG);
        if (states == null || !states.contains(RouterBlock.TIER.getName())) {
            return RouterTier.BASIC;
        }
        return RouterBlock.TIER.getValue(states.getString(RouterBlock.TIER.getName())).orElse(RouterTier.BASIC);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(tierOf(stack).translationKey()).withStyle(ChatFormatting.GRAY));
    }
}
