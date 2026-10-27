package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Ingredientes fantasmas na Tela de Sinal: soltar um item do JEI na grade de itens do Baú (do dado ou do Ícone), ou no
 * campo do id do Ícone quando não há Baú ligado, escolhe o item ({@link TvScreen#ghostBounds()}). Só vale com a aba
 * Editar aberta e só para itens.
 */
final class TvGhostHandler implements IGhostIngredientHandler<TvScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(TvScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        int[] at = screen.ghostBounds();
        if (at == null || !(ingredient.getIngredient() instanceof ItemStack stack) || stack.is(Items.AIR)) {
            return List.of();
        }
        Rect2i area = new Rect2i(at[0], at[1], at[2], at[3]);
        return List.of(new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I dropped) {
                if (dropped instanceof ItemStack item) {
                    screen.editor().acceptGhost(BuiltInRegistries.ITEM.getKey(item.getItem()));
                }
            }
        });
    }

    @Override
    public void onComplete() {
    }
}
