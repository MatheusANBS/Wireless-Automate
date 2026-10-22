package io.github.matheusanbs.wirelessautomate.resonator;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModRecipes;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * {@code {"type": "wirelessautomate:tune_crystal"}}: receita sem forma que só aceita cristais cheios e sem
 * rachadura, para os Cristais Afinados, o Pó de Ressonância e as peças da cascata feitas com cristal (a Liga de
 * Sinal crua e a Lente Harmônica). Com a receita comum, um Opaco ou um cristal gasto entraria como se fosse cheio. O resultado sai cheio, com a integridade do cristal de entrada ({@link #assemble}).
 * Por ser tipo próprio, fica fora do editor de receitas, como o {@link CutCrystalRecipe}.
 */
public class TuneCrystalRecipe extends ShapelessRecipe {
    private static final int MAX_INGREDIENTS = 9;

    // O ShapelessRecipe guarda o resultado num campo de pacote, e os codecs precisam dele.
    private final ItemStack resultado;

    public TuneCrystalRecipe(CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
        super("", category, result, ingredients);
        this.resultado = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!super.matches(input, level)) {
            return false;
        }
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof SignalCrystalItem
                    && (!SignalCrystalItem.isFull(stack) || SignalCrystalItem.isCracked(stack))) {
                return false;
            }
        }
        return true;
    }

    /**
     * O resultado cheio; um cristal sai com a pior integridade dos cristais da grade (a ordem dela não importa), para
     * afinar não renovar a fadiga (só o refazer, {@link RecastCrystalRecipe}, devolve a integridade cheia). O Pó não é
     * cristal e sai como está.
     */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack out = super.assemble(input, registries);
        if (!(out.getItem() instanceof SignalCrystalItem)) {
            return out;
        }
        int worst = Integer.MAX_VALUE;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof SignalCrystalItem) {
                worst = Math.min(worst, SignalCrystalItem.integrity(stack));
            }
        }
        if (worst < Config.crystalIntegrity()) {
            out.set(ModDataComponents.CRYSTAL_INTEGRITY.get(), worst);
        }
        return out;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TUNE_CRYSTAL.get();
    }

    private static DataResult<NonNullList<Ingredient>> toList(List<Ingredient> list) {
        if (list.isEmpty()) {
            return DataResult.error(() -> "receita tune_crystal sem ingredientes");
        }
        if (list.size() > MAX_INGREDIENTS) {
            return DataResult.error(() -> "receita tune_crystal com mais de " + MAX_INGREDIENTS + " ingredientes");
        }
        return DataResult.success(NonNullList.of(Ingredient.EMPTY, list.toArray(Ingredient[]::new)));
    }

    public static final MapCodec<TuneCrystalRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)
                            .forGetter(TuneCrystalRecipe::category),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.resultado),
                    Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients")
                            .flatXmap(TuneCrystalRecipe::toList, DataResult::success)
                            .forGetter(TuneCrystalRecipe::getIngredients))
            .apply(instance, TuneCrystalRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TuneCrystalRecipe> STREAM_CODEC = StreamCodec.of(
            TuneCrystalRecipe::toNetwork, TuneCrystalRecipe::fromNetwork);

    private static void toNetwork(RegistryFriendlyByteBuf buf, TuneCrystalRecipe recipe) {
        CraftingBookCategory.STREAM_CODEC.encode(buf, recipe.category());
        buf.writeVarInt(recipe.getIngredients().size());
        for (Ingredient ingredient : recipe.getIngredients()) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
        }
        ItemStack.STREAM_CODEC.encode(buf, recipe.resultado);
    }

    private static TuneCrystalRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
        CraftingBookCategory category = CraftingBookCategory.STREAM_CODEC.decode(buf);
        int size = buf.readVarInt();
        NonNullList<Ingredient> ingredients = NonNullList.withSize(size, Ingredient.EMPTY);
        ingredients.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
        return new TuneCrystalRecipe(category, ItemStack.STREAM_CODEC.decode(buf), ingredients);
    }

    public static final class Serializer implements RecipeSerializer<TuneCrystalRecipe> {
        @Override
        public MapCodec<TuneCrystalRecipe> codec() {
            return MAP_CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TuneCrystalRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
