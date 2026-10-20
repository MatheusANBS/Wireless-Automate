package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.recipe.FilterCardCopyRecipe;
import io.github.matheusanbs.wirelessautomate.recipe.RouterUpgradeRecipe;
import io.github.matheusanbs.wirelessautomate.recipe.TierUpgradeRecipe;
import io.github.matheusanbs.wirelessautomate.resonator.CutCrystalRecipe;
import io.github.matheusanbs.wirelessautomate.resonator.RecastCrystalRecipe;
import io.github.matheusanbs.wirelessautomate.resonator.TuneCrystalRecipe;
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

    /** {@code {"type": "wirelessautomate:cut_crystal"}}: o Cristal Lapidado, só com Cristais de Sinal cheios. */
    public static final Supplier<RecipeSerializer<CutCrystalRecipe>> CUT_CRYSTAL =
            RECIPE_SERIALIZERS.register("cut_crystal", CutCrystalRecipe.Serializer::new);

    /**
     * {@code {"type": "wirelessautomate:tune_crystal"}}: sem forma, só com cristais cheios e sem rachadura (os
     * Cristais Afinados e o Pó de Ressonância).
     */
    public static final Supplier<RecipeSerializer<TuneCrystalRecipe>> TUNE_CRYSTAL =
            RECIPE_SERIALIZERS.register("tune_crystal", TuneCrystalRecipe.Serializer::new);

    /** {@code {"type": "wirelessautomate:recast_crystal"}}: um cristal rachado + Cristais de Sinal cheios. */
    public static final Supplier<RecipeSerializer<RecastCrystalRecipe>> RECAST_CRYSTAL =
            RECIPE_SERIALIZERS.register("recast_crystal",
                    () -> new SimpleCraftingRecipeSerializer<>(RecastCrystalRecipe::new));

    private ModRecipes() {
    }
}
