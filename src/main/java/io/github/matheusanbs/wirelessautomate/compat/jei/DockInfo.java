package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;

/**
 * A página de informação da Doca de Padrões de cada tier carregado no JEI: o que ela faz e o alcance do tier (raio e
 * máximo de máquinas), com os valores da config.
 */
final class DockInfo {
    private static final String KEY = "jei.wirelessautomate.pattern_dock.info";

    private DockInfo() {
    }

    static void register(IRecipeRegistration registration) {
        for (RouterTier tier : RouterTier.values()) {
            if (tier.loaded()) {
                registration.addItemStackInfo(StorageBlockItem.withTier(ModItems.PATTERN_DOCK.get(), tier),
                        Component.translatable(KEY),
                        Component.translatable(KEY + ".reach", Component.translatable(tier.translationKey()),
                                TierCoreItem.grouped(Config.dockRadius(tier)),
                                TierCoreItem.grouped(Config.dockMaxMachines(tier))));
            }
        }
    }
}
