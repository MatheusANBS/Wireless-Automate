package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen;
import io.github.matheusanbs.wirelessautomate.client.recipe.RecipeEditorScreen;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageListScreen;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
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
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.recipe.TierUpgradeRecipe;

/**
 * Integração opcional com o JEI. Só o próprio JEI acha e carrega esta classe (pela anotação
 * {@link JeiPlugin}, no cliente); nenhum código do mod a referencia, então sem o JEI nada daqui é
 * carregado. Na tela de filtro: arrastar um ingrediente para a grade e Shift + clique nele na lista
 * acrescentam a entrada ({@link FilterGhostHandler}); as áreas extras do painel afastam o JEI. Na
 * bancada, mostra as três receitas de cada anel (cartão de um degrau, cartão que pula e receita direta) e o
 * upgrade bloco + cartão com a origem do cartão. O tier é subtipo do roteador e dos armazenamentos, e a origem
 * é subtipo do cartão, para o JEI listar cada um separado, como a aba criativa.
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
        for (var card : ModItems.TIER_CORES.values()) {
            registration.registerSubtypeInterpreter(card.get(), OriginSubtype.INSTANCE);
        }
        registration.registerSubtypeInterpreter(ModItems.RESONANCE_CORE.get(), CoreTierSubtype.INSTANCE);
    }

    /**
     * A origem do Cartão de Upgrade ({@code upgrade_from}): sem isso o JEI junta "Básico → Elite" e "Avançado →
     * Elite". O cartão antigo, sem origem, fica num subtipo próprio.
     */
    private enum OriginSubtype implements ISubtypeInterpreter<ItemStack> {
        INSTANCE;

        @Override
        public Object getSubtypeData(ItemStack stack, UidContext context) {
            RouterTier origin = TierCoreItem.origin(stack);
            return origin == null ? "legacy" : origin;
        }

        @Override
        public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            RouterTier origin = TierCoreItem.origin(stack);
            return origin == null ? "" : origin.getSerializedName();
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

    /** O Núcleo de Ressonância guarda o tier no {@code BLOCK_STATE}, como o roteador. */
    private enum CoreTierSubtype implements ISubtypeInterpreter<ItemStack> {
        INSTANCE;

        @Override
        public Object getSubtypeData(ItemStack stack, UidContext context) {
            return ResonanceCoreItem.tierOf(stack);
        }

        @Override
        public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            return ResonanceCoreItem.tierOf(stack).getSerializedName();
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        ResonatorJei.registerCategories(registration);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        ResonatorJei.registerCatalysts(registration);
    }

    /**
     * As receitas de exibição. O cartão de um degrau aparece sozinho: a receita de anel ({@link TierUpgradeRecipe})
     * é uma {@code ShapedRecipe} com a Redstone no centro. As outras duas do mesmo anel (o centro decide) e o upgrade
     * bloco + cartão ({@code RouterUpgradeRecipe}, receita especial) vão aqui como receitas só de exibição:
     * <ul>
     *   <li>a receita direta, o anel com o roteador (e cada armazenamento carregado) do degrau anterior no centro;</li>
     *   <li>um exemplo do cartão que pula por destino, com o cartão "Básico → degrau anterior" no centro;</li>
     *   <li>bloco + cartão, um par por origem carregada e destino acima, com o cartão daquela origem.</li>
     * </ul>
     * Os anéis vêm das receitas carregadas no cliente, então um datapack que troca um anel troca também o que o
     * JEI mostra.
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<RecipeHolder<CraftingRecipe>> shown = new ArrayList<>();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            WirelessAutomate.LOGGER.warn("JEI: sem mundo carregado, as receitas de anel não foram mostradas");
        } else {
            for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                if (holder.value() instanceof TierUpgradeRecipe ring) {
                    ringRecipes(ring, shown);
                }
            }
        }
        for (RouterTier from : RouterTier.values()) {
            if (!from.loaded()) {
                continue;
            }
            for (RouterTier to : RouterTier.values()) {
                if (!to.loaded() || !ModItems.TIER_CORES.containsKey(to) || !from.acceptsCard(from, to)) {
                    continue;
                }
                String ids = from.getSerializedName() + "_" + to.getSerializedName();
                ItemStack card = TierCoreItem.card(to, from);
                NonNullList<Ingredient> ingredients = NonNullList.of(Ingredient.EMPTY,
                        Ingredient.of(RouterBlockItem.withTier(ModItems.ROUTER.get(), from)), Ingredient.of(card));
                shown.add(new RecipeHolder<>(WirelessAutomate.id("jei/router_upgrade_" + ids),
                        new ShapelessRecipe("router_upgrade", CraftingBookCategory.MISC,
                                RouterBlockItem.withTier(ModItems.ROUTER.get(), to), ingredients)));
                for (StorageKind kind : StorageKind.values()) {
                    if (!kind.loaded()) {
                        continue;
                    }
                    Item item = ModItems.STORAGE.get(kind).get();
                    NonNullList<Ingredient> storage = NonNullList.of(Ingredient.EMPTY,
                            Ingredient.of(StorageBlockItem.withTier(item, from)), Ingredient.of(card));
                    shown.add(new RecipeHolder<>(WirelessAutomate.id("jei/" + kind.id + "_upgrade_" + ids),
                            new ShapelessRecipe("router_upgrade", CraftingBookCategory.MISC,
                                    StorageBlockItem.withTier(item, to), storage)));
                }
            }
        }
        registration.addRecipes(RecipeTypes.CRAFTING, shown);
        ResonatorJei.registerRecipes(registration);
        WirelessAutomate.LOGGER.info("JEI: {} receitas de exibição de upgrade registradas", shown.size());
    }

    /** A receita direta (roteador e armazenamentos) e o exemplo do cartão que pula de um anel. */
    private static void ringRecipes(TierUpgradeRecipe ring, List<RecipeHolder<CraftingRecipe>> shown) {
        RouterTier tier = ring.tier();
        RouterTier from = tier.previous();
        if (from == null || !tier.loaded()) {
            return;
        }
        String id = tier.getSerializedName();
        shown.add(display("jei/router_direct_" + id, ring, RouterBlockItem.withTier(ModItems.ROUTER.get(), from),
                RouterBlockItem.withTier(ModItems.ROUTER.get(), tier)));
        for (StorageKind kind : StorageKind.values()) {
            if (kind.loaded()) {
                Item item = ModItems.STORAGE.get(kind).get();
                shown.add(display("jei/" + kind.id + "_direct_" + id, ring,
                        StorageBlockItem.withTier(item, from), StorageBlockItem.withTier(item, tier)));
            }
        }
        if (from.previous() != null && ModItems.TIER_CORES.containsKey(from)) {
            shown.add(display("jei/tier_core_jump_" + id, ring,
                    TierCoreItem.card(from, RouterTier.BASIC), TierCoreItem.card(tier, RouterTier.BASIC)));
        }
    }

    /** O anel de {@code ring} com {@code center} no meio, como uma shaped 3×3 só de exibição. */
    private static RecipeHolder<CraftingRecipe> display(String path, TierUpgradeRecipe ring, ItemStack center,
            ItemStack result) {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(9, Ingredient.EMPTY);
        for (int i = 0; i < 9; i++) {
            ingredients.set(i, ring.getIngredients().get(i));
        }
        ingredients.set(TierUpgradeRecipe.CENTER, Ingredient.of(center));
        return new RecipeHolder<>(WirelessAutomate.id(path), new ShapedRecipe("tier_upgrade", ring.category(),
                new ShapedRecipePattern(3, 3, ingredients, Optional.empty()), result));
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(FilterScreen.class, new FilterGhostHandler());
        registration.addGhostIngredientHandler(RecipeEditorScreen.class, new RecipeEditorGhostHandler());
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
