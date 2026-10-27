package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;

/**
 * Ingredientes fantasmas na Tela de Sinal: soltar um item do JEI na grade de itens do Baú (do dado ou do Ícone), ou no
 * campo do id do Ícone quando não há Baú ligado, escolhe o item ({@link TvScreen#ghostBounds()}). Só vale com a aba
 * Editar aberta; a grade aceita itens, e o espaço do Ícone também fluidos e químicos do Mekanism.
 */
final class TvGhostHandler implements IGhostIngredientHandler<TvScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(TvScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        int[] at = screen.ghostBounds();
        if (at == null || !screen.editor().ghostAccepts(ingredient.getIngredient())) {
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
                screen.editor().acceptGhostIngredient(dropped);
            }
        });
    }

    @Override
    public void onComplete() {
    }
}
