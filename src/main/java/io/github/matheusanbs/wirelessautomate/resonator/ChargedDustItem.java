package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Pó Carregado: o catalisador da recarga do Núcleo do tier dele, do Allthemodium ao Ultimate ({@link CatalystRule}). */
public class ChargedDustItem extends Item {
    private final RouterTier tier;

    public ChargedDustItem(RouterTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public RouterTier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.wirelessautomate.charged_dust.tooltip",
                Component.translatable(tier.translationKey())).withStyle(ChatFormatting.GRAY));
    }
}
