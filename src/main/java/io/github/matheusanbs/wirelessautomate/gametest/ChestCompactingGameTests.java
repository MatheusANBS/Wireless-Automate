package io.github.matheusanbs.wirelessautomate.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.menu.ChestUpgradeSlots;
import io.github.matheusanbs.wirelessautomate.menu.CompactingPanel;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.CompactingRulePayload;
import io.github.matheusanbs.wirelessautomate.packet.CompactingStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorageHandler;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageWatch;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChainGraph;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChain;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChains;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingRules;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
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

    static StorageChestBlockEntity chest(GameTestHelper helper, BlockPos pos, RouterTier tier) {
        helper.setBlock(pos, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(pos);
    }

    /** Põe o Upgrade de Compactação com as regras no primeiro slot, como a tela poria. */
    static void putUpgrade(StorageChestBlockEntity chest, CompactingRules rules) {
        ItemStack stack = new ItemStack(ModItems.COMPACTING_UPGRADE.get());
        stack.set(ModDataComponents.COMPACTING_RULES.get(), rules);
        chest.upgrades().setUpgrade(0, stack);
    }

    private static CompactingChain chainOf(Item item) {
        return CompactingChains.get().chainOf(item);
    }

    static long count(StorageChestBlockEntity chest, Item item) {
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
     * O índice remontado no meio do tick (um {@code /reload}) troca a geração, e a cadeia que ficou suja antes dela (o
     * ferro que acabou de entrar) ainda é convertida pela geração nova. O ouro, com alvo próprio no lingote, fica como
     * está. Não prova a troca de ids entre gerações: o índice vanilla remonta com os mesmos ids (o id velho
     * descartado é o {@link #markChainDescartaIdDeGeracaoVelha}).
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void recargaDoIndiceConverteAsSujasNaGeracaoNova(GameTestHelper helper) {
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

    // ------------------------------------------------------------------ o índice protegido

    /**
     * A receita de outro mod que lança não derruba o índice: o par cujo conferidor lança (exceção comum ou
     * {@link LinkageError}) fica de fora, e os outros pares formam as cadeias. O índice do servidor não é trocado.
     */
    @GameTest(template = "empty")
    public static void parQueLancaEPuladoEORestoMonta(GameTestHelper helper) {
        CompactingChains current = CompactingChains.get();
        ChainGraph<Item> graph = CompactingChains.newGraph();
        List<CompactingChains.Pair> pairs = List.of(
                new CompactingChains.Pair(Items.IRON_NUGGET, Items.IRON_INGOT, 9),
                new CompactingChains.Pair(Items.GOLD_NUGGET, Items.GOLD_INGOT, 9),
                new CompactingChains.Pair(Items.IRON_INGOT, Items.IRON_BLOCK, 9),
                new CompactingChains.Pair(Items.COPPER_INGOT, Items.COPPER_BLOCK, 9));
        int skipped = CompactingChains.linkPairs(pairs, pair -> {
            if (pair.small() == Items.GOLD_NUGGET) {
                throw new IllegalStateException("receita quebrada do teste");
            }
            if (pair.small() == Items.COPPER_INGOT) {
                throw new NoClassDefFoundError("classe sumida do teste");
            }
            return true;
        }, graph);
        helper.assertValueEqual(skipped, 2, "pares pulados");
        CompactingChains built = CompactingChains.assemble(graph);
        helper.assertTrue(built.chainOf(Items.GOLD_NUGGET) == null, "o par que lançou entrou");
        helper.assertTrue(built.chainOf(Items.COPPER_BLOCK) == null, "o par do LinkageError entrou");
        CompactingChain iron = built.chainOf(Items.IRON_INGOT);
        helper.assertTrue(iron != null && iron.forms().length == 3, "a cadeia do ferro não montou inteira");
        helper.assertTrue(CompactingChains.get() == current, "o índice do servidor foi trocado");
        helper.succeed();
    }

    /**
     * Uma cadeia com mais formas que o teto fica fora do índice (e, por isso, do estado do painel); a do teto
     * exato entra, e o estado com ela vai e volta pelo codec.
     */
    @GameTest(template = "empty")
    public static void cadeiaAcimaDoTetoDeFormasFicaDeFora(GameTestHelper helper) {
        List<Item> items = BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR && CompactingChains.get().chainOf(item) == null)
                .limit(CompactingChain.MAX_FORMS * 2L + 1).toList();
        List<Item> longer = items.subList(0, CompactingChain.MAX_FORMS + 1);
        List<Item> exact = items.subList(CompactingChain.MAX_FORMS + 1, items.size());
        ChainGraph<Item> graph = CompactingChains.newGraph();
        for (List<Item> chain : List.of(longer, exact)) {
            for (int i = 0; i + 1 < chain.size(); i++) {
                graph.link(chain.get(i), chain.get(i + 1), 9);
            }
        }
        CompactingChains built = CompactingChains.assemble(graph);
        helper.assertTrue(built.chainOf(longer.get(0)) == null, "a cadeia acima do teto entrou");
        CompactingChain kept = built.chainOf(exact.get(0));
        helper.assertTrue(kept != null && kept.forms().length == CompactingChain.MAX_FORMS,
                "a cadeia do teto ficou de fora");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        CompactingStatePayload state = new CompactingStatePayload(5, CompactingMode.COMPRESS,
                List.of(new CompactingStatePayload.Row(List.of(kept.forms()), 0, false)));
        CompactingStatePayload.STREAM_CODEC.encode(buf, state);
        helper.assertValueEqual(CompactingStatePayload.STREAM_CODEC.decode(buf), state, "o estado depois do codec");
        helper.succeed();
    }

    // ------------------------------------------------------------------ o estado e as regras na rede

    private static ServerPlayer playerAt(GameTestHelper helper, StorageChestBlockEntity chest) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(chest.getBlockPos().above()));
        player.getInventory().clearContent();
        return player;
    }

    private static StorageListMenu<ItemStack> open(ServerPlayer player, StorageChestBlockEntity chest, int containerId) {
        StorageListMenu<ItemStack> menu = new StorageListMenu<>(containerId, player.getInventory(), chest);
        player.containerMenu = menu;
        return menu;
    }

    private static CompactingRules rulesOf(StorageChestBlockEntity chest) {
        return chest.upgrades().upgrade(0).getOrDefault(ModDataComponents.COMPACTING_RULES.get(), CompactingRules.DEFAULT);
    }

    /** O alvo e o modo mandados pelo cliente chegam ao Baú e convertem o conteúdo. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void regraPeloPayloadMudaOAlvo(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        putUpgrade(chest, CompactingRules.DEFAULT);
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 9, false);
        chest.storage().insert(new ItemStack(Items.GOLD_INGOT), 9, false);
        ServerPlayer player = playerAt(helper, chest);
        open(player, chest, 77);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    assertCounts(helper, chest, 0, 9);
                    helper.assertTrue(CompactingPanel.handle(player, CompactingRulePayload.target(77,
                            CompactingRulePayload.Kind.TARGET, Items.IRON_BLOCK)), "alvo aceito");
                    helper.assertTrue(rulesOf(chest).hasOwn(chainOf(Items.IRON_INGOT)), "a regra própria no slot");
                })
                .thenWaitUntil(() -> assertCounts(helper, chest, 1, 0))
                .thenExecute(() -> {
                    helper.assertValueEqual(count(chest, Items.GOLD_INGOT), 9L, "o ouro intacto");
                    helper.assertTrue(CompactingPanel.handle(player, CompactingRulePayload.mode(77,
                            CompactingMode.COMPRESS.ordinal())), "modo aceito");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(count(chest, Items.GOLD_BLOCK), 1L, "o ouro compactado"))
                .thenExecute(() -> helper.assertTrue(CompactingPanel.handle(player, CompactingRulePayload.target(77,
                        CompactingRulePayload.Kind.CLEAR, Items.IRON_NUGGET)), "limpar aceito por qualquer forma"))
                .thenExecute(() -> helper.assertTrue(!rulesOf(chest).hasOwn(chainOf(Items.IRON_INGOT)), "sem alvo próprio"))
                .thenSucceed();
    }

    /** Pedido fora das regras é recusado e não muda nada. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void payloadInvalidoEIgnorado(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        StorageChestBlockEntity other = chest(helper, new BlockPos(6, 1, 6), RouterTier.ELITE);
        putUpgrade(chest, COMPRESS);
        CompactingRules before = rulesOf(chest);
        ServerPlayer player = playerAt(helper, chest);
        open(player, chest, 78);
        helper.assertTrue(!CompactingPanel.handle(player, CompactingRulePayload.target(78,
                CompactingRulePayload.Kind.TARGET, Items.STONE)), "item fora de qualquer cadeia");
        helper.assertTrue(!CompactingPanel.handle(player, CompactingRulePayload.target(78,
                CompactingRulePayload.Kind.CLEAR, Items.STONE)), "limpar fora de qualquer cadeia");
        helper.assertTrue(!CompactingPanel.handle(player, CompactingRulePayload.of(78,
                CompactingRulePayload.Kind.TARGET)), "alvo sem item");
        helper.assertTrue(!CompactingPanel.handle(player, CompactingRulePayload.mode(78, 99)), "modo acima do enum");
        helper.assertTrue(!CompactingPanel.handle(player, CompactingRulePayload.mode(78, -1)), "modo negativo");
        helper.assertTrue(!CompactingPanel.handle(player, CompactingRulePayload.target(79,
                CompactingRulePayload.Kind.TARGET, Items.IRON_BLOCK)), "outro containerId");
        helper.assertTrue(!CompactingPanel.handle(null, CompactingRulePayload.of(78, CompactingRulePayload.Kind.WATCH)),
                "sem jogador");
        helper.assertValueEqual(rulesOf(chest), before, "as regras de antes");
        // O menu de outro Baú, sem o upgrade.
        ServerPlayer visitor = playerAt(helper, other);
        open(visitor, other, 80);
        helper.assertTrue(!CompactingPanel.handle(visitor, CompactingRulePayload.target(80,
                CompactingRulePayload.Kind.TARGET, Items.IRON_BLOCK)), "Baú sem o upgrade");
        helper.assertValueEqual(rulesOf(chest), before, "o primeiro Baú intacto");
        // Fora de alcance.
        player.moveTo(Vec3.atCenterOf(chest.getBlockPos().above(200)));
        helper.assertTrue(!CompactingPanel.handle(player, CompactingRulePayload.target(78,
                CompactingRulePayload.Kind.TARGET, Items.IRON_BLOCK)), "fora de alcance");
        helper.assertValueEqual(rulesOf(chest), before, "as regras depois do alcance");
        helper.succeed();
    }

    /**
     * Os alvos próprios param no teto do codec: com 255, o 256º entra e o 257º é recusado sem mudar nada; trocar o
     * alvo de uma cadeia que já tem um segue valendo, e o item do upgrade continua codificável. A leitura de dados
     * antigos também para no teto.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void alvoAlemDoTetoERecusado(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        Set<ResourceLocation> filler = new HashSet<>();
        for (int i = 0; i < CompactingRules.MAX_TARGETS - 1; i++) {
            filler.add(ResourceLocation.fromNamespaceAndPath("modsumido", "alvo_" + i));
        }
        putUpgrade(chest, new CompactingRules(CompactingMode.OFF, Set.copyOf(filler)));
        ServerPlayer player = playerAt(helper, chest);
        open(player, chest, 83);
        helper.assertTrue(CompactingPanel.handle(player, CompactingRulePayload.target(83,
                CompactingRulePayload.Kind.TARGET, Items.IRON_BLOCK)), "o 256º alvo recusado");
        CompactingRules full = rulesOf(chest);
        helper.assertValueEqual(full.targets().size(), CompactingRules.MAX_TARGETS, "alvos no teto");
        helper.assertTrue(!CompactingPanel.handle(player, CompactingRulePayload.target(83,
                CompactingRulePayload.Kind.TARGET, Items.GOLD_BLOCK)), "o 257º alvo aceito");
        helper.assertValueEqual(rulesOf(chest), full, "as regras mudaram com a recusa");
        helper.assertTrue(CompactingPanel.handle(player, CompactingRulePayload.target(83,
                CompactingRulePayload.Kind.TARGET, Items.IRON_INGOT)), "trocar o alvo da mesma cadeia no teto");

        ItemStack upgrade = chest.upgrades().upgrade(0);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ItemStack.STREAM_CODEC.encode(buf, upgrade);
        helper.assertTrue(ItemStack.matches(ItemStack.STREAM_CODEC.decode(buf), upgrade), "o item não voltou do codec");

        JsonArray targets = new JsonArray();
        for (int i = 0; i < CompactingRules.MAX_TARGETS + 44; i++) {
            targets.add("modsumido:antigo_" + i);
        }
        JsonObject saved = new JsonObject();
        saved.add("targets", targets);
        CompactingRules capped = CompactingRules.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        helper.assertValueEqual(capped.targets().size(), CompactingRules.MAX_TARGETS, "a leitura passou do teto");
        helper.succeed();
    }

    /** Sem o WATCH nada é montado; com ele, um estado por mudança e nenhum sem mudança. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void semPainelNaoMandaEstado(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        putUpgrade(chest, CompactingRules.DEFAULT);
        ServerPlayer player = playerAt(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 81);
        // Tudo no mesmo instante: o contador é de todos os testes, e nenhum outro mexe nele no meio.
        int base = ChestUpgradeSlots.sentStates();
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 9, false);
        menu.broadcastChanges();
        chest.storage().insert(new ItemStack(Items.GOLD_INGOT), 9, false);
        menu.broadcastChanges();
        helper.assertValueEqual(ChestUpgradeSlots.sentStates(), base, "sem WATCH nada é montado");
        helper.assertTrue(CompactingPanel.handle(player, CompactingRulePayload.of(81, CompactingRulePayload.Kind.WATCH)),
                "WATCH aceito");
        menu.broadcastChanges();
        helper.assertValueEqual(ChestUpgradeSlots.sentStates(), base + 1, "o primeiro estado");
        menu.broadcastChanges();
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 5, false);
        menu.broadcastChanges();
        helper.assertValueEqual(ChestUpgradeSlots.sentStates(), base + 1, "mais do que já havia não muda o estado");
        chest.storage().insert(new ItemStack(Items.COPPER_INGOT), 1, false);
        menu.broadcastChanges();
        helper.assertValueEqual(ChestUpgradeSlots.sentStates(), base + 2, "uma cadeia nova no Baú");
        CompactingPanel.handle(player, CompactingRulePayload.of(81, CompactingRulePayload.Kind.UNWATCH));
        chest.storage().insert(new ItemStack(Items.DIAMOND), 9, false);
        chest.storage().insert(new ItemStack(Items.EMERALD), 9, false);
        menu.broadcastChanges();
        helper.assertValueEqual(ChestUpgradeSlots.sentStates(), base + 2, "fechado, nada mais");
        helper.succeed();
    }

    /** Os dois payloads vão e voltam pelo codec, com a parada (-1). */
    @GameTest(template = "empty")
    public static void payloadsIdaEVoltaPeloCodec(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        CompactingStatePayload state = new CompactingStatePayload(5, CompactingMode.COMPRESS, List.of(
                new CompactingStatePayload.Row(List.of(Items.IRON_NUGGET, Items.IRON_INGOT, Items.IRON_BLOCK), 2, true),
                new CompactingStatePayload.Row(List.of(Items.GOLD_INGOT, Items.GOLD_BLOCK), -1, false)));
        CompactingStatePayload.STREAM_CODEC.encode(buf, state);
        helper.assertValueEqual(CompactingStatePayload.STREAM_CODEC.decode(buf), state, "estado depois do codec");
        helper.assertValueEqual(buf.readableBytes(), 0, "bytes sobrando do estado");
        for (CompactingRulePayload rule : List.of(
                CompactingRulePayload.of(5, CompactingRulePayload.Kind.WATCH),
                CompactingRulePayload.mode(5, 2),
                CompactingRulePayload.target(5, CompactingRulePayload.Kind.TARGET, Items.IRON_BLOCK))) {
            CompactingRulePayload.STREAM_CODEC.encode(buf, rule);
            helper.assertValueEqual(CompactingRulePayload.STREAM_CODEC.decode(buf), rule, "regra depois do codec");
        }
        helper.assertValueEqual(buf.readableBytes(), 0, "bytes sobrando das regras");
        helper.succeed();
    }

    /**
     * Com as regras e o conteúdo parados e o índice remontado, só quem olha o painel (a versão) percebe a troca de
     * geração: o Baú vai sozinho para a fila do fim do tick e converte, sem esperar outra mudança de conteúdo.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void recargaPercebidaPeloPainelConverteSozinha(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        putUpgrade(chest, CompactingRules.DEFAULT);
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 9, false);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    assertCounts(helper, chest, 0, 9);
                    // As regras entram sem marcar nada, como depois de carregar o Baú do disco.
                    chest.compacting().setRules(COMPRESS);
                    CompactingChains.rebuild(helper.getLevel().getServer());
                    chest.compacting().version();
                })
                .thenWaitUntil(() -> assertCounts(helper, chest, 1, 0))
                .thenSucceed();
    }

    /** Um id de cadeia lido antes do reload é descartado; as cadeias presentes são remarcadas pela geração nova. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void markChainDescartaIdDeGeracaoVelha(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A, RouterTier.ELITE);
        putUpgrade(chest, CompactingRules.DEFAULT);
        chest.storage().insert(new ItemStack(Items.IRON_INGOT), 9, false);
        chest.storage().insert(new ItemStack(Items.GOLD_INGOT), 9, false);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    int staleId = chainOf(Items.GOLD_INGOT).id();
                    chest.compacting().setRules(COMPRESS.withTarget(chainOf(Items.GOLD_INGOT), Items.GOLD_INGOT));
                    CompactingChains.rebuild(helper.getLevel().getServer());
                    chest.compacting().markChain(staleId);
                    chest.compacting().markChain(100_000);
                })
                .thenWaitUntil(() -> assertCounts(helper, chest, 1, 0))
                .thenExecute(() -> {
                    helper.assertValueEqual(count(chest, Items.GOLD_INGOT), 9L, "o ouro com alvo próprio fica");
                    helper.assertValueEqual(count(chest, Items.GOLD_BLOCK), 0L, "nenhum bloco de ouro");
                })
                .thenSucceed();
    }
}
