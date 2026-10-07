package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.client.FilterScreen;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;

/**
 * Ingredientes fantasmas na tela de filtro. Um item serve num filtro de itens; um fluido, ou um
 * item que contém fluido (balde), num de fluidos ({@link FilterScreen#ghostEntry}). Arrastar para a
 * grade de entradas acrescenta; Shift + clique na lista do JEI também ({@link #quickMove}, JEI 19.28+;
 * nas versões anteriores o método não é chamado e só o arrastar funciona). O clique simples continua
 * sendo do JEI (ver receitas).
 */
final class FilterGhostHandler implements IGhostIngredientHandler<FilterScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(FilterScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        if (screen.ghostEntry(ingredient.getIngredient()).isEmpty()) {
            return List.of();
        }
        Rect2i area = screen.ghostArea();
        return List.of(new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I dropped) {
                screen.ghostEntry(dropped).ifPresent(screen::addGhost);
            }
        });
    }

    @Override
    public <I> boolean quickMove(FilterScreen screen, ITypedIngredient<I> ingredient) {
        return screen.ghostEntry(ingredient.getIngredient()).map(entry -> {
            screen.addGhost(entry);
            return true;
        }).orElse(false);
    }

    @Override
    public void onComplete() {
    }
}
