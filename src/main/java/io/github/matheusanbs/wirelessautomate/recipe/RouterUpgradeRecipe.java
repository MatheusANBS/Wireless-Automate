package io.github.matheusanbs.wirelessautomate.recipe;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.registry.ModRecipes;
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
 * Upgrade do roteador na bancada, sem forma: exatamente um roteador e um núcleo do tier seguinte ao
 * dele (Básico + Avançado, Avançado + Elite, Elite + Ultimate), como o clique com o núcleo no
 * roteador colocado ({@link RouterBlock#tryUpgrade}). O resultado é o mesmo roteador, com os outros
 * componentes do item, no tier novo.
 */
public class RouterUpgradeRecipe extends CustomRecipe {
    public RouterUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** O roteador no tier novo, ou vazio se a grade não casa. */
    public static ItemStack upgraded(CraftingInput input) {
        ItemStack router = ItemStack.EMPTY;
        RouterTier core = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof RouterBlockItem && router.isEmpty()) {
                router = stack;
            } else if (stack.getItem() instanceof TierCoreItem item && core == null) {
                core = item.tier();
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (router.isEmpty() || core == null || RouterBlockItem.tierOf(router).next() != core) {
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
