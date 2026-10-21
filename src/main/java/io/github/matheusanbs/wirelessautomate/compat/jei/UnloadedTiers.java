package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Esconde do JEI os itens dos tiers que não carregaram (os do Allthemodium sem o mod): os Cristais Afinados, os Pós
 * Carregados, os Corações do Ressonador e os Cartões de Upgrade desses tiers. Eles ficam registrados para o mundo não
 * perder nada quando o mod sai, e o JEI lista todo item registrado, mesmo fora da aba criativa.
 */
final class UnloadedTiers {
    private UnloadedTiers() {
    }

    static void hide(IIngredientManager ingredients) {
        Set<Item> hidden = new HashSet<>();
        for (Map<RouterTier, ? extends DeferredItem<?>> items : List.of(ModItems.TUNED_CRYSTALS,
                ModItems.CHARGED_DUSTS, ModItems.RESONATOR_HEARTS, ModItems.TIER_CORES)) {
            for (var entry : items.entrySet()) {
                if (!entry.getKey().loaded()) {
                    hidden.add(entry.getValue().get());
                }
            }
        }
        if (hidden.isEmpty()) {
            return;
        }
        List<ItemStack> stacks = ingredients.getAllItemStacks().stream()
                .filter(stack -> hidden.contains(stack.getItem())).toList();
        if (!stacks.isEmpty()) {
            ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks);
        }
    }
}
