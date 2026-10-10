package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Source do Ars Nouveau: Source Jars de verdade, um roteador em cima de cada (facing=UP, face
 * configurada {@link net.minecraft.core.Direction#UP}). Rodam só na run {@code runGameTestServerSource},
 * que tem o Ars na pasta mods e liga o namespace {@value #NAMESPACE} (o template é
 * {@code data/wirelessautomate_source/structures/empty.nbt}). Sem o Ars, só passam.
 *
 * <p>Porte 1.20.1: os Relays do Ars 4.12 só ligam em {@code AbstractSourceMachine}; os mixins de
 * {@code compat/arsnouveau/mixin} entregam a eles a {@code RelayView} do Tanque de Source, e os testes de Relay
 * abaixo provam o caminho inteiro (varinha, ciclos do Relay e do Splitter). Origem e destino seguem o main:
 * Imbuement Chamber não é drenada, Sourcelink não recebe, Creative Jar é ralo e fonte.
 *
 * <p>Nenhum tipo do Ars nas assinaturas: o Forge inspeciona esta classe por reflexão mesmo sem o
 * Ars. O que usa a API fica em {@code SourceTestSupport}.
 */
@GameTestHolder(SourceGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class SourceGameTests {
    static final String NAMESPACE = "wirelessautomate_source";
    static final ResourceLocation JAR = new ResourceLocation("ars_nouveau", "source_jar");

    /** A run de Source está ligada e o Ars está presente. */
    static boolean enabled() {
        return Boolean.getBoolean("wirelessautomate.sourceTests") && ModList.get().isLoaded("ars_nouveau");
    }

    /** A run de Source carrega o Ars, e a Source Jar existe no registro e guarda Source ({@code ISourceTile} no 1.20.1). */
    @GameTest(template = "empty")
    public static void arsNouveauIsLoaded(GameTestHelper helper) {
        if (!Boolean.getBoolean("wirelessautomate.sourceTests")) {
            helper.succeed();
            return;
        }
        helper.assertTrue(ModList.get().isLoaded("ars_nouveau"), "Ars Nouveau não carregou na run de Source");
        helper.assertTrue(BuiltInRegistries.BLOCK.get(JAR) != Blocks.AIR, "sem a Source Jar no registro");
        helper.setBlock(A, BuiltInRegistries.BLOCK.get(JAR));
        helper.assertTrue(SourceTestSupport.hasSource(helper.getLevel(), helper.absolutePos(A)),
                "Source Jar sem ISourceTile");
        helper.succeed();
    }

    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 0);
    private static final BlockPos C = new BlockPos(0, 1, 2);

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    /** Source Jar em {@code pos}, com um roteador do tier em cima, na rede, no modo dado. */
    private static RouterBlockEntity jar(GameTestHelper helper, BlockPos pos, RouterTier tier, UUID network,
            PortMode mode) {
        helper.setBlock(pos, BuiltInRegistries.BLOCK.get(JAR));
        BlockPos routerPos = pos.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, tier));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, routerPos);
        router.setNetworkId(network);
        router.setMode(ResourceType.SOURCE, Direction.UP, mode);
        return router;
    }

    private static int amount(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(SourceTestSupport.hasSource(helper.getLevel(), helper.absolutePos(pos)),
                "Source Jar sem capability em " + pos.toShortString());
        return SourceTestSupport.amount(helper.getLevel(), helper.absolutePos(pos));
    }

    private static void set(GameTestHelper helper, BlockPos pos, int amount) {
        SourceTestSupport.set(helper.getLevel(), helper.absolutePos(pos), amount);
    }

    private static boolean registered(RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            if (!NetworkManager.get().contains(router)) {
                return false;
            }
        }
        return true;
    }

    /** Uma jarra para outra pela rede, sem limite de vazão (Ultimate). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sourceMovesBetweenJars(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source");
        RouterBlockEntity from = jar(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity to = jar(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, to), "roteadores não registrados"))
                .thenExecute(() -> set(helper, A, 5_000))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, B), 5_000, "Source no destino");
                    GameTestCompat.assertValueEqual(helper, amount(helper, A), 0, "Source na origem");
                })
                .thenSucceed();
    }

    /** Prioridade maior enche primeiro; a outra jarra não recebe nada enquanto a primeira aceita. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void higherPriorityJarFillsFirst(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-prioridade");
        RouterBlockEntity from = jar(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity high = jar(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        RouterBlockEntity low = jar(helper, C, RouterTier.ULTIMATE, network, PortMode.INSERT);
        high.setPriority(ResourceType.SOURCE, Direction.UP, 5);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, high, low), "roteadores não registrados"))
                .thenExecute(() -> set(helper, A, 3_000))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, amount(helper, B), 3_000, "Source na prioridade 5"))
                .thenIdle(10)
                .thenExecute(() -> GameTestCompat.assertValueEqual(helper, amount(helper, C), 0, "Source na prioridade 0"))
                .thenSucceed();
    }

    /** Mesma prioridade: a Source se divide por igual entre as jarras. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void equalPrioritySplitsEvenly(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-divisao");
        RouterBlockEntity from = jar(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity first = jar(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        RouterBlockEntity second = jar(helper, C, RouterTier.ULTIMATE, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, first, second), "roteadores não registrados"))
                .thenExecute(() -> set(helper, A, 4_000))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, amount(helper, A), 0, "Source na origem"))
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, B), 2_000, "primeira jarra");
                    GameTestCompat.assertValueEqual(helper, amount(helper, C), 2_000, "segunda jarra");
                })
                .thenSucceed();
    }

    /**
     * Destino cheio: nada sai da origem; esvaziado, o destino acorda e recebe. O Ars não avisa mudança de
     * conteúdo no {@code setSource}, então o destino acorda pelo teto do sono, não por um evento.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fullJarSleepsAndWakes(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-cheia");
        RouterBlockEntity from = jar(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity to = jar(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, to), "roteadores não registrados"))
                .thenExecute(() -> {
                    set(helper, B, SourceTestSupport.capacity(helper.getLevel(), helper.absolutePos(B)));
                    set(helper, A, 1_000);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, A), 1_000, "saiu Source para uma jarra cheia");
                    set(helper, B, 0);
                })
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, B), 1_000, "Source depois de esvaziar o destino");
                    GameTestCompat.assertValueEqual(helper, amount(helper, A), 0, "Source na origem");
                })
                .thenSucceed();
    }

    /** Básico: 1.000 Source/s (o balde começa cheio com um segundo); nada se perde no caminho. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void basicTierLimitsSourceRate(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-vazao");
        RouterBlockEntity from = jar(helper, A, RouterTier.BASIC, network, PortMode.EXTRACT);
        RouterBlockEntity to = jar(helper, B, RouterTier.BASIC, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, to), "roteadores não registrados"))
                .thenExecute(() -> set(helper, A, 10_000))
                .thenWaitUntil(() -> helper.assertTrue(amount(helper, B) > 0, "nada chegou"))
                .thenIdle(20)
                .thenExecute(() -> {
                    int moved = amount(helper, B);
                    helper.assertTrue(moved >= 100, "o limitador travou, passou só " + moved);
                    // Um segundo de balde (100) + 20 ticks a 5 por tick (100), com folga de um tick.
                    helper.assertTrue(moved <= 205, "passou do limite do tier: " + moved);
                    GameTestCompat.assertValueEqual(helper, amount(helper, A) + moved, 10_000, "Source perdida ou criada");
                })
                .thenSucceed();
    }

    // ---- Tanque de Source ----

    /** Tanque de Source do tier em {@code pos}, sem roteador. */
    private static StorageSourceTankBlockEntity placeTank(GameTestHelper helper, BlockPos pos, RouterTier tier) {
        helper.setBlock(pos, ModBlocks.STORAGE.get(StorageKind.SOURCE_TANK).get().defaultBlockState()
                .setValue(RouterBlock.TIER, tier));
        return tank(helper, pos);
    }

    private static StorageSourceTankBlockEntity tank(GameTestHelper helper, BlockPos pos) {
        return GameTestCompat.getBlockEntity(helper, pos);
    }

    /** Tanque de Source do tier em {@code pos}, com um roteador Ultimate em cima, na rede, no modo dado. */
    private static RouterBlockEntity sourceTank(GameTestHelper helper, BlockPos pos, RouterTier tier, UUID network,
            PortMode mode) {
        placeTank(helper, pos, tier);
        BlockPos routerPos = pos.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, routerPos);
        router.setNetworkId(network);
        router.setMode(ResourceType.SOURCE, Direction.UP, mode);
        return router;
    }

    /** Uma posição a 2 blocos do tanque em {@code A} (dentro do raio 5 das máquinas do Ars), absoluta. */
    private static BlockPos center(GameTestHelper helper) {
        return helper.absolutePos(A.offset(2, 0, 0));
    }

    private static boolean providerAt(GameTestHelper helper, BlockPos pos) {
        return SourceTestSupport.providerAt(helper.getLevel(), helper.absolutePos(pos));
    }

    /** Da jarra para o tanque pela rede. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void routerFillsTankFromJar(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-tanque");
        RouterBlockEntity from = jar(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity to = sourceTank(helper, B, RouterTier.BASIC, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, to), "roteadores não registrados"))
                .thenExecute(() -> set(helper, A, 5_000))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, tank(helper, B).store().stored(), 5_000L, "Source no tanque");
                    GameTestCompat.assertValueEqual(helper, amount(helper, A), 0, "Source na jarra");
                })
                .thenSucceed();
    }

    /**
     * Entre dois tanques, bilhões numa visita: mais que {@link Integer#MAX_VALUE}, o que só o caminho
     * bulk passa (a capability do Ars corta no {@code int}).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void twoTanksMoveBillionsInOneVisit(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-bilhoes");
        RouterBlockEntity from = sourceTank(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity to = sourceTank(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        tank(helper, A).store().insert(3_000_000_000L, false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, to), "roteadores não registrados"))
                .thenExecute(() -> {
                    helper.assertTrue(from.bulkSource(Direction.UP) != null, "a origem não vê a BulkSource");
                    helper.assertTrue(to.bulkSource(Direction.UP) != null, "o destino não vê a BulkSource");
                })
                // numa visita só: sem o caminho em bloco, a primeira leitura não vazia seria Integer.MAX_VALUE
                .thenWaitUntil(() -> helper.assertTrue(tank(helper, B).store().stored() > 0, "nada chegou ao destino"))
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, tank(helper, B).store().stored(), 3_000_000_000L, "Source no destino");
                    GameTestCompat.assertValueEqual(helper, tank(helper, A).store().stored(), 0L, "Source na origem");
                })
                .thenSucceed();
    }

    /**
     * As máquinas do Ars tiram do tanque pelo SourceManager; sem o bastante, nada sai. Porte 1.20.1: o Ars 4.12 tira
     * de uma fonte só ({@code takeSource}); no {@code main}, o Ars juntava fontes e devolvia o que tirou.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void arsMachinesTakeFromTank(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        placeTank(helper, A, RouterTier.BASIC).store().insert(5_000, false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(providerAt(helper, A), "sem provider do tanque"))
                .thenExecute(() -> {
                    helper.assertTrue(SourceTestSupport.takeNearby(helper.getLevel(), center(helper), 5, 2_000),
                            "o Ars não tirou 2.000 do tanque");
                    GameTestCompat.assertValueEqual(helper, tank(helper, A).store().stored(), 3_000L, "Source depois de tirar");
                    helper.assertTrue(!SourceTestSupport.takeNearby(helper.getLevel(), center(helper), 5, 9_000),
                            "o Ars tirou 9.000 de um tanque com 3.000");
                    GameTestCompat.assertValueEqual(helper, tank(helper, A).store().stored(), 3_000L, "Source depois de devolver");
                })
                .thenSucceed();
    }

    /** Com mais que {@link Integer#MAX_VALUE} guardado, o Ars tira a quantia exata (o caso do int). */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void arsTakesExactAmountFromHugeTank(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        placeTank(helper, A, RouterTier.ULTIMATE).store().insert(3_000_000_000L, false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(providerAt(helper, A), "sem provider do tanque"))
                .thenExecute(() -> {
                    helper.assertTrue(SourceTestSupport.takeNearby(helper.getLevel(), center(helper), 5, 1_000),
                            "o Ars não tirou 1.000 do tanque");
                    GameTestCompat.assertValueEqual(helper, tank(helper, A).store().stored(), 2_999_999_000L, "Source depois de tirar");
                })
                .thenSucceed();
    }

    /**
     * Os Sourcelinks enxergam o tanque enquanto ele aceita Source. O {@code setSource} do Ars passa pelo
     * {@code ScalarStore.replace}: limita à capacidade e avisa (o nível do bloco muda).
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sourcelinksSeeTank(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        placeTank(helper, A, RouterTier.BASIC);
        BlockPos tankPos = helper.absolutePos(A);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(providerAt(helper, A), "sem provider do tanque"))
                .thenExecute(() -> {
                    List<BlockPos> empty = SourceTestSupport.canGiveNearby(helper.getLevel(), center(helper), 5);
                    helper.assertTrue(empty.contains(tankPos), "o tanque vazio não aceita Source: " + empty);
                    SourceTestSupport.set(helper.getLevel(), tankPos, 200_000);
                    GameTestCompat.assertValueEqual(helper, tank(helper, A).store().stored(), 10_000L,
                            "setSource limitado à capacidade");
                    GameTestCompat.assertValueEqual(helper, helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 10,
                            "nível depois do setSource");
                    List<BlockPos> full = SourceTestSupport.canGiveNearby(helper.getLevel(), center(helper), 5);
                    helper.assertTrue(!full.contains(tankPos), "o tanque cheio ainda aceita Source");
                })
                .thenSucceed();
    }

    /**
     * Tanque Ultimate com o {@code int} cheio (mais que {@link Integer#MAX_VALUE}) não aparece para os
     * Sourcelinks: eles calculam a vazão como máximo − guardado e passariam 0 para sempre. Com espaço no
     * {@code int}, aparece. Um {@code setSource(Integer.MAX_VALUE)} nesse estado não derruba o conteúdo e um {@code setSource(MAX - 1000)} tira só 1.000.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sourcelinksSkipTankFullAsInt(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        placeTank(helper, A, RouterTier.ULTIMATE).store().insert(3_000_000_000L, false);
        BlockPos tankPos = helper.absolutePos(A);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(providerAt(helper, A), "sem provider do tanque"))
                .thenExecute(() -> {
                    List<BlockPos> full = SourceTestSupport.canGiveNearby(helper.getLevel(), center(helper), 5);
                    helper.assertTrue(!full.contains(tankPos), "o tanque com o int cheio aparece para os Sourcelinks");
                    SourceTestSupport.set(helper.getLevel(), tankPos, Integer.MAX_VALUE);
                    GameTestCompat.assertValueEqual(helper, tank(helper, A).store().stored(), 3_000_000_000L,
                            "setSource(MAX) derrubou o conteúdo acima do int");
                    // um setSource(getSource() - n) de terceiros tira só n, não derruba para o teto do int
                    SourceTestSupport.set(helper.getLevel(), tankPos, Integer.MAX_VALUE - 1000);
                    GameTestCompat.assertValueEqual(helper, tank(helper, A).store().stored(), 2_999_999_000L,
                            "setSource(MAX - 1000) não tirou só 1000");
                    tank(helper, A).store().extract(2_999_999_000L - 1_000_000L, false);
                    List<BlockPos> room = SourceTestSupport.canGiveNearby(helper.getLevel(), center(helper), 5);
                    helper.assertTrue(room.contains(tankPos), "o tanque com 1.000.000 não aparece: " + room);
                })
                .thenSucceed();
    }

    /** Quebrado o tanque, o provider dele deixa de valer. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void providerInvalidAfterBreak(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        placeTank(helper, A, RouterTier.BASIC);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(providerAt(helper, A), "sem provider do tanque"))
                .thenExecute(() -> helper.setBlock(A, Blocks.AIR))
                .thenIdle(1)
                .thenExecute(() -> helper.assertTrue(!providerAt(helper, A), "provider válido sem o tanque"))
                .thenSucceed();
    }

    /** A visão do Ars no tanque ({@code ISourceTile} no 1.20.1), em int: quantidade e capacidade cortadas no Integer.MAX_VALUE. */
    @GameTest(template = "empty")
    public static void tankHasSourceCapability(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        placeTank(helper, A, RouterTier.ULTIMATE).store().insert(3_000_000_000L, false);
        helper.assertTrue(SourceTestSupport.hasSource(helper.getLevel(), helper.absolutePos(A)),
                "tanque sem ISourceTile");
        GameTestCompat.assertValueEqual(helper, SourceTestSupport.capacity(helper.getLevel(), helper.absolutePos(A)),
                Integer.MAX_VALUE, "capacidade do Ultimate");
        GameTestCompat.assertValueEqual(helper, amount(helper, A), Integer.MAX_VALUE, "quantidade acima do int");
        helper.succeed();
    }

    /** A receita do tanque carrega com o Ars. */
    @GameTest(template = "empty")
    public static void sourceTankRecipeLoaded(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .byKey(new ResourceLocation("wirelessautomate", "storage_source_tank")).isPresent(),
                "sem a receita do Tanque de Source");
        helper.succeed();
    }

    // ---- Relays, origem e destino (porte 1.20.1, etapa 2) ----

    private static final ResourceLocation RELAY = new ResourceLocation("ars_nouveau", "relay");
    private static final ResourceLocation RELAY_SPLITTER = new ResourceLocation("ars_nouveau", "relay_splitter");
    private static final ResourceLocation RELAY_COLLECTOR = new ResourceLocation("ars_nouveau", "relay_collector");
    private static final ResourceLocation RELAY_DEPOSIT = new ResourceLocation("ars_nouveau", "relay_deposit");
    private static final ResourceLocation CREATIVE_JAR = new ResourceLocation("ars_nouveau", "creative_source_jar");
    private static final ResourceLocation IMBUEMENT = new ResourceLocation("ars_nouveau", "imbuement_chamber");
    private static final ResourceLocation SOURCELINK = new ResourceLocation("ars_nouveau", "volcanic_sourcelink");
    private static final BlockPos D = new BlockPos(2, 1, 2);

    /** Põe o bloco do Ars em {@code pos}; falha se ele não existe no registro. */
    private static void arsBlock(GameTestHelper helper, BlockPos pos, ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR) {
            helper.fail("sem " + id + " no registro");
        }
        helper.setBlock(pos, block);
    }

    /** Um bloco do Ars em {@code pos} com um roteador Ultimate em cima, na rede, no modo dado. */
    private static RouterBlockEntity arsMachine(GameTestHelper helper, BlockPos pos, ResourceLocation id, UUID network,
            PortMode mode) {
        arsBlock(helper, pos, id);
        BlockPos routerPos = pos.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, routerPos);
        router.setNetworkId(network);
        router.setMode(ResourceType.SOURCE, Direction.UP, mode);
        return router;
    }

    private static long stored(GameTestHelper helper, BlockPos pos) {
        return tank(helper, pos).store().stored();
    }

    /**
     * Varinha no Relay e depois no tanque: o Relay passa a mandar para o tanque (o {@code instanceof
     * AbstractSourceMachine} do Relay vê a {@code RelayView}) e, a cada ciclo de 20 ticks, a Source vai do Relay
     * para o tanque.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void relaySendsToTankByWand(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        placeTank(helper, A, RouterTier.BASIC);
        arsBlock(helper, D, RELAY);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(providerAt(helper, A), "sem provider do tanque"))
                .thenExecute(() -> {
                    helper.assertTrue(SourceTestSupport.wandSendTo(helper.getLevel(), helper.absolutePos(D),
                            helper.absolutePos(A), helper.makeMockPlayer()), "o Relay não ligou o envio ao tanque");
                    set(helper, D, 1_000);
                })
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, stored(helper, A), 1_000L, "Source no tanque");
                    GameTestCompat.assertValueEqual(helper, amount(helper, D), 0, "Source no Relay");
                })
                .thenSucceed();
    }

    /**
     * Varinha no tanque e depois no Relay: o Relay passa a tirar do tanque e enche (1.000) em ciclos; o resto
     * fica no tanque.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void relayTakesFromTankByWand(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        placeTank(helper, A, RouterTier.BASIC).store().insert(5_000, false);
        arsBlock(helper, D, RELAY);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(providerAt(helper, A), "sem provider do tanque"))
                .thenExecute(() -> helper.assertTrue(SourceTestSupport.wandTakeFrom(helper.getLevel(),
                        helper.absolutePos(D), helper.absolutePos(A), helper.makeMockPlayer()),
                        "o Relay não ligou a coleta no tanque"))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, D), 1_000, "Source no Relay");
                    GameTestCompat.assertValueEqual(helper, stored(helper, A), 4_000L, "Source no tanque");
                })
                .thenSucceed();
    }

    /**
     * Relay Splitter entre dois tanques: tira de um ({@code processFromList}) e manda para o outro
     * ({@code processToList}) em ciclos, sem descartar os tanques das listas e sem perder Source.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void relaySplitterMovesBetweenTanks(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        placeTank(helper, A, RouterTier.BASIC).store().insert(5_000, false);
        placeTank(helper, B, RouterTier.BASIC);
        arsBlock(helper, D, RELAY_SPLITTER);
        BlockPos from = helper.absolutePos(A);
        BlockPos to = helper.absolutePos(B);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(providerAt(helper, A) && providerAt(helper, B),
                        "sem provider dos tanques"))
                .thenExecute(() -> helper.assertTrue(SourceTestSupport.splitterLink(helper.getLevel(),
                        helper.absolutePos(D), from, to), "o Splitter não ligou os tanques"))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, stored(helper, B), 5_000L, "Source no tanque de destino");
                    GameTestCompat.assertValueEqual(helper, stored(helper, A), 0L, "Source no tanque de origem");
                })
                .thenExecute(() -> {
                    helper.assertTrue(SourceTestSupport.splitterStillLinked(helper.getLevel(), helper.absolutePos(D),
                            from, to), "o Splitter descartou um tanque");
                    GameTestCompat.assertValueEqual(helper, amount(helper, D), 0, "Source parada no Splitter");
                })
                .thenSucceed();
    }

    /** O Relay de coleta tira do tanque no raio dele (pelo SourceManager), sem ligação. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void relayCollectorTakesFromTank(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        placeTank(helper, A, RouterTier.BASIC).store().insert(5_000, false);
        arsBlock(helper, D, RELAY_COLLECTOR);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, D), 1_000, "Source no Relay de coleta");
                    GameTestCompat.assertValueEqual(helper, stored(helper, A), 4_000L, "Source no tanque");
                })
                .thenSucceed();
    }

    /** O Relay de depósito põe no tanque no raio dele (pelo SourceManager), sem ligação. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void relayDepositFillsTank(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        placeTank(helper, A, RouterTier.BASIC);
        arsBlock(helper, D, RELAY_DEPOSIT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(providerAt(helper, A), "sem provider do tanque"))
                .thenExecute(() -> set(helper, D, 1_000))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, stored(helper, A), 1_000L, "Source no tanque");
                    GameTestCompat.assertValueEqual(helper, amount(helper, D), 0, "Source no Relay de depósito");
                })
                .thenSucceed();
    }

    /**
     * Imbuement Chamber numa face em Extrair não é drenada (como no main, ela só recebe): a jarra ao lado, na
     * mesma rede, entrega ao destino, e a Source da câmara fica.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void imbuementChamberIsNotDrained(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-imbuement");
        RouterBlockEntity chamber = arsMachine(helper, A, IMBUEMENT, network, PortMode.EXTRACT);
        RouterBlockEntity jar = jar(helper, B, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity to = jar(helper, C, RouterTier.ULTIMATE, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(chamber, jar, to), "roteadores não registrados"))
                .thenExecute(() -> {
                    set(helper, A, 5_000);
                    set(helper, B, 1_000);
                })
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, amount(helper, C), 1_000, "Source da jarra"))
                .thenIdle(40)
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, A), 5_000, "Source na Imbuement Chamber");
                    GameTestCompat.assertValueEqual(helper, amount(helper, C), 1_000, "Source no destino");
                })
                .thenSucceed();
    }

    /**
     * Sourcelink numa face em Inserir não recebe (como no main): toda a Source vai para a jarra de mesma
     * prioridade, que sem a regra dividiria com o Sourcelink.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sourcelinkDoesNotReceive(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-sourcelink");
        RouterBlockEntity from = jar(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity link = arsMachine(helper, B, SOURCELINK, network, PortMode.INSERT);
        RouterBlockEntity to = jar(helper, C, RouterTier.ULTIMATE, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, link, to), "roteadores não registrados"))
                .thenExecute(() -> set(helper, A, 1_000))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, amount(helper, A), 0, "Source na origem"))
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, C), 1_000, "Source na jarra");
                    GameTestCompat.assertValueEqual(helper, amount(helper, B), 0, "Source no Sourcelink");
                })
                .thenIdle(40)
                .thenExecute(() -> GameTestCompat.assertValueEqual(helper, amount(helper, B), 0, "Source no Sourcelink"))
                .thenSucceed();
    }

    /** Creative Jar num destino é ralo: recebe tudo e continua cheia. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void creativeJarIsSink(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-ralo");
        RouterBlockEntity from = jar(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity sink = arsMachine(helper, B, CREATIVE_JAR, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, sink), "roteadores não registrados"))
                .thenExecute(() -> set(helper, A, 5_000))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, amount(helper, A), 0, "Source na origem"))
                .thenExecute(() -> GameTestCompat.assertValueEqual(helper, amount(helper, B),
                        SourceTestSupport.capacity(helper.getLevel(), helper.absolutePos(B)), "Creative Jar"))
                .thenSucceed();
    }

    /** Creative Jar numa origem é fonte: enche o tanque de destino e continua cheia. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void creativeJarIsSource(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-fonte");
        RouterBlockEntity source = arsMachine(helper, A, CREATIVE_JAR, network, PortMode.EXTRACT);
        RouterBlockEntity to = sourceTank(helper, B, RouterTier.BASIC, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(source, to), "roteadores não registrados"))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, stored(helper, B), 10_000L,
                        "Source no tanque cheio"))
                .thenExecute(() -> GameTestCompat.assertValueEqual(helper, amount(helper, A),
                        SourceTestSupport.capacity(helper.getLevel(), helper.absolutePos(A)), "Creative Jar"))
                .thenSucceed();
    }

    private SourceGameTests() {
    }
}
