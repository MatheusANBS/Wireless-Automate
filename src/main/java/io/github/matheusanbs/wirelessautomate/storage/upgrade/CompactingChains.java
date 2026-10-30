package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /** Um par candidato: {@code n} do {@code small} fazem 1 do {@code big}. */
    public record Pair(Item small, Item big, int n) {
    }

    /** Confere se o par vai e volta de verdade; o do servidor pergunta às receitas, e as de outro mod podem lançar. */
    @FunctionalInterface
    public interface PairCheck {
        boolean confirms(Pair pair);
    }

    /**
     * Remonta o índice das receitas de mesa e o troca no fim. As receitas são de qualquer mod: uma que lança (ao dar
     * os ingredientes, o resultado ou ao casar) é pulada com um aviso no log, e o resto do índice é montado.
     */
    public static void rebuild(MinecraftServer server) {
        long start = System.nanoTime();
        RecipeManager manager = server.getRecipeManager();
        ServerLevel level = server.overworld();
        Set<Pair> seen = new HashSet<>();
        List<Pair> candidates = new ArrayList<>();
        Map<Item, List<List<Ingredient>>> forward = new HashMap<>();
        List<RecipeHolder<CraftingRecipe>> all = manager.getAllRecipesFor(RecipeType.CRAFTING);
        int skipped = 0;
        for (RecipeHolder<CraftingRecipe> holder : all) {
            try {
                collect(holder.value(), server, seen, candidates, forward);
            } catch (RuntimeException | LinkageError e) {
                skipped++;
                WirelessAutomate.LOGGER.warn("Compactação: a receita {} lançou ao ser lida e ficou de fora", holder.id(), e);
            }
        }
        ChainGraph<Item> graph = newGraph();
        skipped += linkPairs(candidates, pair -> hasForward(forward.get(pair.big()), new ItemStack(pair.small()), pair.n())
                && roundTrip(manager, level, new ItemStack(pair.small()), pair.big(), pair.n()), graph);
        CompactingChains built = assemble(graph);
        instance = built;
        WirelessAutomate.LOGGER.info("Compactação: {} cadeias de {} receitas em {} ms ({} receitas ou pares pulados)",
                built.size(), all.size(), (System.nanoTime() - start) / 1_000_000L, skipped);
    }

    /** O grafo vazio na ordem dos ids da registry, a que torna o índice determinístico. */
    public static ChainGraph<Item> newGraph() {
        return new ChainGraph<>(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item)));
    }

    /** Separa a receita: um 2×2/3×3 que dá 1 vai para {@code forward}; um item só que dá 4 ou 9 vira candidato. */
    private static void collect(CraftingRecipe recipe, MinecraftServer server, Set<Pair> seen, List<Pair> candidates,
            Map<Item, List<List<Ingredient>>> forward) {
        if (recipe.isSpecial()) {
            return;
        }
        List<Ingredient> filled = new ArrayList<>(9);
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) {
                filled.add(ingredient);
            }
        }
        ItemStack result = recipe.getResultItem(server.registryAccess());
        int n = result.getCount();
        if (!result.isComponentsPatchEmpty()) {
            return;
        }
        if (n == 1 && (filled.size() == 4 || filled.size() == 9)) {
            forward.computeIfAbsent(result.getItem(), k -> new ArrayList<>(2)).add(filled);
        } else if ((n == 4 || n == 9) && filled.size() == 1) {
            for (ItemStack big : filled.get(0).getItems()) {
                Pair pair = new Pair(result.getItem(), big.getItem(), n);
                if (big.isComponentsPatchEmpty() && seen.add(pair)) {
                    candidates.add(pair);
                }
            }
        }
    }

    /**
     * Liga ao grafo os pares que o conferidor confirma. Um par cujo conferidor lança fica de fora, com um aviso no
     * log por par, e os outros seguem.
     *
     * @return quantos pares lançaram
     */
    public static int linkPairs(List<Pair> candidates, PairCheck check, ChainGraph<Item> graph) {
        int skipped = 0;
        for (Pair pair : candidates) {
            boolean confirmed;
            try {
                confirmed = check.confirms(pair);
            } catch (RuntimeException | LinkageError e) {
                skipped++;
                WirelessAutomate.LOGGER.warn("Compactação: conferir {} × {} → {} lançou e o par ficou de fora",
                        pair.n(), BuiltInRegistries.ITEM.getKey(pair.small()), BuiltInRegistries.ITEM.getKey(pair.big()), e);
                continue;
            }
            if (confirmed) {
                graph.link(pair.small(), pair.big(), pair.n());
            }
        }
        return skipped;
    }

    /**
     * O índice das cadeias do grafo, numa geração nova, sem trocar o atual. Uma cadeia com mais de
     * {@link CompactingChain#MAX_FORMS} formas fica de fora com um aviso no log: o painel não a mandaria ao cliente
     * e o jogador não veria nem escolheria o alvo de uma cadeia que o Baú converte.
     */
    public static CompactingChains assemble(ChainGraph<Item> graph) {
        Reference2ObjectOpenHashMap<Item, CompactingChain> index = new Reference2ObjectOpenHashMap<>();
        List<CompactingChain> chains = new ArrayList<>();
        for (ChainGraph.Chain<Item> chain : graph.chains()) {
            Item[] forms = chain.forms().toArray(Item[]::new);
            if (forms.length > CompactingChain.MAX_FORMS) {
                WirelessAutomate.LOGGER.warn("Compactação: a cadeia de {} tem {} formas (o teto é {}) e ficou de fora",
                        BuiltInRegistries.ITEM.getKey(forms[0]), forms.length, CompactingChain.MAX_FORMS);
                continue;
            }
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
        return new CompactingChains(index, chains.toArray(CompactingChain[]::new), ++generationCounter);
    }

    /** Pré-filtro barato: alguma receita do maior tem n ingredientes e todos aceitam o menor. */
    private static boolean hasForward(@Nullable List<List<Ingredient>> recipes, ItemStack probe, int n) {
        if (recipes == null) {
            return false;
        }
        for (List<Ingredient> filled : recipes) {
            if (filled.size() != n) {
                continue;
            }
            boolean all = true;
            for (Ingredient ingredient : filled) {
                if (!ingredient.test(probe)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    private static boolean roundTrip(RecipeManager manager, ServerLevel level, ItemStack smallStack, Item big, int n) {
        Item small = smallStack.getItem();
        CraftingInput down = CraftingInput.of(1, 1, List.of(new ItemStack(big)));
        Optional<RecipeHolder<CraftingRecipe>> back = manager.getRecipeFor(RecipeType.CRAFTING, down, level);
        if (back.isEmpty() || !gives(back.get().value(), down, level, small, n)) {
            return false;
        }
        int side = n == 9 ? 3 : 2;
        CraftingInput up = CraftingInput.of(side, side, Collections.nCopies(n, smallStack));
        Optional<RecipeHolder<CraftingRecipe>> forth = manager.getRecipeFor(RecipeType.CRAFTING, up, level);
        return forth.isPresent() && gives(forth.get().value(), up, level, big, 1);
    }

    private static boolean gives(CraftingRecipe recipe, CraftingInput input, ServerLevel level, Item item, int count) {
        ItemStack out = recipe.assemble(input, level.registryAccess());
        return out.is(item) && out.getCount() == count && out.isComponentsPatchEmpty();
    }
}
