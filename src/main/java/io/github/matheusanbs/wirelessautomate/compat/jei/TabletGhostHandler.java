package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * Ingrediente fantasma no Tablet: soltar um item do JEI no slot do ícone do grupo escolhido o usa como ícone
 * ({@link TabletScreen#ghostBounds()}). Enquanto o item está no ar o slot pisca ({@link TabletScreen#ghostDragging}),
 * além do realce do próprio JEI. Só itens.
 */
final class TabletGhostHandler implements IGhostIngredientHandler<TabletScreen> {
    private TabletScreen dragging;

    @Override
    public <I> List<Target<I>> getTargetsTyped(TabletScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        int[] at = screen.ghostBounds();
        if (at == null || !(ingredient.getIngredient() instanceof ItemStack)) {
            return List.of();
        }
        if (doStart) {
            dragging = screen;
            screen.ghostDragging(true);
        }
        Rect2i area = new Rect2i(at[0], at[1], at[2], at[3]);
        return List.of(new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I dropped) {
                screen.acceptGhost(dropped);
            }
        });
    }

    @Override
    public void onComplete() {
        if (dragging != null) {
            dragging.ghostDragging(false);
            dragging = null;
        }
    }
}
