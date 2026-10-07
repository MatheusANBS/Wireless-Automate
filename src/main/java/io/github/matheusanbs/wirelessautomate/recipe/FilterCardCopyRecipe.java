package io.github.matheusanbs.wirelessautomate.recipe;

import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Duplicar Cartão de Filtro, sem forma: exatamente um cartão configurado (com o componente
 * {@code wirelessautomate:card_filter}) e um ou mais cartões vazios (sem o componente). O resultado
 * são os vazios com o componente copiado, e o configurado volta para a grade, como na cópia de livros.
 */
public class FilterCardCopyRecipe extends CustomRecipe {
    public FilterCardCopyRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static boolean configured(ItemStack stack) {
        return FilterCardItem.isCard(stack) && stack.has(ModDataComponents.CARD_FILTER.get());
    }

    /** O resultado: o configurado com uma unidade por cartão vazio, ou vazio se a grade não casa. */
    private static ItemStack copies(CraftingInput input) {
        ItemStack original = ItemStack.EMPTY;
        int blanks = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!FilterCardItem.isCard(stack)) {
                return ItemStack.EMPTY;
            }
            if (configured(stack)) {
                if (!original.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                original = stack;
            } else {
                blanks++;
            }
        }
        return original.isEmpty() || blanks == 0 ? ItemStack.EMPTY : original.copyWithCount(blanks);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !copies(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return copies(input);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < remaining.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.hasCraftingRemainingItem()) {
                remaining.set(i, stack.getCraftingRemainingItem());
            } else if (configured(stack)) {
                remaining.set(i, stack.copyWithCount(1));
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.FILTER_CARD_COPY.get();
    }
}
