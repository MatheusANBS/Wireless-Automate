package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * O índice das cadeias de compactação do servidor, montado das receitas de mesa. Um par (menor, maior, n)
 * só entra se a receita de mesa vai e volta de verdade: n do menor (3x3 para 9, 2x2 para 4) dão 1 do maior,
 * e 1 do maior dá n do menor, conferido com o {@link RecipeManager} (a receita que o jogador pegaria). Assim
 * lã, arenito e neve (que não voltam) ficam de fora, e cru e lingote nunca se misturam (fundir não é receita
 * de mesa). A montagem custa uma vez por carga de receitas (subida do servidor e {@code /reload}) e a consulta
 * é O(1) por item.
 */
public final class CompactingChains {
    private static int generationCounter;
    private static CompactingChains instance = empty();

    private final Reference2ObjectOpenHashMap<Item, CompactingChain> index;
    private final CompactingChain[] byId;
    private final int generation;

    private CompactingChains(Reference2ObjectOpenHashMap<Item, CompactingChain> index, CompactingChain[] byId, int generation) {
        this.index = index;
        this.byId = byId;
        this.generation = generation;
    }

    private static CompactingChains empty() {
        return new CompactingChains(new Reference2ObjectOpenHashMap<>(), new CompactingChain[0], ++generationCounter);
    }

    public static CompactingChains get() {
        return instance;
    }

    /** A cadeia a que o item pertence; só vale para pilhas sem componentes (quem chama confere). */
    @Nullable
    public CompactingChain chainOf(Item item) {
        return index.get(item);
    }

    @Nullable
    public CompactingChain byId(int id) {
        return id >= 0 && id < byId.length ? byId[id] : null;
    }

    public int size() {
        return byId.length;
    }

    /** Sobe a cada montagem: os ids das cadeias só valem dentro de uma geração. */
    public int generation() {
        return generation;
    }

    public static void reset() {
        instance = empty();
    }

    private record Pair(Item small, Item big, int n) {
    }

    public static void rebuild(MinecraftServer server) {
        long start = System.nanoTime();
        RecipeManager manager = server.getRecipeManager();
        ServerLevel level = server.overworld();
        ChainGraph<Item> graph = new ChainGraph<>(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item)));
        Set<Pair> seen = new HashSet<>();
        List<RecipeHolder<CraftingRecipe>> all = manager.getAllRecipesFor(RecipeType.CRAFTING);
        for (RecipeHolder<CraftingRecipe> holder : all) {
            CraftingRecipe recipe = holder.value();
            if (recipe.isSpecial()) {
                continue;
            }
            Ingredient only = null;
            int filled = 0;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (!ingredient.isEmpty()) {
                    only = ingredient;
                    filled++;
                }
            }
            if (filled != 1) {
                continue;
            }
            ItemStack result = recipe.getResultItem(server.registryAccess());
            int n = result.getCount();
            if ((n != 4 && n != 9) || !result.isComponentsPatchEmpty()) {
                continue;
            }
            for (ItemStack big : only.getItems()) {
                if (big.isComponentsPatchEmpty() && seen.add(new Pair(result.getItem(), big.getItem(), n))
                        && roundTrip(manager, level, result.getItem(), big.getItem(), n)) {
                    graph.link(result.getItem(), big.getItem(), n);
                }
            }
        }
        Reference2ObjectOpenHashMap<Item, CompactingChain> index = new Reference2ObjectOpenHashMap<>();
        List<CompactingChain> chains = new ArrayList<>();
        for (ChainGraph.Chain<Item> chain : graph.chains()) {
            Item[] forms = chain.forms().toArray(Item[]::new);
            ItemStack[] keys = new ItemStack[forms.length];
            for (int i = 0; i < forms.length; i++) {
                keys[i] = new ItemStack(forms[i]);
            }
            CompactingChain built = new CompactingChain(chains.size(), forms, chain.factors(), keys);
            chains.add(built);
            for (Item form : forms) {
                index.put(form, built);
            }
        }
        instance = new CompactingChains(index, chains.toArray(CompactingChain[]::new), ++generationCounter);
        WirelessAutomate.LOGGER.info("Compactação: {} cadeias de {} receitas em {} ms", chains.size(), all.size(),
                (System.nanoTime() - start) / 1_000_000L);
    }

    private static boolean roundTrip(RecipeManager manager, ServerLevel level, Item small, Item big, int n) {
        CraftingInput down = CraftingInput.of(1, 1, List.of(new ItemStack(big)));
        Optional<RecipeHolder<CraftingRecipe>> back = manager.getRecipeFor(RecipeType.CRAFTING, down, level);
        if (back.isEmpty() || !gives(back.get().value(), down, level, small, n)) {
            return false;
        }
        int side = n == 9 ? 3 : 2;
        CraftingInput up = CraftingInput.of(side, side, Collections.nCopies(n, new ItemStack(small)));
        Optional<RecipeHolder<CraftingRecipe>> forth = manager.getRecipeFor(RecipeType.CRAFTING, up, level);
        return forth.isPresent() && gives(forth.get().value(), up, level, big, 1);
    }

    private static boolean gives(CraftingRecipe recipe, CraftingInput input, ServerLevel level, Item item, int count) {
        ItemStack out = recipe.assemble(input, level.registryAccess());
        return out.is(item) && out.getCount() == count && out.isComponentsPatchEmpty();
    }
}
