package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.Level;

/**
 * O índice das cadeias de compactação do servidor, montado das receitas de mesa. Um par (menor, maior, n) só entra se
 * alguma receita de mesa volta e alguma vai de verdade ({@link #confirms}): uma receita faz de 1 do maior n do menor, e
 * outra faz de n do menor (3x3 para 9, 2x2 para 4) 1 do maior. Não é a receita que o jogador pegaria
 * ({@code getRecipeFor}): quando dois mods têm o mesmo material, a primeira receita que casa com o bloco de um pode ser a
 * do outro (o bloco de prata crua do Modern Industrialization vira a prata do Occultism), e a cadeia de cada mod se
 * perderia. Lã, arenito e neve (que não voltam) ficam de fora, e cru e lingote nunca se misturam (fundir não é receita
 * de mesa).
 *
 * <p>As cadeias do mesmo material em mods diferentes formam um {@link VariantGroup} ({@link VariantGroups}), montado na
 * mesma passada. A montagem custa uma vez por carga de receitas (subida do servidor e {@code /reload}) e as consultas
 * (item → cadeia, cadeia → grupo) são O(1).
 */
public final class CompactingChains {
    /** A ordem das variantes num grupo: o id da forma base em texto, então o mod em ordem alfabética. */
    private static final Comparator<CompactingChain> BY_BASE =
            Comparator.comparing(chain -> BuiltInRegistries.ITEM.getKey(chain.base()).toString());

    private static int generationCounter;
    private static CompactingChains instance = empty();

    private final Reference2ObjectOpenHashMap<Item, CompactingChain> index;
    private final CompactingChain[] byId;
    /** O grupo de cada cadeia pelo id, ou nulo sem variante; depois da montagem, só o gancho de teste o muda. */
    private final VariantGroup[] groups;
    private int groupCount;
    private final int generation;

    private CompactingChains(Reference2ObjectOpenHashMap<Item, CompactingChain> index, CompactingChain[] byId,
            VariantGroup[] groups, int groupCount, int generation) {
        this.index = index;
        this.byId = byId;
        this.groups = groups;
        this.groupCount = groupCount;
        this.generation = generation;
    }

    private static CompactingChains empty() {
        return new CompactingChains(new Reference2ObjectOpenHashMap<>(), new CompactingChain[0], new VariantGroup[0], 0,
                ++generationCounter);
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

    /** O grupo de variantes da cadeia, ou {@code null} quando ela não tem variante (ou é de outra geração). */
    @Nullable
    public VariantGroup groupOf(CompactingChain chain) {
        int id = chain.id();
        return id >= 0 && id < byId.length && byId[id] == chain ? groups[id] : null;
    }

    public int size() {
        return byId.length;
    }

    /** Quantos grupos de variantes o índice tem. */
    public int groupCount() {
        return groupCount;
    }

    /** Sobe a cada montagem: os ids das cadeias só valem dentro de uma geração. */
    public int generation() {
        return generation;
    }

    public static void reset() {
        instance = empty();
    }

    /**
     * Só para GameTest: liga duas cadeias do índice atual como variantes (o vanilla não tem o mesmo material em dois
     * mods), juntando os grupos delas num novo. As duas precisam ter as mesmas formas e fatores. O próximo
     * {@link #rebuild} monta um índice novo e desfaz a ligação.
     */
    public static void linkVariantsForTest(CompactingChain a, CompactingChain b) {
        CompactingChains chains = instance;
        if (a == b || chains.byId(a.id()) != a || chains.byId(b.id()) != b) {
            throw new IllegalArgumentException("as duas cadeias precisam ser do índice atual e diferentes");
        }
        if (!Arrays.equals(a.factors(), b.factors())) {
            throw new IllegalArgumentException("variantes precisam ter as mesmas formas e fatores");
        }
        Set<CompactingChain> members = new HashSet<>();
        for (CompactingChain chain : List.of(a, b)) {
            VariantGroup group = chains.groups[chain.id()];
            if (group == null) {
                members.add(chain);
            } else {
                members.addAll(group.chains());
            }
        }
        List<CompactingChain> sorted = new ArrayList<>(members);
        sorted.sort(BY_BASE);
        VariantGroup joined = new VariantGroup(chains.groupCount++, List.copyOf(sorted));
        for (CompactingChain chain : sorted) {
            chains.groups[chain.id()] = joined;
        }
    }

    /** Um par candidato: {@code n} do {@code small} fazem 1 do {@code big}. */
    public record Pair(Item small, Item big, int n) {
    }

    /** Uma receita de ida (n → 1) com os ingredientes não vazios, para o pré-filtro barato. */
    public record Forward(CraftingRecipe recipe, List<Ingredient> filled) {
        public static Forward of(CraftingRecipe recipe) {
            List<Ingredient> filled = new ArrayList<>(9);
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (!ingredient.isEmpty()) {
                    filled.add(ingredient);
                }
            }
            return new Forward(recipe, filled);
        }
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
        Map<Pair, List<CraftingRecipe>> backs = new HashMap<>();
        List<Pair> candidates = new ArrayList<>();
        Map<Item, List<Forward>> forwards = new HashMap<>();
        List<RecipeHolder<CraftingRecipe>> all = manager.getAllRecipesFor(RecipeType.CRAFTING);
        int skipped = 0;
        for (RecipeHolder<CraftingRecipe> holder : all) {
            try {
                collect(holder.value(), server, backs, candidates, forwards);
            } catch (RuntimeException | LinkageError e) {
                skipped++;
                WirelessAutomate.LOGGER.warn("Compactação: a receita {} lançou ao ser lida e ficou de fora", holder.id(), e);
            }
        }
        ChainGraph<Item> graph = newGraph();
        skipped += linkPairs(candidates, pair -> confirms(pair, backs.get(pair), forwards.get(pair.big()), level), graph);
        CompactingChains built = assemble(graph);
        instance = built;
        WirelessAutomate.LOGGER.info(
                "Compactação: {} cadeias e {} grupos de variantes de {} receitas em {} ms ({} receitas ou pares pulados)",
                built.size(), built.groupCount(), all.size(), (System.nanoTime() - start) / 1_000_000L, skipped);
    }

