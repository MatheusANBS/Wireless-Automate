package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorageHandler;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageWatch;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChain;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChains;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A Compactação do Baú: o índice das cadeias montado das receitas de mesa, a conversão no fim do tick, o limite de
 * estoque que conta a cadeia e o descer na hora para a Mesa. Máquinas em y=1 com o roteador em cima, como no
 * {@link StorageGameTests}; cada teste com roteador cria a própria rede.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ChestCompactingGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final CompactingRules COMPRESS = CompactingRules.DEFAULT.withMode(CompactingMode.COMPRESS);

    private static StorageChestBlockEntity chest(GameTestHelper helper, BlockPos pos, RouterTier tier) {
        helper.setBlock(pos, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(pos);
    }

    /** Põe o Upgrade de Compactação com as regras no primeiro slot, como a tela poria. */
    private static void putUpgrade(StorageChestBlockEntity chest, CompactingRules rules) {
        ItemStack stack = new ItemStack(ModItems.COMPACTING_UPGRADE.get());
        stack.set(ModDataComponents.COMPACTING_RULES.get(), rules);
        chest.upgrades().setUpgrade(0, stack);
    }

    private static CompactingChain chainOf(Item item) {
        return CompactingChains.get().chainOf(item);
    }

    private static long count(StorageChestBlockEntity chest, Item item) {
        return chest.storage().count(new ItemStack(item));
    }

    private static void assertCounts(GameTestHelper helper, StorageChestBlockEntity chest, long blocks, long ingots) {
        helper.assertValueEqual(count(chest, Items.IRON_BLOCK), blocks, "blocos de ferro");
        helper.assertValueEqual(count(chest, Items.IRON_INGOT), ingots, "lingotes de ferro");
    }

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    /** Roteador Elite em cima de {@code machine}, com a face de cima de itens no modo dado. */
    private static RouterBlockEntity router(GameTestHelper helper, BlockPos machine, UUID network, PortMode mode) {
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ELITE));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(network);
        router.setNetworkId(ResourceType.ITEM, network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        return router;
    }

    private static ChestBlockEntity vanillaWithIngots(GameTestHelper helper, BlockPos pos, int ingots) {
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(pos);
        int left = ingots;
        for (int slot = 0; left > 0; slot++) {
            int stack = Math.min(64, left);
            chest.setItem(slot, new ItemStack(Items.IRON_INGOT, stack));
            left -= stack;
        }
        return chest;
    }

    private static int vanillaIngots(ChestBlockEntity chest) {
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(Items.IRON_INGOT)) {
                total += chest.getItem(i).getCount();
            }
        }
        return total;
    }

    private static void waitRegistered(GameTestHelper helper, RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void indiceAchaPepitaLingoteBloco(GameTestHelper helper) {
        CompactingChain chain = CompactingChains.get().chainOf(Items.IRON_INGOT);
        helper.assertTrue(chain != null, "o lingote de ferro tem cadeia");
        helper.assertTrue(chain.levelOf(Items.IRON_NUGGET) == 0, "pepita na base");
        helper.assertTrue(chain.levelOf(Items.IRON_INGOT) == 1, "lingote no nível 1");
        helper.assertTrue(chain.levelOf(Items.IRON_BLOCK) == 2, "bloco no nível 2");
        helper.assertTrue(chain.factors()[1] == 9 && chain.factors()[2] == 9, "fatores 9");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void indiceAchaCruEBlocoCru(GameTestHelper helper) {
        CompactingChain chain = CompactingChains.get().chainOf(Items.RAW_IRON);
        helper.assertTrue(chain != null && chain.levelOf(Items.RAW_IRON_BLOCK) == 1, "cru e bloco cru");
        helper.assertTrue(chain.levelOf(Items.IRON_INGOT) < 0, "cru não vira lingote (fundir fica de fora)");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void indiceIgnoraOQueNaoVolta(GameTestHelper helper) {
        helper.assertTrue(CompactingChains.get().chainOf(Items.WHITE_WOOL) == null, "lã não volta");
        helper.assertTrue(CompactingChains.get().chainOf(Items.SANDSTONE) == null, "arenito não volta");
        helper.assertTrue(CompactingChains.get().chainOf(Items.SNOW_BLOCK) == null, "neve não volta");
        helper.succeed();
    }

    /** 100 lingotes pelo inventário comum, com Compactar: 11 blocos e 1 lingote. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void compactaNaEntradaPeloHandler(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        putUpgrade(chest, COMPRESS);
        ItemStorageHandler handler = chest.handler();
        helper.assertTrue(handler.insertItem(handler.getSlots() - 1, new ItemStack(Items.IRON_INGOT, 64), false).isEmpty(),
                "primeira pilha");
        helper.assertTrue(handler.insertItem(handler.getSlots() - 1, new ItemStack(Items.IRON_INGOT, 36), false).isEmpty(),
                "o resto");
        helper.startSequence()
                .thenWaitUntil(() -> assertCounts(helper, chest, 11, 1))
                .thenSucceed();
    }

    /** Um roteador traz lingotes de um baú vanilla e o Baú os guarda como blocos. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void compactaNaEntradaPeloRoteador(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-compactacao-roteador");
        ChestBlockEntity source = vanillaWithIngots(helper, A, 90);
        StorageChestBlockEntity chest = chest(helper, B, RouterTier.ELITE);
        putUpgrade(chest, COMPRESS);
        RouterBlockEntity from = router(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity to = router(helper, B, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, from, to))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(vanillaIngots(source), 0, "a origem esvaziou");
                    assertCounts(helper, chest, 10, 0);
                })
                .thenSucceed();
    }

    /** O alvo da cadeia (lingote) vence o modo global (Compactar): 2 blocos descem para 18 lingotes. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void alvoEspecificoVenceOGlobal(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        putUpgrade(chest, COMPRESS.withTarget(chainOf(Items.IRON_INGOT), Items.IRON_INGOT));
        chest.storage().insert(new ItemStack(Items.IRON_BLOCK), 2, false);
        helper.startSequence()
                .thenWaitUntil(() -> assertCounts(helper, chest, 0, 18))
                .thenSucceed();
    }

    /**
     * Descompactar para na capacidade: com 26 de espaço, cada bloco que desce gasta 8, então descem 3 dos 4 e o
     * quarto fica bloco.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void descompactarParaNaCapacidade(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.BASIC);
        long cap = chest.capacity();
        helper.assertTrue(cap > 30, "o Baú Básico tem capacidade");
        chest.storage().insert(new ItemStack(Items.COBBLESTONE), cap - 30, false);
        chest.storage().insert(new ItemStack(Items.IRON_BLOCK), 4, false);
        putUpgrade(chest, CompactingRules.DEFAULT.withTarget(chainOf(Items.IRON_INGOT), Items.IRON_INGOT));
        helper.startSequence()
                .thenWaitUntil(() -> assertCounts(helper, chest, 1, 27))
                .thenExecute(() -> helper.assertTrue(chest.storage().total() <= cap, "dentro da capacidade"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void semUpgradeNaoMexe(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 100, false);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> assertCounts(helper, chest, 0, 100))
                .thenSucceed();
    }

    /** O upgrade recém-feito (regras padrão, modo Desligada) não muda nada. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void upgradeNovoNaoMexe(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        chest.upgrades().setUpgrade(0, new ItemStack(ModItems.COMPACTING_UPGRADE.get()));
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 100, false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(chest.compacting().rules() != null, "as regras padrão aplicadas");
                    assertCounts(helper, chest, 0, 100);
                })
                .thenSucceed();
    }

    /** O limite de 100 lingotes do filtro do Baú conta os blocos: 11 blocos e 1 lingote já são 100. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void limiteDoFiltroContaACadeia(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        chest.setFilter(new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.IRON_INGOT), 100))));
        putUpgrade(chest, COMPRESS);
        ItemStorageHandler handler = chest.handler();
        helper.assertTrue(handler.insertItem(handler.getSlots() - 1, new ItemStack(Items.IRON_INGOT, 64), false).isEmpty(),
                "primeira pilha");
        helper.assertTrue(handler.insertItem(handler.getSlots() - 1, new ItemStack(Items.IRON_INGOT, 36), false).isEmpty(),
                "até o limite");
        helper.startSequence()
                .thenWaitUntil(() -> assertCounts(helper, chest, 11, 1))
                .thenExecute(() -> {
                    helper.assertValueEqual(chest.storage().stockCount(new ItemStack(Items.IRON_INGOT), false), 100L,
                            "a cadeia vale 100 lingotes");
                    helper.assertValueEqual(chest.storage().insert(new ItemStack(Items.IRON_INGOT), 1, true), 0L,
                            "o 101º é recusado");
                    helper.assertValueEqual(handler.insertItem(handler.getSlots() - 1, new ItemStack(Items.IRON_INGOT), true)
                            .getCount(), 1, "o funil também é recusado");
                })
                .thenSucceed();
    }

    /** O estoque de 100 lingotes do roteador no Baú com Compactar para em 100, contando os blocos. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void limiteDoRoteadorContaACadeia(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-compactacao-estoque");
        ChestBlockEntity source = vanillaWithIngots(helper, A, 200);
        StorageChestBlockEntity chest = chest(helper, B, RouterTier.ELITE);
        putUpgrade(chest, COMPRESS);
        RouterBlockEntity from = router(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity to = router(helper, B, network, PortMode.INSERT);
        to.setFilter(ResourceType.ITEM, Direction.UP, new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.IRON_INGOT), 100))));
        ItemStack ingot = new ItemStack(Items.IRON_INGOT);
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, from, to))
                .thenWaitUntil(() -> helper.assertValueEqual(chest.storage().stockCount(ingot, false), 100L, "estoque no Baú"))
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertValueEqual(chest.storage().stockCount(ingot, false), 100L, "parou no estoque");
                    helper.assertValueEqual(vanillaIngots(source), 100, "o resto na origem");
                    assertCounts(helper, chest, 11, 1);
                })
                .thenSucceed();
    }

    /** A Mesa pede 3 lingotes e só há blocos: 1 bloco desce por dentro e a sobra fica, sem voltar a compactar. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void descerNaHoraParaAMesa(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        putUpgrade(chest, COMPRESS);
        chest.storage().insert(new ItemStack(Items.IRON_BLOCK), 2, false);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(chest.compacting().unpackFor(new ItemStack(Items.IRON_INGOT), 3), "desceu");
                    assertCounts(helper, chest, 1, 9);
                })
                .thenIdle(3)
                .thenExecute(() -> assertCounts(helper, chest, 1, 9))
                .thenSucceed();
    }

    /** Lingotes com nome não são da cadeia: ficam como estão. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void itemComComponenteNaoCompacta(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        putUpgrade(chest, COMPRESS);
        ItemStack named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("lingote com nome"));
        chest.storage().insert(named, 9, false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertValueEqual(chest.storage().count(named), 9L, "os lingotes com nome");
                    helper.assertValueEqual(count(chest, Items.IRON_BLOCK), 0L, "nenhum bloco");
                })
                .thenSucceed();
    }

    /**
     * A conversão é movimento interno: o filtro do Baú limita o bloco a 1 (e recusa o segundo de fora), mas 9
     * lingotes ainda viram o segundo bloco.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void conversaoIgnoraOFiltro(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        chest.setFilter(new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.IRON_INGOT), 0),
                        new FilterEntry.ItemEntry(new ItemStack(Items.IRON_BLOCK), 1))));
        putUpgrade(chest, COMPRESS);
        helper.assertValueEqual(chest.storage().insert(new ItemStack(Items.IRON_BLOCK), 1, false), 1L, "o primeiro bloco");
        helper.assertValueEqual(chest.storage().insert(new ItemStack(Items.IRON_BLOCK), 1, true), 0L, "o filtro recusa outro bloco");
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 9, false);
        helper.startSequence()
                .thenWaitUntil(() -> assertCounts(helper, chest, 2, 0))
                .thenSucceed();
    }

    /** O vigia da Tela de Sinal vê só a entrada dos 9 lingotes, não a conversão. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void conversaoNaoContaNoVigia(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        StorageWatch watch = chest.startWatch();
        watch.watchItem(Items.IRON_BLOCK);
        watch.watchItem(Items.IRON_INGOT);
        putUpgrade(chest, COMPRESS);
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 9, false);
        helper.startSequence()
                .thenWaitUntil(() -> assertCounts(helper, chest, 1, 0))
                .thenExecute(() -> {
                    helper.assertValueEqual(watch.totalIn(), 9L, "entraram 9");
                    helper.assertValueEqual(watch.totalOut(), 0L, "nada saiu");
                    helper.assertValueEqual(watch.itemIn(Items.IRON_INGOT), 9L, "os 9 lingotes");
                    helper.assertValueEqual(watch.itemIn(Items.IRON_BLOCK), 0L, "o bloco da conversão não conta");
                })
                .thenSucceed();
    }

    /**
     * O índice remontado no meio do tick (um {@code /reload}) troca a geração: a cadeia suja do ferro é convertida
     * pela geração nova, e o ouro, com alvo no lingote, fica como está.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void recargaDoIndiceNaoConverteCadeiaErrada(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        chest.storage().insert(new ItemStack(Items.GOLD_INGOT), 9, false);
        putUpgrade(chest, COMPRESS.withTarget(chainOf(Items.GOLD_INGOT), Items.GOLD_INGOT));
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    int before = CompactingChains.get().generation();
                    chest.storage().insert(new ItemStack(Items.IRON_INGOT), 9, false);
                    CompactingChains.rebuild(helper.getLevel().getServer());
                    helper.assertTrue(CompactingChains.get().generation() != before, "geração nova");
                })
                .thenWaitUntil(() -> assertCounts(helper, chest, 1, 0))
                .thenExecute(() -> {
                    helper.assertValueEqual(count(chest, Items.GOLD_INGOT), 9L, "o ouro intacto");
                    helper.assertValueEqual(count(chest, Items.GOLD_BLOCK), 0L, "nenhum bloco de ouro");
                })
                .thenSucceed();
    }
}
