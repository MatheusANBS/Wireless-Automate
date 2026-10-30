package io.github.matheusanbs.wirelessautomate.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Upgrade do Baú (Mesa de Trabalho ou Compactação): vai num dos dois slots de upgrade do Baú, um de cada tipo. O
 * item em si não faz nada; quem lê os slots é o {@link io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity}.
 */
public class ChestUpgradeItem extends Item {
    public enum Kind {
        CRAFTING("crafting_upgrade"),
        COMPACTING("compacting_upgrade");

        private final String tooltipKey;

        Kind(String id) {
            this.tooltipKey = "tooltip.wirelessautomate." + id;
        }

        public String tooltipKey() {
            return tooltipKey;
        }
    }

    private final Kind kind;

    public ChestUpgradeItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(kind.tooltipKey()).withStyle(ChatFormatting.GRAY));
    }
}
