package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.recipe.FilterCardCopyRecipe;
import io.github.matheusanbs.wirelessautomate.recipe.RouterUpgradeRecipe;
import io.github.matheusanbs.wirelessautomate.recipe.TierUpgradeRecipe;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, WirelessAutomate.MODID);

    /** {@code {"type": "wirelessautomate:filter_card_copy"}}: duplica um Cartão de Filtro. */
    public static final Supplier<RecipeSerializer<FilterCardCopyRecipe>> FILTER_CARD_COPY =
            RECIPE_SERIALIZERS.register("filter_card_copy",
                    () -> new SimpleCraftingRecipeSerializer<>(FilterCardCopyRecipe::new));

    /**
     * {@code {"type": "wirelessautomate:router_upgrade"}}: roteador ou armazenamento + Cartão de Upgrade; com
     * origem, só no tier dela.
     */
    public static final Supplier<RecipeSerializer<RouterUpgradeRecipe>> ROUTER_UPGRADE =
            RECIPE_SERIALIZERS.register("router_upgrade",
                    () -> new SimpleCraftingRecipeSerializer<>(RouterUpgradeRecipe::new));

    /**
     * {@code {"type": "wirelessautomate:tier_upgrade"}}: o anel de um tier ({@code tier_core_<tier>.json}), com
     * as três receitas pelo centro: Redstone, cartão um degrau abaixo ou bloco um degrau abaixo.
     */
    public static final Supplier<RecipeSerializer<TierUpgradeRecipe>> TIER_UPGRADE =
            RECIPE_SERIALIZERS.register("tier_upgrade", TierUpgradeRecipe.Serializer::new);

    private ModRecipes() {
    }
}
