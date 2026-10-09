package io.github.matheusanbs.wirelessautomate.recipe;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.registry.ModRecipes;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Upgrade de tier na bancada, sem forma: exatamente um roteador (ou um armazenamento) e um cartão de um tier
 * acima do item (Básico + Esmeralda vale, como Avançado + Elite), como o clique com o
 * cartão no bloco colocado ({@link RouterBlock#tryUpgrade}, {@link StorageBlock#tryUpgrade}).
 * O resultado é o mesmo item, com os outros componentes (o conteúdo e o filtro de um armazenamento cheio),
 * no tier novo. Os dois guardam o tier no {@code block_state}, com a mesma propriedade.
 */
public class RouterUpgradeRecipe extends CustomRecipe {
    public RouterUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** O roteador ou o armazenamento no tier novo, ou vazio se a grade não casa. */
    public static ItemStack upgraded(CraftingInput input) {
        ItemStack router = ItemStack.EMPTY;
        RouterTier core = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if ((stack.getItem() instanceof RouterBlockItem || stack.getItem() instanceof StorageBlockItem)
                    && router.isEmpty()) {
                router = stack;
            } else if (stack.getItem() instanceof TierCoreItem item && core == null) {
                core = item.tier();
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (router.isEmpty() || core == null || !RouterBlockItem.tierOf(router).canUpgradeTo(core)) {
            return ItemStack.EMPTY;
        }
        ItemStack result = router.copyWithCount(1);
        result.set(DataComponents.BLOCK_STATE, result
                .getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
                .with(RouterBlock.TIER, core));
        return result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !upgraded(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return upgraded(input);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ROUTER_UPGRADE.get();
    }
}
