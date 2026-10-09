package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.FilterScreen;
import io.github.matheusanbs.wirelessautomate.client.StorageListScreen;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import java.util.Optional;
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
 * bancada, mostra o upgrade do roteador com o cartão de qualquer tier acima. O tier é subtipo do roteador e
 * dos armazenamentos, para o JEI listar cada um separado, como a aba criativa.
 */
@JeiPlugin
public final class WirelessAutomateJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = WirelessAutomate.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    /**
     * O tier fica no componente {@code BLOCK_STATE}; sem um interpretador, o JEI junta todas as variantes
     * do mesmo item numa só (a Básica). O item sem o componente conta como Básico, como no jogo.
     */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModItems.ROUTER.get(), TierSubtype.INSTANCE);
        for (StorageKind kind : StorageKind.values()) {
            registration.registerSubtypeInterpreter(ModItems.STORAGE.get(kind).get(), TierSubtype.INSTANCE);
        }
    }

    /** O roteador e os armazenamentos guardam o tier do mesmo jeito ({@link StorageBlockItem#tierOf}). */
    private enum TierSubtype implements ISubtypeInterpreter<ItemStack> {
        INSTANCE;

        @Override
        public Object getSubtypeData(ItemStack stack, UidContext context) {
            return StorageBlockItem.tierOf(stack);
        }

        @Override
        public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            return StorageBlockItem.tierOf(stack).getSerializedName();
        }
    }

    /**
     * O upgrade na bancada ({@code RouterUpgradeRecipe}) é uma receita especial, que o JEI não
     * mostra; aqui vai um par (origem carregada, cartão do destino acima dela) para o roteador e para cada armazenamento
     * carregado, como receitas sem forma só de exibição.
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<RecipeHolder<CraftingRecipe>> upgrades = new ArrayList<>();
        for (RouterTier from : RouterTier.values()) {
            if (!from.loaded()) {
                continue;
            }
            for (RouterTier to : RouterTier.values()) {
                if (!to.loaded() || !ModItems.TIER_CORES.containsKey(to) || !from.canUpgradeTo(to)) {
                    continue;
                }
                String ids = from.getSerializedName() + "_" + to.getSerializedName();
                NonNullList<Ingredient> ingredients = NonNullList.of(Ingredient.EMPTY,
                        Ingredient.of(RouterBlockItem.withTier(ModItems.ROUTER.get(), from)),
                        Ingredient.of(ModItems.TIER_CORES.get(to).get()));
                upgrades.add(new RecipeHolder<>(WirelessAutomate.id("jei/router_upgrade_" + ids),
                        new ShapelessRecipe("router_upgrade", CraftingBookCategory.MISC,
                                RouterBlockItem.withTier(ModItems.ROUTER.get(), to), ingredients)));
                for (StorageKind kind : StorageKind.values()) {
                    if (!kind.loaded()) {
                        continue;
                    }
                    Item item = ModItems.STORAGE.get(kind).get();
                    NonNullList<Ingredient> storage = NonNullList.of(Ingredient.EMPTY,
                            Ingredient.of(StorageBlockItem.withTier(item, from)),
                            Ingredient.of(ModItems.TIER_CORES.get(to).get()));
                    upgrades.add(new RecipeHolder<>(WirelessAutomate.id("jei/" + kind.id + "_upgrade_" + ids),
                            new ShapelessRecipe("router_upgrade", CraftingBookCategory.MISC,
                                    StorageBlockItem.withTier(item, to), storage)));
                }
            }
        }
        registration.addRecipes(RecipeTypes.CRAFTING, upgrades);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(FilterScreen.class, new FilterGhostHandler());
        // Lista do Baú e dos Tanques: o tipo sob o mouse vale para os atalhos do JEI (R, U, A...).
        IIngredientManager ingredients = registration.getJeiHelpers().getIngredientManager();
        registration.addGuiContainerHandler(StorageListScreen.class, new IGuiContainerHandler<StorageListScreen>() {
            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                    IClickableIngredientFactory factory, StorageListScreen screen, double mouseX, double mouseY) {
                StorageListScreen.Hovered hovered = screen.ingredientAt(mouseX, mouseY);
                if (hovered == null) {
                    return Optional.empty();
                }
                return ingredients.createTypedIngredient(hovered.ingredient(), true)
                        .flatMap(typed -> factory.createBuilder(typed).buildWithArea(hovered.x(), hovered.y(), 16, 16));
            }
        });
        registration.addGuiContainerHandler(FilterScreen.class, new IGuiContainerHandler<FilterScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(FilterScreen screen) {
                return screen.extraAreas();
            }
        });
    }
}
