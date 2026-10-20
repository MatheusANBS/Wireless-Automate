package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * Coração do Ressonador, um por tier: o do Básico vai na receita do Núcleo, e cada um dos outros é gasto ao subir o
 * Núcleo para o tier dele pelo Cartão de Upgrade ({@link ResonanceCoreBlock#tryUpgrade}).
 */
public class ResonatorHeartItem extends Item {
    private final RouterTier tier;

    public ResonatorHeartItem(RouterTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public RouterTier tier() {
        return tier;
    }

    /** A pilha do Coração de {@code tier} no inventário do jogador, ou {@code null} sem nenhum. */
    static @Nullable ItemStack find(Player player, RouterTier tier) {
        Inventory inventory = player.getInventory();
        for (int i = 0, size = inventory.getContainerSize(); i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof ResonatorHeartItem heart && heart.tier == tier) {
                return stack;
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        Component name = Component.translatable(tier.translationKey());
        lines.add(Component.translatable(tier.previous() == null ? KEY + "craft" : KEY + "upgrade", name)
                .withStyle(ChatFormatting.GRAY));
    }

    private static final String KEY = "item.wirelessautomate.resonator_heart.tooltip.";
}
