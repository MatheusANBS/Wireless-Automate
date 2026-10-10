package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.client.RecipeEditorScreen;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeDraft;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * Ingredientes fantasmas no editor de receitas: arrastar um item do JEI para um slot da grade põe
 * uma cópia dele ali ({@link RecipeEditorScreen#setGhost}), como escolher no inventário. Só itens, e só
 * com uma receita escolhida que não está desativada.
 */
final class RecipeEditorGhostHandler implements IGhostIngredientHandler<RecipeEditorScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(RecipeEditorScreen screen, ITypedIngredient<I> ingredient,
            boolean doStart) {
        if (!(ingredient.getIngredient() instanceof ItemStack) || !screen.canEditGrid()) {
            return List.of();
        }
        List<Target<I>> targets = new ArrayList<>(RecipeDraft.SLOTS);
        for (int i = 0; i < RecipeDraft.SLOTS; i++) {
            int slot = i;
            Rect2i area = screen.gridSlotArea(slot);
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I dropped) {
                    if (dropped instanceof ItemStack stack) {
                        screen.setGhost(slot, stack);
                    }
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }
}
