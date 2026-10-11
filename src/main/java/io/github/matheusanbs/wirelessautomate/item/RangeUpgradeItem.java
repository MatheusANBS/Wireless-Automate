package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Upgrade de Alcance: no segundo slot de upgrade da tela do roteador, deixa o roteador vincular
 * máquinas no raio do tier (config {@code tiers.<tier>.machineRadius} e {@code maxMachines}). O
 * item em si não faz nada; quem lê o slot é o {@link io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity}.
 */
public class RangeUpgradeItem extends Item {
    public RangeUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        for (RouterTier tier : RouterTier.values()) {
            int radius = Config.machineRadius(tier);
            if (!tier.loaded() || radius <= 0) {
                continue;
            }
            tooltip.add(Component.translatable("item.wirelessautomate.range_upgrade.tooltip.tier",
                    Component.translatable(tier.translationKey()), radius, Config.maxMachines(tier))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.wirelessautomate.range_upgrade.tooltip.link")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
