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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Source do Ars Nouveau: Source Jars de verdade, um roteador em cima de cada (facing=UP, face
 * configurada {@link net.minecraft.core.Direction#UP}). Rodam só na run {@code runGameTestServerSource},
 * que tem o Ars na pasta mods e liga o namespace {@value #NAMESPACE} (o template é
 * {@code data/wirelessautomate_source/structure/empty.nbt}). Sem o Ars, só passam.
 *
 * <p>Nenhum tipo do Ars nas assinaturas: o NeoForge inspeciona esta classe por reflexão mesmo sem o
 * Ars. O que usa a API fica em {@code SourceTestSupport}.
 */
@GameTestHolder(SourceGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class SourceGameTests {
    static final String NAMESPACE = "wirelessautomate_source";
    static final ResourceLocation JAR = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_jar");

    /** A run de Source está ligada e o Ars está presente. */
    static boolean enabled() {
        return Boolean.getBoolean("wirelessautomate.sourceTests") && ModList.get().isLoaded("ars_nouveau");
    }

    /** A run de Source carrega o Ars, e a Source Jar existe no registro e tem a capability. */
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
                "Source Jar sem a capability ars_nouveau:source");
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
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
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
                    helper.assertValueEqual(amount(helper, B), 5_000, "Source no destino");
                    helper.assertValueEqual(amount(helper, A), 0, "Source na origem");
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
                .thenWaitUntil(() -> helper.assertValueEqual(amount(helper, B), 3_000, "Source na prioridade 5"))
                .thenIdle(10)
                .thenExecute(() -> helper.assertValueEqual(amount(helper, C), 0, "Source na prioridade 0"))
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
                .thenWaitUntil(() -> helper.assertValueEqual(amount(helper, A), 0, "Source na origem"))
                .thenExecute(() -> {
                    helper.assertValueEqual(amount(helper, B), 2_000, "primeira jarra");
                    helper.assertValueEqual(amount(helper, C), 2_000, "segunda jarra");
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
                    helper.assertValueEqual(amount(helper, A), 1_000, "saiu Source para uma jarra cheia");
                    set(helper, B, 0);
                })
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(amount(helper, B), 1_000, "Source depois de esvaziar o destino");
                    helper.assertValueEqual(amount(helper, A), 0, "Source na origem");
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
                    helper.assertTrue(moved >= 1_000, "o limitador travou, passou só " + moved);
                    // Um segundo de balde (1.000) + 20 ticks a 50 por tick (1.000), com folga de um tick.
                    helper.assertTrue(moved <= 2_050, "passou do limite do tier: " + moved);
                    helper.assertValueEqual(amount(helper, A) + moved, 10_000, "Source perdida ou criada");
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
        return helper.getBlockEntity(pos);
    }

    /** Tanque de Source do tier em {@code pos}, com um roteador Ultimate em cima, na rede, no modo dado. */
    private static RouterBlockEntity sourceTank(GameTestHelper helper, BlockPos pos, RouterTier tier, UUID network,
            PortMode mode) {
        placeTank(helper, pos, tier);
        BlockPos routerPos = pos.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
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
                    helper.assertValueEqual(tank(helper, B).store().stored(), 5_000L, "Source no tanque");
                    helper.assertValueEqual(amount(helper, A), 0, "Source na jarra");
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
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(tank(helper, B).store().stored(), 3_000_000_000L, "Source no destino");
                    helper.assertValueEqual(tank(helper, A).store().stored(), 0L, "Source na origem");
                })
                .thenSucceed();
    }

    /** As máquinas do Ars tiram do tanque pelo SourceManager; sem o bastante, o Ars devolve o que tirou. */
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
                    helper.assertValueEqual(tank(helper, A).store().stored(), 3_000L, "Source depois de tirar");
                    helper.assertTrue(!SourceTestSupport.takeNearby(helper.getLevel(), center(helper), 5, 9_000),
                            "o Ars tirou 9.000 de um tanque com 3.000");
                    helper.assertValueEqual(tank(helper, A).store().stored(), 3_000L, "Source depois de devolver");
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
                    helper.assertValueEqual(tank(helper, A).store().stored(), 2_999_999_000L, "Source depois de tirar");
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
                    helper.assertValueEqual(tank(helper, A).store().stored(), 160_000L,
                            "setSource limitado à capacidade");
                    helper.assertValueEqual(helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 10,
                            "nível depois do setSource");
                    List<BlockPos> full = SourceTestSupport.canGiveNearby(helper.getLevel(), center(helper), 5);
                    helper.assertTrue(!full.contains(tankPos), "o tanque cheio ainda aceita Source");
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

    /** A capability do Ars no tanque, em int: quantidade e capacidade cortadas no Integer.MAX_VALUE. */
    @GameTest(template = "empty")
    public static void tankHasSourceCapability(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        placeTank(helper, A, RouterTier.ULTIMATE).store().insert(3_000_000_000L, false);
        helper.assertTrue(SourceTestSupport.hasSource(helper.getLevel(), helper.absolutePos(A)),
                "tanque sem a capability ars_nouveau:source");
        helper.assertValueEqual(SourceTestSupport.capacity(helper.getLevel(), helper.absolutePos(A)),
                Integer.MAX_VALUE, "capacidade do Ultimate");
        helper.assertValueEqual(amount(helper, A), Integer.MAX_VALUE, "quantidade acima do int");
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
                .byKey(ResourceLocation.fromNamespaceAndPath("wirelessautomate", "storage_source_tank")).isPresent(),
                "sem a receita do Tanque de Source");
        helper.succeed();
    }

    private SourceGameTests() {
    }
}
