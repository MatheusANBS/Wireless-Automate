package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** O item da Doca de Padrões: o tier viaja no {@code block_state}, como no roteador; a dica mostra o alcance. */
public class PatternDockItem extends BlockItem {
    public PatternDockItem(PatternDockBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        RouterTier tier = StorageBlockItem.tierOf(stack);
        tooltip.add(Component.translatable(tier.translationKey()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.wirelessautomate.pattern_dock.reach",
                TierCoreItem.grouped(Config.dockRadius(tier)), TierCoreItem.grouped(Config.dockMaxMachines(tier)))
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
