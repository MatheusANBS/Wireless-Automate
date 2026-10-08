package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.FilterScreen;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockItem;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.NonNullList;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * Integração opcional com o JEI. Só o próprio JEI acha e carrega esta classe (pela anotação
 * {@link JeiPlugin}, no cliente); nenhum código do mod a referencia, então sem o JEI nada daqui é
 * carregado. Na tela de filtro: arrastar um ingrediente para a grade e Shift + clique nele na lista
 * acrescentam a entrada ({@link FilterGhostHandler}); as áreas extras do painel afastam o JEI. Na
 * bancada, mostra o upgrade do roteador com o núcleo do tier seguinte.
 */
@JeiPlugin
public final class WirelessAutomateJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = WirelessAutomate.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    /**
     * O upgrade na bancada ({@code RouterUpgradeRecipe}) é uma receita especial, que o JEI não
     * mostra; aqui vão os três casos do roteador e os três do Baú como receitas sem forma só de exibição.
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<RecipeHolder<CraftingRecipe>> upgrades = new ArrayList<>();
        for (RouterTier tier : RouterTier.values()) {
            RouterTier next = tier.next();
            if (next == null || !ModItems.TIER_CORES.containsKey(next)) {
                continue;
            }
            NonNullList<Ingredient> ingredients = NonNullList.of(Ingredient.EMPTY,
                    Ingredient.of(RouterBlockItem.withTier(ModItems.ROUTER.get(), tier)),
                    Ingredient.of(ModItems.TIER_CORES.get(next).get()));
            upgrades.add(new RecipeHolder<>(WirelessAutomate.id("jei/router_upgrade_" + next.getSerializedName()),
                    new ShapelessRecipe("router_upgrade", CraftingBookCategory.MISC,
                            RouterBlockItem.withTier(ModItems.ROUTER.get(), next), ingredients)));
            NonNullList<Ingredient> chest = NonNullList.of(Ingredient.EMPTY,
                    Ingredient.of(StorageChestBlockItem.withTier(ModItems.STORAGE_CHEST.get(), tier)),
                    Ingredient.of(ModItems.TIER_CORES.get(next).get()));
            upgrades.add(new RecipeHolder<>(WirelessAutomate.id("jei/storage_chest_upgrade_" + next.getSerializedName()),
                    new ShapelessRecipe("router_upgrade", CraftingBookCategory.MISC,
                            StorageChestBlockItem.withTier(ModItems.STORAGE_CHEST.get(), next), chest)));
        }
        registration.addRecipes(RecipeTypes.CRAFTING, upgrades);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(FilterScreen.class, new FilterGhostHandler());
        registration.addGuiContainerHandler(FilterScreen.class, new IGuiContainerHandler<FilterScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(FilterScreen screen) {
                return screen.extraAreas();
            }
        });
    }
}
