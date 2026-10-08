package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
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

    private SourceGameTests() {
    }
}
