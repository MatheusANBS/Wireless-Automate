package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Uma peça da cascata do Ressonador (da Liga de Sinal crua ao Núcleo Condensado, mais a Moldura de Liga do Coração
 * Ultimate): item comum com uma linha de dica, a chave {@code <nome do item>.tooltip}.
 */
public class CascadeItem extends Item {
    private Component tip;

    public CascadeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (tip == null) {
            tip = Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY);
        }
        lines.add(tip);
    }
}
