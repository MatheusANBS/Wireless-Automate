package io.github.matheusanbs.wirelessautomate.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Configurador, sem função por enquanto: o item, a receita e os componentes continuam para os que já existem
 * nos mundos não sumirem, e uma ferramenta nova ocupa o lugar dele numa próxima versão. Copiar e colar perfis
 * é do Tablet.
 */
public class ConfiguratorItem extends Item {
    public ConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.wirelessautomate.configurator.tooltip.no_function")
                .withStyle(ChatFormatting.GRAY));
    }
}
