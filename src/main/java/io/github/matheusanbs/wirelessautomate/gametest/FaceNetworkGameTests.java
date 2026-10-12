package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Rede por face: a rede mora no {@link FaceConfig} (presa, perfis e exceções), sobrevive ao NBT e ao
 * giro, e a rede da aba antiga ({@code networks}, ou a única {@code network}) migra para as faces na
 * carga sem sobrescrever a rede que uma face já tem.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class FaceNetworkGameTests {
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private static final BlockPos ROUTER = CHEST.above();
    private static final BlockPos FURNACE_A = new BlockPos(0, 1, 0);
    private static final BlockPos FURNACE_B = new BlockPos(2, 1, 0);

    /** Baú com um roteador Avançado preso por cima, sem rede. */
    private static RouterBlockEntity router(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.setBlock(ROUTER, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        return helper.getBlockEntity(ROUTER);
    }

    /** O roteador de {@link #router} com duas fornalhas em volta, para vincular. */
    private static RouterBlockEntity routerWithFurnaces(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        helper.setBlock(FURNACE_A, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
        helper.setBlock(FURNACE_B, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
        return router;
    }

    private static UUID network(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    private static void loaded(GameTestHelper helper, RouterBlockEntity router) {
        helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
    }

    private static CompoundTag save(GameTestHelper helper, RouterBlockEntity router) {
        return router.saveWithFullMetadata(helper.getLevel().registryAccess());
    }

    /** Um roteador novo, fora do mundo, lido de {@code tag}. */
    private static RouterBlockEntity reload(GameTestHelper helper, RouterBlockEntity router, CompoundTag tag) {
        RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
        copy.loadWithComponents(tag, helper.getLevel().registryAccess());
        return copy;
    }

    /** Compara redes (podem ser nulas: o {@code assertValueEqual} derruba o servidor com {@code null}). */
    private static void assertNetwork(GameTestHelper helper, @Nullable UUID actual, @Nullable UUID expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperava " + expected + ", veio " + actual);
    }

    private static void assertAllAttached(GameTestHelper helper, RouterBlockEntity router, ResourceType type,
            UUID expected, String what) {
        for (Direction face : Direction.values()) {
            assertNetwork(helper, router.faceNetwork(type, face), expected, what + " (" + type + ", " + face + ")");
        }
    }

    private static void assertAllRemote(GameTestHelper helper, RouterBlockEntity router, EditTarget target,
            ResourceType type, UUID expected, String what) {
        for (Direction side : Direction.values()) {
            assertNetwork(helper, router.faceNetwork(target, type, side), expected, what + " (" + type + ", " + side + ")");
        }
    }

    @GameTest(template = "empty")
    public static void faceNetworkRoundTrip(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        UUID network = network(helper, "face");
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    // Rede numa face em modo Nenhum: o resto da face é padrão, mas a face não é.
                    router.setFaceNetwork(ResourceType.ITEM, Direction.NORTH, network);
                    helper.assertFalse(router.face(ResourceType.ITEM, Direction.NORTH).isDefault(), "face com rede é padrão");
                    helper.assertTrue(new FaceConfig().save(helper.getLevel().registryAccess()).isEmpty(),
                            "face padrão não é uma tag vazia");

                    CompoundTag tag = save(helper, router);
                    helper.assertFalse(tag.contains("networks"), "a tag antiga networks ainda é escrita");
                    RouterBlockEntity copy = reload(helper, router, tag);
                    assertNetwork(helper, copy.faceNetwork(ResourceType.ITEM, Direction.NORTH), network, "rede da face norte");
                    helper.assertValueEqual(copy.face(ResourceType.ITEM, Direction.NORTH).mode(), PortMode.NONE, "modo da face");
                    assertNetwork(helper, copy.faceNetwork(ResourceType.ITEM, Direction.SOUTH), null, "a face sul ganhou rede");
                    assertNetwork(helper, copy.faceNetwork(ResourceType.FLUID, Direction.NORTH), null, "fluidos ganharam rede");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void legacyNetworksMigrateToFaces(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        UUID items = network(helper, "itens antigos");
        UUID energy = network(helper, "energia antiga");
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    router.configureFace(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 2, RedstoneMode.IGNORE);
                    CompoundTag tag = save(helper, router);
                    CompoundTag networks = new CompoundTag();
                    networks.putUUID(ResourceType.ITEM.key(), items);
                    networks.putUUID(ResourceType.ENERGY.key(), energy);
                    tag.put("networks", networks);

                    RouterBlockEntity copy = reload(helper, router, tag);
                    assertAllAttached(helper, copy, ResourceType.ITEM, items, "itens fora da rede antiga");
                    assertAllAttached(helper, copy, ResourceType.ENERGY, energy, "energia fora da rede antiga");
                    for (ResourceType type : ResourceType.values()) {
                        if (type != ResourceType.ITEM && type != ResourceType.ENERGY) {
                            assertAllAttached(helper, copy, type, null, "tipo sem rede antiga ganhou rede");
                        }
                    }
                    FaceConfig up = copy.face(ResourceType.ITEM, Direction.UP);
                    helper.assertTrue(up.mode() == PortMode.EXTRACT && up.priority() == 2, "a face configurada se perdeu");

                    CompoundTag again = save(helper, copy);
                    helper.assertFalse(again.contains("networks"), "a tag networks voltou a ser escrita");
                    assertAllAttached(helper, reload(helper, router, again), ResourceType.ITEM, items,
                            "a rede migrada não sobreviveu a salvar de novo");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void legacySingleNetworkMigrates(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        UUID network = network(helper, "rede única antiga");
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    CompoundTag tag = save(helper, router);
                    tag.putUUID("network", network);
                    RouterBlockEntity copy = reload(helper, router, tag);
                    for (ResourceType type : ResourceType.values()) {
                        assertAllAttached(helper, copy, type, network, "a rede única não foi para as faces");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void legacyMigrationKeepsOwnFaceNetwork(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        UUID legacy = network(helper, "aba antiga");
        UUID own = network(helper, "da face");
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    router.setFaceNetwork(ResourceType.ITEM, Direction.EAST, own);
                    CompoundTag tag = save(helper, router);
                    CompoundTag networks = new CompoundTag();
                    networks.putUUID(ResourceType.ITEM.key(), legacy);
                    tag.put("networks", networks);

                    RouterBlockEntity copy = reload(helper, router, tag);
                    assertNetwork(helper, copy.faceNetwork(ResourceType.ITEM, Direction.EAST), own,
                            "a migração sobrescreveu a rede da face");
                    assertNetwork(helper, copy.faceNetwork(ResourceType.ITEM, Direction.WEST), legacy,
                            "a face sem rede não recebeu a rede antiga");
                    helper.assertTrue(Arrays.asList(copy.networks(ResourceType.ITEM)).containsAll(List.of(own, legacy)),
                            "networks(ITEM) sem as duas redes");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void legacyMigrationReachesProfilesAndExceptions(GameTestHelper helper) {
        RouterBlockEntity router = routerWithFurnaces(helper);
        UUID fluids = network(helper, "fluidos antigos");
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    helper.assertTrue(router.linkMachine(helper.absolutePos(FURNACE_A)), "não vinculou a fornalha A");
                    helper.assertTrue(router.linkMachine(helper.absolutePos(FURNACE_B)), "não vinculou a fornalha B");
                    RemoteMachines remotes = router.remoteMachines();
                    int indexB = remotes.index(helper.absolutePos(FURNACE_B));
                    EditTarget group = EditTarget.group(remotes.machine(indexB).group());
                    EditTarget machineB = EditTarget.machine(indexB);
                    router.configureFace(machineB, ResourceType.FLUID, Direction.UP, PortMode.INSERT, 0, RedstoneMode.IGNORE);
                    helper.assertTrue(remotes.machine(indexB).hasException(), "a B não ganhou exceção");

                    CompoundTag tag = save(helper, router);
                    CompoundTag networks = new CompoundTag();
                    networks.putUUID(ResourceType.FLUID.key(), fluids);
                    tag.put("networks", networks);
                    RouterBlockEntity copy = reload(helper, router, tag);

                    helper.assertTrue(copy.remoteMachines().machine(indexB).hasException(), "a exceção não foi lida");
                    assertAllRemote(helper, copy, group, ResourceType.FLUID, fluids, "perfil sem a rede antiga");
                    assertAllRemote(helper, copy, machineB, ResourceType.FLUID, fluids, "exceção sem a rede antiga");
                    assertAllRemote(helper, copy, group, ResourceType.ITEM, null, "itens do perfil ganharam rede");
                    helper.assertValueEqual(copy.face(machineB, ResourceType.FLUID, Direction.UP).mode(), PortMode.INSERT,
                            "a exceção perdeu a mudança");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void allFacesShortcut(GameTestHelper helper) {
        RouterBlockEntity router = routerWithFurnaces(helper);
        UUID network = network(helper, "todas as faces");
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    helper.assertTrue(router.linkMachine(helper.absolutePos(FURNACE_A)), "não vinculou a fornalha A");
                    helper.assertTrue(router.linkMachine(helper.absolutePos(FURNACE_B)), "não vinculou a fornalha B");
                    RemoteMachines remotes = router.remoteMachines();
                    int indexB = remotes.index(helper.absolutePos(FURNACE_B));
                    EditTarget group = EditTarget.group(remotes.machine(indexB).group());
                    EditTarget machineB = EditTarget.machine(indexB);
                    router.configureFace(machineB, ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0, RedstoneMode.IGNORE);

                    router.setNetworkId(ResourceType.ITEM, network);
                    assertAllAttached(helper, router, ResourceType.ITEM, network, "presa fora da rede");
                    assertAllRemote(helper, router, group, ResourceType.ITEM, network, "perfil fora da rede");
                    assertAllRemote(helper, router, machineB, ResourceType.ITEM, network, "exceção fora da rede");
                    helper.assertTrue(remotes.machine(indexB).hasException(), "a exceção sumiu");
                    helper.assertTrue(Arrays.equals(router.networks(ResourceType.ITEM), new UUID[] {network}),
                            "networks(ITEM) não é só a rede: " + Arrays.toString(router.networks(ResourceType.ITEM)));
                    assertNetwork(helper, router.networkId(ResourceType.ITEM), network, "networkId(ITEM)");
                    helper.assertTrue(router.networks(ResourceType.FLUID).length == 0, "fluidos ganharam rede");
                    helper.assertTrue(router.hasNetwork(), "hasNetwork falso");

                    router.setNetworkId(ResourceType.ITEM, null);
                    helper.assertFalse(router.hasNetwork(), "tirar da rede deixou alguma face numa rede");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void exceptionDiffersOnlyByNetwork(GameTestHelper helper) {
        RouterBlockEntity router = routerWithFurnaces(helper);
        UUID network = network(helper, "exceção");
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    helper.assertTrue(router.linkMachine(helper.absolutePos(FURNACE_A)), "não vinculou a fornalha A");
                    RemoteMachines remotes = router.remoteMachines();
                    int indexA = remotes.index(helper.absolutePos(FURNACE_A));
                    EditTarget machineA = EditTarget.machine(indexA);
                    EditTarget group = EditTarget.group(remotes.machine(indexA).group());

                    router.setFaceNetwork(machineA, ResourceType.ITEM, Direction.NORTH, network);
                    helper.assertTrue(remotes.machine(indexA).hasException(), "mudar só a rede não criou exceção");
                    assertNetwork(helper, router.faceNetwork(machineA, ResourceType.ITEM, Direction.NORTH), network,
                            "rede da exceção");
                    assertNetwork(helper, router.faceNetwork(group, ResourceType.ITEM, Direction.NORTH), null,
                            "a rede foi para o perfil");

                    router.setFaceNetwork(machineA, ResourceType.ITEM, Direction.NORTH, null);
                    helper.assertFalse(remotes.machine(indexA).hasException(), "voltar à rede do perfil deixou a exceção");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void spinKeepsFaceNetwork(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        UUID north = network(helper, "norte");
        UUID east = network(helper, "leste");
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    router.setFaceNetwork(ResourceType.ITEM, Direction.NORTH, north);
                    router.setFaceNetwork(ResourceType.ITEM, Direction.EAST, east);
                    router.spinTo(1);
                })
                .thenExecute(() -> {
                    RouterBlockEntity spun = helper.getBlockEntity(ROUTER);
                    helper.assertValueEqual(spun.spin(), 1, "não girou");
                    assertNetwork(helper, spun.faceNetwork(ResourceType.ITEM, Direction.NORTH), north, "rede da face norte");
                    assertNetwork(helper, spun.faceNetwork(ResourceType.ITEM, Direction.EAST), east, "rede da face leste");
                    assertNetwork(helper, spun.faceNetwork(ResourceType.ITEM, Direction.SOUTH), null, "a face sul ganhou rede");
                })
                .thenSucceed();
    }
}
