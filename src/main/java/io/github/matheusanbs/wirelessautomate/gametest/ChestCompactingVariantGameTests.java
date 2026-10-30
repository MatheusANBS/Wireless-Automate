package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ChestCompactingGameTests.chest;
import static io.github.matheusanbs.wirelessautomate.gametest.ChestCompactingGameTests.count;
import static io.github.matheusanbs.wirelessautomate.gametest.ChestCompactingGameTests.putUpgrade;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorageHandler;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChain;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChains;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingRules;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.VariantGroup;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * As cadeias de mods diferentes: a regra "alguma receita" (com receitas montadas no teste) e as variantes do mesmo
 * material no Baú. O vanilla não tem o mesmo material em dois mods, então o gancho de teste
 * {@link CompactingChains#linkVariantsForTest} liga a cadeia do ferro e a do ouro (pepita, lingote, bloco nas duas).
 * A ligação vale para o índice inteiro, então esses testes ficam num lote só deles; o último a terminar remonta o
 * índice e a desfaz.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ChestCompactingVariantGameTests {
    private static final String BATCH = "wirelessautomate_compactacao_variantes";
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final CompactingRules COMPRESS = CompactingRules.DEFAULT.withMode(CompactingMode.COMPRESS);
    /** Quantos testes do lote ainda usam a ligação ferro ~ ouro. */
    private static int linkUsers;

    // ------------------------------------------------------------------ a regra "alguma receita"

    private static ShapelessRecipe shapeless(Item result, int count, Ingredient... ingredients) {
        return new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(result, count),
                NonNullList.of(Ingredient.EMPTY, ingredients));
    }

    /**
     * Como o bloco de prata crua do Modern Industrialization com o Occultism: a primeira receita de volta do bloco de
     * ferro dá outro item (ouro), e só a segunda dá o lingote de ferro. O par entra porque alguma receita volta e
     * alguma vai. A receita de ida com o centro fixo (como a do MI) aceita 9 lingotes de ferro e recusa 9 de ouro.
     */
    @GameTest(template = "empty")
    public static void algumaReceitaAchaACadeiaDoOutroMod(GameTestHelper helper) {
        CraftingRecipe otherFirst = shapeless(Items.GOLD_INGOT, 9, Ingredient.of(Items.IRON_BLOCK, Items.GOLD_BLOCK));
        CraftingRecipe own = shapeless(Items.IRON_INGOT, 9, Ingredient.of(Items.IRON_BLOCK));
        Ingredient ring = Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT);
        Ingredient center = Ingredient.of(Items.IRON_INGOT);
        NonNullList<Ingredient> grid = NonNullList.of(Ingredient.EMPTY, ring, ring, ring, ring, center, ring, ring, ring, ring);
        CraftingRecipe centered = new ShapedRecipe("", CraftingBookCategory.MISC,
                new ShapedRecipePattern(3, 3, grid, Optional.empty()), new ItemStack(Items.IRON_BLOCK));
        List<CompactingChains.Forward> forwards = List.of(CompactingChains.Forward.of(centered));
        CompactingChains.Pair iron = new CompactingChains.Pair(Items.IRON_INGOT, Items.IRON_BLOCK, 9);
        CompactingChains.Pair gold = new CompactingChains.Pair(Items.GOLD_INGOT, Items.IRON_BLOCK, 9);

        helper.assertTrue(CompactingChains.confirms(iron, List.of(otherFirst, own), forwards, helper.getLevel()),
                "a volta pela segunda receita não fechou");
        helper.assertFalse(CompactingChains.confirms(iron, List.of(otherFirst), forwards, helper.getLevel()),
                "sem a receita própria de volta, o par entrou");
        helper.assertFalse(CompactingChains.confirms(gold, List.of(otherFirst), forwards, helper.getLevel()),
                "9 de ouro não fazem o bloco de ferro com o centro fixo");
        helper.assertFalse(CompactingChains.confirms(iron, List.of(own), null, helper.getLevel()), "sem receita de ida");
        helper.succeed();
    }

    // ------------------------------------------------------------------ as variantes no Baú

    /**
     * Liga o ferro e o ouro como variantes enquanto houver teste do lote rodando; o último a terminar remonta o índice
     * (o {@code rebuild} desfaz a ligação). Confere antes que as duas cadeias têm as mesmas formas.
     */
    private static VariantGroup linkIronAndGold(GameTestHelper helper) {
        CompactingChain iron = CompactingChains.get().chainOf(Items.IRON_INGOT);
        CompactingChain gold = CompactingChains.get().chainOf(Items.GOLD_INGOT);
        helper.assertTrue(iron != null && gold != null, "sem as cadeias do ferro e do ouro");
        helper.assertValueEqual(iron.forms().length, gold.forms().length, "o ferro e o ouro com formas diferentes");
        helper.assertValueEqual(List.of(iron.forms()), List.of(Items.IRON_NUGGET, Items.IRON_INGOT, Items.IRON_BLOCK),
                "as formas do ferro");
        helper.assertValueEqual(List.of(gold.forms()), List.of(Items.GOLD_NUGGET, Items.GOLD_INGOT, Items.GOLD_BLOCK),
                "as formas do ouro");
        if (CompactingChains.get().groupOf(iron) == null || CompactingChains.get().groupOf(iron) != CompactingChains.get().groupOf(gold)) {
            CompactingChains.linkVariantsForTest(iron, gold);
        }
        linkUsers++;
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {
            }

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                release(helper);
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                release(helper);
            }

            @Override
            public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            }
        });
        return CompactingChains.get().groupOf(iron);
    }

    private static void release(GameTestHelper helper) {
        if (--linkUsers == 0) {
            CompactingChains.rebuild(helper.getLevel().getServer());
        }
    }

    private static CompactingChain chainOf(Item item) {
        return CompactingChains.get().chainOf(item);
    }

    /** O valor do ferro e do ouro juntos, em pepitas. */
    private static long nuggets(StorageChestBlockEntity chest) {
        return count(chest, Items.IRON_NUGGET) + count(chest, Items.GOLD_NUGGET)
                + 9 * (count(chest, Items.IRON_INGOT) + count(chest, Items.GOLD_INGOT))
                + 81 * (count(chest, Items.IRON_BLOCK) + count(chest, Items.GOLD_BLOCK));
    }

    private static void assertForms(GameTestHelper helper, StorageChestBlockEntity chest, Map<Item, Long> expected) {
        for (Map.Entry<Item, Long> entry : expected.entrySet()) {
            helper.assertValueEqual(count(chest, entry.getKey()), entry.getValue(), entry.getKey().toString());
        }
    }

    /** O padrão sem escolha é a primeira base em ordem alfabética: {@code minecraft:gold_nugget}. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void preferidaPadraoEAPrimeiraEmOrdemAlfabetica(GameTestHelper helper) {
        VariantGroup group = linkIronAndGold(helper);
        helper.assertValueEqual(group.chains().get(0), chainOf(Items.GOLD_INGOT), "a primeira do grupo");
        helper.assertValueEqual(CompactingRules.DEFAULT.preferredOf(group), chainOf(Items.GOLD_INGOT), "a preferida padrão");
        CompactingRules iron = CompactingRules.DEFAULT.withPreferred(group, chainOf(Items.IRON_INGOT));
        helper.assertValueEqual(iron.preferredOf(group), chainOf(Items.IRON_INGOT), "a escolha não valeu");
        helper.assertValueEqual(iron.withPreferred(group, chainOf(Items.GOLD_INGOT)).preferred().size(), 1,
                "a escolha anterior ficou no conjunto");
        helper.succeed();
    }

    /** Preferida o ouro: 9 lingotes de ferro com Compactar viram 1 bloco de ouro. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void variantesViramAPreferidaECompactam(GameTestHelper helper) {
        VariantGroup group = linkIronAndGold(helper);
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        putUpgrade(chest, COMPRESS.withPreferred(group, chainOf(Items.GOLD_INGOT)));
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 9, false);
        helper.startSequence()
                .thenWaitUntil(() -> assertForms(helper, chest, Map.of(Items.GOLD_BLOCK, 1L, Items.IRON_INGOT, 0L,
                        Items.IRON_BLOCK, 0L, Items.GOLD_INGOT, 0L)))
                .thenSucceed();
    }

    /** Trocar a preferida (como o painel faz, pelo item do upgrade) leva os itens para a outra variante. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 150)
    public static void trocarAPreferidaLevaOsItens(GameTestHelper helper) {
        VariantGroup group = linkIronAndGold(helper);
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        putUpgrade(chest, COMPRESS.withPreferred(group, chainOf(Items.GOLD_INGOT)));
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 18, false);
        helper.startSequence()
                .thenWaitUntil(() -> assertForms(helper, chest, Map.of(Items.GOLD_BLOCK, 2L, Items.IRON_INGOT, 0L)))
                .thenExecute(() -> putUpgrade(chest, COMPRESS.withPreferred(group, chainOf(Items.IRON_INGOT))))
                .thenWaitUntil(() -> assertForms(helper, chest, Map.of(Items.IRON_BLOCK, 2L, Items.GOLD_BLOCK, 0L,
                        Items.GOLD_INGOT, 0L)))
                .thenSucceed();
    }

    /** O alvo vale pela forma: com alvo no lingote de ferro e o ouro preferido, os blocos de ferro viram lingotes de ouro. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void alvoValePelaFormaNaVariantePreferida(GameTestHelper helper) {
        VariantGroup group = linkIronAndGold(helper);
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        CompactingRules rules = COMPRESS.withTarget(chainOf(Items.IRON_INGOT), Items.IRON_INGOT)
                .withPreferred(group, chainOf(Items.GOLD_INGOT));
        helper.assertTrue(rules.hasOwn(chainOf(Items.GOLD_INGOT)), "o alvo do ferro não vale no ouro");
        putUpgrade(chest, rules);
        chest.storage().insert(new ItemStack(Items.IRON_BLOCK), 2, false);
        helper.startSequence()
                .thenWaitUntil(() -> assertForms(helper, chest, Map.of(Items.GOLD_INGOT, 18L, Items.IRON_BLOCK, 0L,
                        Items.GOLD_BLOCK, 0L)))
                .thenSucceed();
    }

    /** Desligada, sem alvo próprio: nada troca nem compacta. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void desligadaNaoTroca(GameTestHelper helper) {
        VariantGroup group = linkIronAndGold(helper);
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        putUpgrade(chest, CompactingRules.DEFAULT.withPreferred(group, chainOf(Items.GOLD_INGOT)));
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 9, false);
        chest.storage().insert(new ItemStack(Items.GOLD_INGOT), 5, false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> assertForms(helper, chest, Map.of(Items.IRON_INGOT, 9L, Items.GOLD_INGOT, 5L,
                        Items.GOLD_BLOCK, 0L, Items.IRON_BLOCK, 0L)))
                .thenSucceed();
    }

    /**
     * O limite de 100 lingotes de ferro conta o grupo: os 100 viram 11 blocos e 1 lingote de ouro, e o 101º lingote de
     * ferro é recusado.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void limiteDeEstoqueContaOGrupo(GameTestHelper helper) {
        VariantGroup group = linkIronAndGold(helper);
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        chest.setFilter(new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.IRON_INGOT), 100))));
        putUpgrade(chest, COMPRESS.withPreferred(group, chainOf(Items.GOLD_INGOT)));
        ItemStorageHandler handler = chest.handler();
        helper.assertTrue(handler.insertItem(handler.getSlots() - 1, new ItemStack(Items.IRON_INGOT, 64), false).isEmpty(),
                "primeira pilha");
        helper.assertTrue(handler.insertItem(handler.getSlots() - 1, new ItemStack(Items.IRON_INGOT, 36), false).isEmpty(),
                "até o limite");
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        helper.startSequence()
                .thenWaitUntil(() -> assertForms(helper, chest, Map.of(Items.GOLD_BLOCK, 11L, Items.GOLD_INGOT, 1L,
                        Items.IRON_INGOT, 0L)))
                .thenExecute(() -> {
                    helper.assertValueEqual(chest.storage().stockCount(ironIngot, false), 100L, "o grupo vale 100 lingotes");
                    helper.assertValueEqual(chest.storage().insert(ironIngot, 1, true), 0L, "o 101º é recusado");
                })
                .thenSucceed();
    }

    /** Nada some: ferro e ouro misturados em todas as formas valem o mesmo em pepitas antes e depois. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void nadaSomeNaTroca(GameTestHelper helper) {
        VariantGroup group = linkIronAndGold(helper);
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        chest.storage().insert(new ItemStack(Items.IRON_NUGGET), 5, false);
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 20, false);
        chest.storage().insert(new ItemStack(Items.IRON_BLOCK), 2, false);
        chest.storage().insert(new ItemStack(Items.GOLD_INGOT), 3, false);
        chest.storage().insert(new ItemStack(Items.GOLD_NUGGET), 7, false);
        long before = nuggets(chest);
        long totalBefore = chest.storage().total();
        helper.assertValueEqual(before, 381L, "a conta de antes");
        putUpgrade(chest, COMPRESS.withPreferred(group, chainOf(Items.GOLD_INGOT)));
        helper.startSequence()
                .thenWaitUntil(() -> assertForms(helper, chest, Map.of(Items.GOLD_BLOCK, 4L, Items.GOLD_INGOT, 6L,
                        Items.GOLD_NUGGET, 3L, Items.IRON_NUGGET, 0L, Items.IRON_INGOT, 0L, Items.IRON_BLOCK, 0L)))
                .thenExecute(() -> {
                    helper.assertValueEqual(nuggets(chest), before, "o valor em pepitas mudou");
                    helper.assertTrue(chest.storage().total() <= totalBefore, "o total cresceu");
                })
                .thenSucceed();
    }
}