    /** O grafo vazio na ordem dos ids da registry, a que torna o índice determinístico. */
    public static ChainGraph<Item> newGraph() {
        return new ChainGraph<>(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item)));
    }

    /**
     * Separa a receita: um 2×2/3×3 que dá 1 vai para {@code forwards}; um item só que dá 4 ou 9 vira candidato, com a
     * receita guardada em {@code backs}.
     */
    private static void collect(CraftingRecipe recipe, MinecraftServer server, Map<Pair, List<CraftingRecipe>> backs,
            List<Pair> candidates, Map<Item, List<Forward>> forwards) {
        if (recipe.isSpecial()) {
            return;
        }
        Forward forward = Forward.of(recipe);
        List<Ingredient> filled = forward.filled();
        ItemStack result = recipe.getResultItem(server.registryAccess());
        int n = result.getCount();
        if (!result.isComponentsPatchEmpty()) {
            return;
        }
        if (n == 1 && (filled.size() == 4 || filled.size() == 9)) {
            forwards.computeIfAbsent(result.getItem(), k -> new ArrayList<>(2)).add(forward);
        } else if ((n == 4 || n == 9) && filled.size() == 1) {
            for (ItemStack big : filled.get(0).getItems()) {
                if (!big.isComponentsPatchEmpty()) {
                    continue;
                }
                Pair pair = new Pair(result.getItem(), big.getItem(), n);
                List<CraftingRecipe> found = backs.get(pair);
                if (found == null) {
                    found = new ArrayList<>(1);
                    backs.put(pair, found);
                    candidates.add(pair);
                }
                found.add(recipe);
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
     * A regra "alguma receita": o par vale se alguma das receitas de volta faz de 1 do maior n do menor e alguma das de
     * ida faz de n do menor (na grade 3x3 ou 2x2) 1 do maior, sem componentes, conferidas com {@code matches} e
     * {@code assemble}. As de ida passam antes pelo pré-filtro barato (n ingredientes, todos aceitam o menor).
     */
    public static boolean confirms(Pair pair, @Nullable List<CraftingRecipe> backs, @Nullable List<Forward> forwards,
            Level level) {
        if (backs == null || forwards == null) {
            return false;
        }
        CraftingInput down = CraftingInput.of(1, 1, List.of(new ItemStack(pair.big())));
        boolean back = false;
        for (CraftingRecipe recipe : backs) {
            if (recipe.matches(down, level) && gives(recipe, down, level, pair.small(), pair.n())) {
                back = true;
                break;
            }
        }
        if (!back) {
            return false;
        }
        ItemStack smallStack = new ItemStack(pair.small());
        int side = pair.n() == 9 ? 3 : 2;
        CraftingInput up = CraftingInput.of(side, side, Collections.nCopies(pair.n(), smallStack));
        for (Forward forward : forwards) {
            if (acceptsAll(forward.filled(), smallStack, pair.n()) && forward.recipe().matches(up, level)
                    && gives(forward.recipe(), up, level, pair.big(), 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * O índice das cadeias do grafo, numa geração nova, sem trocar o atual. Uma cadeia com mais de
     * {@link CompactingChain#MAX_FORMS} formas fica de fora com um aviso no log: o painel não a mandaria ao cliente
     * e o jogador não veria nem escolheria o alvo de uma cadeia que o Baú converte. Os grupos de variantes saem na
     * mesma passada, com as tags de cada forma lidas uma vez.
     */
    public static CompactingChains assemble(ChainGraph<Item> graph) {
        Reference2ObjectOpenHashMap<Item, CompactingChain> index = new Reference2ObjectOpenHashMap<>();
        List<CompactingChain> chains = new ArrayList<>();
        List<VariantGroups.Shape> shapes = new ArrayList<>();
        for (ChainGraph.Chain<Item> chain : graph.chains()) {
            Item[] forms = chain.forms().toArray(Item[]::new);
            if (forms.length > CompactingChain.MAX_FORMS) {
                WirelessAutomate.LOGGER.warn("Compactação: a cadeia de {} tem {} formas (o teto é {}) e ficou de fora",
                        BuiltInRegistries.ITEM.getKey(forms[0]), forms.length, CompactingChain.MAX_FORMS);
                continue;
            }
            ItemStack[] keys = new ItemStack[forms.length];
            List<Set<String>> tags = new ArrayList<>(forms.length);
            for (int i = 0; i < forms.length; i++) {
                keys[i] = new ItemStack(forms[i]);
                tags.add(specificTags(forms[i]));
            }
            CompactingChain built = new CompactingChain(chains.size(), forms, chain.factors(), keys);
            chains.add(built);
            shapes.add(new VariantGroups.Shape(chain.factors(), tags));
            for (Item form : forms) {
                index.put(form, built);
            }
        }
        VariantGroup[] groups = new VariantGroup[chains.size()];
        List<int[]> grouped = VariantGroups.of(shapes);
        for (int g = 0; g < grouped.size(); g++) {
            List<CompactingChain> members = new ArrayList<>(grouped.get(g).length);
            for (int id : grouped.get(g)) {
                members.add(chains.get(id));
            }
            members.sort(BY_BASE);
            VariantGroup group = new VariantGroup(g, List.copyOf(members));
            for (CompactingChain member : members) {
                groups[member.id()] = group;
            }
        }
        return new CompactingChains(index, chains.toArray(CompactingChain[]::new), groups, grouped.size(),
                ++generationCounter);
    }

    /** As tags {@code c:} específicas do item ({@link VariantGroups#specific}), em texto. */
    private static Set<String> specificTags(Item item) {
        Set<String> out = new HashSet<>();
        item.builtInRegistryHolder().tags().forEach(tag -> {
            String name = tag.location().toString();
            if (VariantGroups.specific(name)) {
                out.add(name);
            }
        });
        return out;
    }

    private static boolean acceptsAll(List<Ingredient> filled, ItemStack probe, int n) {
        if (filled.size() != n) {
            return false;
        }
        for (Ingredient ingredient : filled) {
            if (!ingredient.test(probe)) {
                return false;
            }
        }
        return true;
    }

    private static boolean gives(CraftingRecipe recipe, CraftingInput input, Level level, Item item, int count) {
        ItemStack out = recipe.assemble(input, level.registryAccess());
        return out.is(item) && out.getCount() == count && out.isComponentsPatchEmpty();
    }
}
