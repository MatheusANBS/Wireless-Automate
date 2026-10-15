package io.github.matheusanbs.wirelessautomate.resonator;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/**
 * {@code {"type": "wirelessautomate:cut_crystal"}}: a receita do Cristal Lapidado. Igual a uma receita com
 * forma, mas só aceita Cristais de Sinal cheios: com a receita comum, quatro Opacos virariam um Lapidado
 * cheio. Por ser tipo próprio, fica fora do editor de receitas, como os anéis dos Cartões de Upgrade.
 */
public class CutCrystalRecipe extends ShapedRecipe {
    private final ShapedRecipePattern grade;
    private final ItemStack resultado;

    public CutCrystalRecipe(CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result) {
        super("", category, pattern, result);
        this.grade = pattern;
        this.resultado = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!super.matches(input, level)) {
            return false;
        }
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof SignalCrystalItem && !SignalCrystalItem.isFull(stack)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return resultado.copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CUT_CRYSTAL.get();
    }

    public static final MapCodec<CutCrystalRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)
                            .forGetter(CutCrystalRecipe::category),
                    ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.grade),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.resultado))
            .apply(instance, CutCrystalRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CutCrystalRecipe> STREAM_CODEC = StreamCodec.composite(
            CraftingBookCategory.STREAM_CODEC, CutCrystalRecipe::category,
            ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.grade,
            ItemStack.STREAM_CODEC, recipe -> recipe.resultado,
            CutCrystalRecipe::new);

    public static final class Serializer implements RecipeSerializer<CutCrystalRecipe> {
        @Override
        public MapCodec<CutCrystalRecipe> codec() {
            return MAP_CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CutCrystalRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
