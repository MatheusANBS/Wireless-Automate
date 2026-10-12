package io.github.matheusanbs.wirelessautomate.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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
    public static void allFacesDropsExceptionEqualToProfile(GameTestHelper helper) {
        RouterBlockEntity router = routerWithFurnaces(helper);
        UUID network = network(helper, "exceção igual ao perfil");
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    helper.assertTrue(router.linkMachine(helper.absolutePos(FURNACE_A)), "não vinculou a fornalha A");
                    helper.assertTrue(router.linkMachine(helper.absolutePos(FURNACE_B)), "não vinculou a fornalha B");
                    RemoteMachines remotes = router.remoteMachines();
                    int indexA = remotes.index(helper.absolutePos(FURNACE_A));
                    int indexB = remotes.index(helper.absolutePos(FURNACE_B));
                    // A: exceção só pela rede; B: exceção pelo modo
                    router.setFaceNetwork(EditTarget.machine(indexA), ResourceType.ITEM, Direction.NORTH, network);
                    router.configureFace(EditTarget.machine(indexB), ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE);
                    helper.assertTrue(remotes.machine(indexA).hasException(), "a A não ganhou exceção");

                    router.setNetworkId(ResourceType.ITEM, network);
                    helper.assertFalse(remotes.machine(indexA).hasException(),
                            "a exceção igual ao perfil continuou depois de todas as faces");
                    helper.assertTrue(remotes.machine(indexB).hasException(), "a exceção de verdade sumiu");
                    assertAllRemote(helper, router, EditTarget.machine(indexA), ResourceType.ITEM, network,
                            "a A fora da rede");
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

    // ------------------------------------------------------------------ motor (rede de cada porta)

    // Baús nos cantos e no centro (nenhum vizinho de outro), cada um com um roteador em cima
    // (facing=UP): a face configurada da máquina é UP, salvo no roteador do centro, que usa UP e DOWN
    // (as duas dão no mesmo baú).
    private static final BlockPos CORNER_A = new BlockPos(0, 1, 0);
    private static final BlockPos CORNER_B = new BlockPos(2, 1, 2);
    private static final BlockPos CORNER_C = new BlockPos(2, 1, 0);
    private static final BlockPos CORNER_D = new BlockPos(0, 1, 2);

    /** Baú em {@code pos} com um roteador Básico em cima, com a face UP de itens em {@code mode} na rede. */
    private static RouterBlockEntity chestRouter(GameTestHelper helper, BlockPos pos, @Nullable UUID network, PortMode mode) {
        helper.setBlock(pos, Blocks.CHEST);
        helper.setBlock(pos.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos.above());
        router.setNetworkId(ResourceType.ITEM, network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        return router;
    }

    /** O roteador do centro ({@link #CHEST}) sem rede; as faces são postas por {@link #face}. */
    private static RouterBlockEntity middle(GameTestHelper helper) {
        return chestRouter(helper, CHEST, null, PortMode.NONE);
    }

    private static void face(RouterBlockEntity router, Direction face, @Nullable UUID network, PortMode mode) {
        router.setFaceNetwork(ResourceType.ITEM, face, network);
        router.setMode(ResourceType.ITEM, face, mode);
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        ChestBlockEntity chest = helper.getBlockEntity(pos);
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void assertCount(GameTestHelper helper, BlockPos pos, int expected) {
        int actual = count(helper, pos, Items.DIAMOND);
        helper.assertTrue(actual == expected, "diamantes em " + pos.toShortString() + ": esperava " + expected + ", veio " + actual);
    }

    private static void putDiamonds(GameTestHelper helper, BlockPos pos, int amount) {
        ChestBlockEntity chest = helper.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, amount));
    }

    private static void allLoaded(GameTestHelper helper, RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            loaded(helper, router);
        }
    }

    private static @Nullable Object order(RouterBlockEntity router, Direction face) {
        return NetworkManager.get().sourceOrderForTests(router, ResourceType.ITEM, face);
    }

    /** O exemplo da spec: o item sai de A pela rede Mina, entra em M por Cima, sai por Baixo pela Armazém e chega em B. */
    @GameTest(template = "empty")
    public static void twoNetworksThroughOneMachine(GameTestHelper helper) {
        UUID mine = network(helper, "mina");
        UUID storage = network(helper, "armazem");
        RouterBlockEntity m = middle(helper);
        face(m, Direction.UP, mine, PortMode.INSERT);
        face(m, Direction.DOWN, storage, PortMode.EXTRACT);
        RouterBlockEntity s = chestRouter(helper, CORNER_A, mine, PortMode.EXTRACT);
        RouterBlockEntity t = chestRouter(helper, CORNER_B, storage, PortMode.INSERT);
        putDiamonds(helper, CORNER_A, 10);
        helper.startSequence()
                .thenWaitUntil(() -> allLoaded(helper, m, s, t))
                .thenExecute(() -> {
                    helper.assertTrue(NetworkManager.get().isMember(mine, m), "M fora da Mina");
                    helper.assertTrue(NetworkManager.get().isMember(storage, m), "M fora do Armazém");
                })
                .thenWaitUntil(() -> {
                    assertCount(helper, CORNER_B, 10);
                    assertCount(helper, CORNER_A, 0);
                    assertCount(helper, CHEST, 0);
                })
                .thenSucceed();
    }

    /**
     * Mudar a rede de uma face suja o tipo na rede antiga, na nova e nas outras do nó naquele tipo
     * (a leitura das portas é dividida); depois da montagem o nó sai da antiga e ela para de entregar.
     */
    @GameTest(template = "empty")
    public static void changingFaceNetworkDirtiesOldAndNew(GameTestHelper helper) {
        UUID mine = network(helper, "mina");
        UUID storage = network(helper, "armazem");
        UUID third = network(helper, "terceira");
        RouterBlockEntity m = middle(helper);
        face(m, Direction.UP, mine, PortMode.INSERT);
        face(m, Direction.DOWN, storage, PortMode.EXTRACT);
        RouterBlockEntity s = chestRouter(helper, CORNER_A, mine, PortMode.EXTRACT);
        RouterBlockEntity t = chestRouter(helper, CORNER_B, storage, PortMode.INSERT);
        // A terceira rede ganha outro membro, para existir no motor antes de M entrar nela.
        RouterBlockEntity other = chestRouter(helper, CORNER_C, third, PortMode.NONE);
        putDiamonds(helper, CORNER_A, 1);
        helper.startSequence()
                .thenWaitUntil(() -> allLoaded(helper, m, s, t, other))
                .thenWaitUntil(() -> assertCount(helper, CORNER_B, 1))
                .thenExecute(() -> {
                    m.setFaceNetwork(ResourceType.ITEM, Direction.UP, third);
                    NetworkManager manager = NetworkManager.get();
                    helper.assertTrue(manager.isDirty(mine, ResourceType.ITEM), "a rede antiga não sujou");
                    helper.assertTrue(manager.isDirty(third, ResourceType.ITEM), "a rede nova não sujou");
                    helper.assertTrue(manager.isDirty(storage, ResourceType.ITEM), "a outra rede do nó não sujou");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertFalse(NetworkManager.get().isMember(mine, m), "M continua na Mina");
                    helper.assertTrue(NetworkManager.get().isMember(third, m), "M fora da terceira rede");
                    helper.assertTrue(NetworkManager.get().isMember(storage, m), "M saiu do Armazém");
                    putDiamonds(helper, CORNER_A, 5);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    assertCount(helper, CORNER_A, 5);
                    assertCount(helper, CHEST, 0);
                })
                .thenSucceed();
    }

    /**
     * O risco da leitura dividida: R com Baixo extraindo na rede X e Cima na Y, cada uma com dois
     * destinos (o rodízio tem cursor). Mudar uma face relê o tipo inteiro no nó; a ordem da outra face,
     * na outra rede, tem de continuar a mesma instância. Mudar Cima e depois Baixo cobre as duas
     * ordens de montagem (a rede da face mudada monta antes ou depois da outra).
     */
    @GameTest(template = "empty")
    public static void cursorsSurviveWithTwoNetworksOnOneNode(GameTestHelper helper) {
        UUID x = network(helper, "x");
        UUID y = network(helper, "y");
        RouterBlockEntity r = middle(helper);
        face(r, Direction.DOWN, x, PortMode.EXTRACT);
        face(r, Direction.UP, y, PortMode.EXTRACT);
        RouterBlockEntity c = chestRouter(helper, CORNER_C, x, PortMode.INSERT);
        RouterBlockEntity d = chestRouter(helper, CORNER_D, x, PortMode.INSERT);
        RouterBlockEntity a = chestRouter(helper, CORNER_A, y, PortMode.INSERT);
        RouterBlockEntity b = chestRouter(helper, CORNER_B, y, PortMode.INSERT);
        Object[] kept = new Object[1];
        helper.startSequence()
                .thenWaitUntil(() -> allLoaded(helper, r, a, b, c, d))
                .thenWaitUntil(() -> {
                    helper.assertTrue(order(r, Direction.DOWN) != null, "Baixo sem rota");
                    helper.assertTrue(order(r, Direction.UP) != null, "Cima sem rota");
                })
                .thenExecute(() -> {
                    kept[0] = order(r, Direction.DOWN);
                    r.setPriority(ResourceType.ITEM, Direction.UP, 5);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    Object now = order(r, Direction.DOWN);
                    helper.assertTrue(now != null, "Baixo ficou sem rota ao mudar Cima");
                    helper.assertTrue(now == kept[0], "Baixo perdeu os cursores ao mudar Cima");
                    helper.assertTrue(order(r, Direction.UP) != null, "Cima ficou sem rota");
                    kept[0] = order(r, Direction.UP);
                    r.setPriority(ResourceType.ITEM, Direction.DOWN, 5);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    Object now = order(r, Direction.UP);
                    helper.assertTrue(now != null, "Cima ficou sem rota ao mudar Baixo");
                    helper.assertTrue(now == kept[0], "Cima perdeu os cursores ao mudar Baixo");
                    helper.assertTrue(order(r, Direction.DOWN) != null, "Baixo ficou sem rota");
                })
                .thenSucceed();
    }

    /**
     * Na rede Y, uma origem de outro roteador dorme e entrega em destinos que não são de R. Mudar a
     * face Baixo de R (rede X) remonta Y também (R tem a face Cima nela), mas não acorda essa origem
     * nem a deixa sem rota.
     */
    @GameTest(template = "empty")
    public static void otherNodeSleepSurvives(GameTestHelper helper) {
        UUID x = network(helper, "x");
        UUID y = network(helper, "y");
        RouterBlockEntity r = middle(helper);
        face(r, Direction.DOWN, x, PortMode.EXTRACT);
        face(r, Direction.UP, y, PortMode.EXTRACT);
        RouterBlockEntity c = chestRouter(helper, CORNER_C, x, PortMode.INSERT);
        RouterBlockEntity s = chestRouter(helper, CORNER_A, y, PortMode.EXTRACT);
        RouterBlockEntity b = chestRouter(helper, CORNER_B, y, PortMode.INSERT);
        Object[] kept = new Object[1];
        int[] interval = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> allLoaded(helper, r, c, s, b))
                .thenWaitUntil(() -> helper.assertTrue(order(s, Direction.UP) != null, "a origem de Y sem rota"))
                .thenExecute(() -> {
                    NetworkManager manager = NetworkManager.get();
                    long now = helper.getLevel().getServer().getTickCount();
                    helper.assertTrue(manager.sleepForTests(s, null, ResourceType.ITEM, Direction.UP, now, 1000), "porta não existe");
                    kept[0] = order(s, Direction.UP);
                    interval[0] = manager.sourceSleepInterval(s, ResourceType.ITEM, Direction.UP);
                    r.setPriority(ResourceType.ITEM, Direction.DOWN, 5);
                    helper.assertTrue(manager.isDirty(y, ResourceType.ITEM), "Y não sujou");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    NetworkManager manager = NetworkManager.get();
                    long now = helper.getLevel().getServer().getTickCount();
                    helper.assertFalse(manager.sourceAwake(s, null, ResourceType.ITEM, Direction.UP, now), "a origem de Y acordou");
                    helper.assertTrue(manager.sourceSleepInterval(s, ResourceType.ITEM, Direction.UP) == interval[0],
                            "o intervalo do sono mudou");
                    Object order = order(s, Direction.UP);
                    helper.assertTrue(order != null, "a origem de Y ficou sem rota");
                    helper.assertTrue(order == kept[0], "a origem de Y perdeu os cursores");
                })
                .thenSucceed();
    }

    /** Apagar a rede de uma face para só essa face; ela guarda o UUID e a outra continua movendo. */
    @GameTest(template = "empty")
    public static void removedNetworkStopsOnlyThatFace(GameTestHelper helper) {
        UUID mine = network(helper, "mina");
        UUID storage = network(helper, "armazem");
        RouterBlockEntity m = middle(helper);
        face(m, Direction.UP, mine, PortMode.INSERT);
        face(m, Direction.DOWN, storage, PortMode.EXTRACT);
        RouterBlockEntity s = chestRouter(helper, CORNER_A, mine, PortMode.EXTRACT);
        RouterBlockEntity t = chestRouter(helper, CORNER_B, storage, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> allLoaded(helper, m, s, t))
                .thenExecute(() -> helper.assertTrue(
                        NetworkSavedData.get(helper.getLevel().getServer()).remove(storage), "rede não removida"))
                .thenIdle(2)
                .thenExecute(() -> {
                    assertNetwork(helper, m.faceNetwork(ResourceType.ITEM, Direction.DOWN), storage, "a face Baixo perdeu o UUID");
                    putDiamonds(helper, CORNER_A, 10);
                })
                .thenWaitUntil(() -> assertCount(helper, CHEST, 10))
                .thenIdle(20)
                .thenExecute(() -> {
                    assertCount(helper, CHEST, 10);
                    assertCount(helper, CORNER_B, 0);
                })
                .thenSucceed();
    }

    /** Sem rede em nenhuma face de itens, R sai da rede e as portas não ficam com rota velha. */
    @GameTest(template = "empty")
    public static void noNetworkFacesLeaveNoMembership(GameTestHelper helper) {
        UUID x = network(helper, "x");
        RouterBlockEntity r = middle(helper);
        face(r, Direction.UP, x, PortMode.EXTRACT);
        RouterBlockEntity t = chestRouter(helper, CORNER_B, x, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> allLoaded(helper, r, t))
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().isMember(x, r), "R fora da rede"))
                .thenExecute(() -> {
                    r.setNetworkId(ResourceType.ITEM, null);
                    putDiamonds(helper, CHEST, 10);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertFalse(NetworkManager.get().isMember(x, r), "R continua na rede");
                    helper.assertTrue(order(r, Direction.UP) == null, "a porta ficou com rota velha");
                    assertCount(helper, CHEST, 10);
                    assertCount(helper, CORNER_B, 0);
                })
                .thenSucceed();
    }

    /**
     * {@code /wa face <pos> <tipo> <face> network <nome|none>} pelo dispatcher do servidor: a face
     * absoluta recebe a rede pelo nome e {@code none} tira; o argumento de modo continua valendo.
     */
    @GameTest(template = "empty")
    public static void faceCommandSetsNetwork(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        String name = "Comando " + UUID.randomUUID();
        UUID net = network(helper, name);
        MinecraftServer server = helper.getLevel().getServer();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        CommandSourceStack source = server.createCommandSourceStack().withLevel(helper.getLevel())
                .withSuppressedOutput();
        BlockPos pos = helper.absolutePos(ROUTER);
        String prefix = "wa face " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " item ";
        try {
            dispatcher.execute(prefix + "up network " + name, source);
            assertNetwork(helper, router.faceNetwork(ResourceType.ITEM, Direction.UP), net, "rede pelo comando");
            assertNetwork(helper, router.faceNetwork(ResourceType.ITEM, Direction.DOWN), null, "outra face");
            assertNetwork(helper, router.faceNetwork(ResourceType.FLUID, Direction.UP), null, "outro tipo");

            dispatcher.execute(prefix + "down extract", source);
            helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.DOWN).mode(), PortMode.EXTRACT,
                    "o modo deixou de valer");

            dispatcher.execute(prefix + "up network none", source);
            assertNetwork(helper, router.faceNetwork(ResourceType.ITEM, Direction.UP), null, "none não tirou");

            boolean refused = false;
            try {
                dispatcher.execute(prefix + "up network Inexistente " + UUID.randomUUID(), source);
            } catch (CommandSyntaxException e) {
                refused = true;
            }
            helper.assertTrue(refused, "aceitou uma rede que não existe");
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("comando recusado: " + e.getMessage(), e);
        } finally {
            NetworkSavedData.get(server).remove(net);
        }
        helper.succeed();
    }
}
