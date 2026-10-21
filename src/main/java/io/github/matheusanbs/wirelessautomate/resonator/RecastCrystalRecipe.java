package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * {@code {"type": "wirelessautomate:recast_crystal"}}: refazer um cristal rachado. Sem forma: exatamente um cristal
 * rachado (Sinal, Lapidado ou Afinado) e {@code resonator.recastCrystals} Cristais de Sinal cheios, nada mais. Sai o
 * mesmo item, cheio e com a integridade cheia; nos Afinados, o tier fica.
 */
public class RecastCrystalRecipe extends CustomRecipe {
    public RecastCrystalRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return cracked(input) != null;
    }

    /** O cristal rachado da grade, se ela casa com a receita; senão {@code null}. */
    private static ItemStack cracked(CraftingInput input) {
        ItemStack rachado = null;
        int cheios = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (SignalCrystalItem.isCracked(stack)) {
                if (rachado != null) {
                    return null;
                }
                rachado = stack;
            } else if (stack.is(ModItems.SIGNAL_CRYSTAL.get()) && SignalCrystalItem.isFull(stack)) {
                cheios++;
            } else {
                return null;
            }
        }
        return cheios == Config.recastCrystals() ? rachado : null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack rachado = cracked(input);
        if (rachado == null) {
            return ItemStack.EMPTY;
        }
        ItemStack refeito = rachado.copyWithCount(1);
        refeito.remove(ModDataComponents.CRYSTAL_CHARGE.get());
        refeito.remove(ModDataComponents.CRYSTAL_INTEGRITY.get());
        return refeito;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height > Config.recastCrystals();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.RECAST_CRYSTAL.get();
    }
}
