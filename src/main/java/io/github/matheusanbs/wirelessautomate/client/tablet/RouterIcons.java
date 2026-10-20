package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/** O item do roteador de cada tier, criado uma vez (a Lista desenha o ícone por quadro, sem alocar). */
final class RouterIcons {
    /** Os tiers, numa constante ({@code values()} clona o array a cada chamada). */
    static final RouterTier[] TIERS = RouterTier.values();

    private static ItemStack[] stacks;

    private RouterIcons() {
    }

    static ItemStack of(RouterTier tier) {
        if (stacks == null) {
            ItemStack[] built = new ItemStack[TIERS.length];
            for (RouterTier t : TIERS) {
                built[t.ordinal()] = RouterBlockItem.withTier(ModItems.ROUTER.get(), t);
            }
            stacks = built;
        }
        return stacks[tier.ordinal()];
    }
}
