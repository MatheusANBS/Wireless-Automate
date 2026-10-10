package io.github.matheusanbs.wirelessautomate.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Upgrade de chunk loading: no slot de upgrade da tela do roteador, mantém carregado o chunk do
 * roteador e o da máquina (se for outro). Quem força os chunks é o
 * {@link io.github.matheusanbs.wirelessautomate.chunk.RouterChunkLoader}; o item em si não faz nada.
 */
public class ChunkLoaderUpgradeItem extends Item {
    public ChunkLoaderUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.wirelessautomate.chunk_loader_upgrade.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wirelessautomate.chunk_loader_upgrade.tooltip.slot")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
