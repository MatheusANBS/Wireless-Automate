package io.github.matheusanbs.wirelessautomate.recipe;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.registry.ModRecipes;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/**
 * As três receitas de subir para o tier {@link #tier()}, com um anel só ({@code {"type":
 * "wirelessautomate:tier_upgrade"}}, um JSON por tier). O anel são as oito casas em volta do centro; o centro
 * decide a receita:
 * <ul>
 *   <li>o ingrediente {@code step} (padrão: Redstone): o cartão de um degrau, do tier anterior na escada desta
 *   instância para este;</li>
 *   <li>um Cartão de Upgrade com origem k cujo destino é o degrau imediatamente abaixo: o cartão que pula, de k
 *   para este tier (um cartão antigo, sem origem, não casa);</li>
 *   <li>um roteador ou armazenamento do degrau imediatamente abaixo: o mesmo item, com os outros componentes, neste
 *   tier (a receita direta).</li>
 * </ul>
 * Como o anel é o mesmo nas três, subir degrau a degrau na bancada e montar o cartão custam o mesmo (o cartão, mais
 * uma Redstone). A escada (a origem do cartão de um degrau e o bloco aceito no centro) vem do {@link RouterTier}
 * em tempo de execução, não do JSON: as variantes do Ultimate continuam separadas só pelas condições de mod.
 *
 * <p>Estende {@link ShapedRecipe} para o livro de receitas, o JEI e o GuideME a verem como uma shaped comum: o
 * padrão herdado é o anel com o {@code step} no centro, e o resultado, o cartão de um degrau.
 */
public class TierUpgradeRecipe extends ShapedRecipe {
    /** Centro padrão do cartão de um degrau. */
    private static final Ingredient DEFAULT_STEP = Ingredient.of(Items.REDSTONE);
    /** Índice do centro na grade 3×3. */
    public static final int CENTER = 4;

    private final RouterTier tier;
    /** O anel como veio do JSON (3×3, centro vazio); o {@code Data} dele reescreve o JSON. */
    private final ShapedRecipePattern ring;
    private final Ingredient step;

    public TierUpgradeRecipe(CraftingBookCategory category, RouterTier tier, ShapedRecipePattern ring, Ingredient step) {
        super("", category, withCenter(ring, step), stepCard(tier));
        this.tier = tier;
        this.ring = ring;
        this.step = step;
    }

    /** O padrão herdado: o anel com o {@code step} no centro (sem {@code Data}: não se codifica, por isso o {@link #ring}). */
    private static ShapedRecipePattern withCenter(ShapedRecipePattern ring, Ingredient step) {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(9, Ingredient.EMPTY);
        if (ring.width() == 3 && ring.height() == 3) {
            for (int i = 0; i < 9; i++) {
                ingredients.set(i, ring.ingredients().get(i));
            }
        }
        ingredients.set(CENTER, step);
        return new ShapedRecipePattern(3, 3, ingredients, Optional.empty());
    }

    /** O cartão de um degrau ({@code previous()} → {@code tier}); vazio no Básico, que o codec recusa. */
    private static ItemStack stepCard(RouterTier tier) {
        RouterTier previous = tier.previous();
        if (!ModItems.TIER_CORES.containsKey(tier) || previous == null) {
            return ItemStack.EMPTY;
        }
        return TierCoreItem.card(tier, previous);
    }

    /** O tier de destino do anel. */
    public RouterTier tier() {
        return tier;
    }

    /** O ingrediente do centro do cartão de um degrau. */
    public Ingredient step() {
        return step;
    }

    /** O que sai da grade, ou vazio se ela não casa. Testa o centro primeiro (barato) e depois o anel. */
    private ItemStack result(CraftingInput input) {
        if (input.width() != 3 || input.height() != 3 || !tier.loaded()) {
            return ItemStack.EMPTY;
        }
        ItemStack center = input.getItem(CENTER);
        if (center.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = fromCenter(center);
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> around = new ArrayList<>(input.items());
        around.set(CENTER, ItemStack.EMPTY);
        return ring.matches(CraftingInput.of(3, 3, around)) ? out : ItemStack.EMPTY;
    }

    private ItemStack fromCenter(ItemStack center) {
        if (step.test(center)) {
            return stepCard(tier);
        }
        if (center.getItem() instanceof TierCoreItem core) {
            RouterTier origin = TierCoreItem.origin(center);
            // Cartão antigo, sem origem, não casa: não se converte cartão antigo.
            if (origin == null || origin.ordinal() >= core.tier().ordinal() || !core.tier().stepsTo(tier)) {
                return ItemStack.EMPTY;
            }
            return TierCoreItem.card(tier, origin);
        }
        if (center.getItem() instanceof RouterBlockItem || center.getItem() instanceof StorageBlockItem) {
            return RouterBlockItem.tierOf(center).stepsTo(tier) ? RouterUpgradeRecipe.withTier(center, tier)
                    : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !result(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result(input);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TIER_UPGRADE.get();
    }

    private static DataResult<TierUpgradeRecipe> validate(TierUpgradeRecipe recipe) {
        if (!ModItems.TIER_CORES.containsKey(recipe.tier)) {
            return DataResult.error(() -> "tier_upgrade: o tier " + recipe.tier.getSerializedName()
                    + " não tem Cartão de Upgrade");
        }
        if (recipe.ring.width() != 3 || recipe.ring.height() != 3) {
            return DataResult.error(() -> "tier_upgrade: o anel precisa ocupar a grade 3×3 inteira (veio "
                    + recipe.ring.width() + "×" + recipe.ring.height() + " depois de tirar as bordas vazias)");
        }
        if (!recipe.ring.ingredients().get(CENTER).isEmpty()) {
            return DataResult.error(() -> "tier_upgrade: o centro do pattern precisa ser um espaço");
        }
        return DataResult.success(recipe);
    }

    public static final MapCodec<TierUpgradeRecipe> MAP_CODEC = RecordCodecBuilder.<TierUpgradeRecipe>mapCodec(
            instance -> instance.group(
                    CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)
                            .forGetter(TierUpgradeRecipe::category),
                    RouterTier.CODEC.fieldOf("tier").forGetter(TierUpgradeRecipe::tier),
                    ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.ring),
                    Ingredient.CODEC_NONEMPTY.optionalFieldOf("step", DEFAULT_STEP).forGetter(TierUpgradeRecipe::step))
                    .apply(instance, TierUpgradeRecipe::new))
            .validate(TierUpgradeRecipe::validate);

    public static final StreamCodec<RegistryFriendlyByteBuf, TierUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
            CraftingBookCategory.STREAM_CODEC, TierUpgradeRecipe::category,
            RouterTier.STREAM_CODEC, TierUpgradeRecipe::tier,
            ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.ring,
            Ingredient.CONTENTS_STREAM_CODEC, TierUpgradeRecipe::step,
            TierUpgradeRecipe::new);

    public static final class Serializer implements RecipeSerializer<TierUpgradeRecipe> {
        @Override
        public MapCodec<TierUpgradeRecipe> codec() {
            return MAP_CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TierUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
