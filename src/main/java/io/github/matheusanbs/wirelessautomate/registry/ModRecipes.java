package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.recipe.FilterCardCopyRecipe;
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

    private ModRecipes() {
    }
}
