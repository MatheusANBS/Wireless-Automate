package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.menu.IdleNetworkUndo;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.NodeProbe;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ClearIdleNetworksPayload;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPayloads;
import io.github.matheusanbs.wirelessautomate.packet.TabletSelectNodePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Correções do roteador de alcance: o resgate da sobra na máquina da porta que recebe, o destino
 * cheio que conta as remotas sem criar porta, o grupo vinculado pelo Vinculador, que nasce sem rede, e o
 * "Tirar a rede das faces sem modo" do painel, com o Desfazer.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeFixGameTests {
    private static final BlockPos LYING = new BlockPos(0, 1, 0);
    private static final BlockPos NORMAL = new BlockPos(2, 1, 2);

    /**
     * O resgate usa a máquina da porta que recebeu: a presa é uma máquina de entrada e saída (a sobra não
     * volta pelo slot de saída) e os dois destinos são remotos; o que o destino que mente recusa termina no
     * remoto normal, e nada entra na entrada da presa nem cai no mundo.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void remoteRescueGoesToRemoteMachine(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            helper.succeed();
            return;
        }
        BlockPos machine = helper.absolutePos(CHEST);
        TestMachines.reset(machine);
        TestMachines.Furnace furnace = TestMachines.furnace(machine);
        furnace.slots[1] = new ItemStack(Items.STONE, 10);
        helper.setBlock(CHEST, TestMachines.FURNACE.defaultBlockState());
        helper.setBlock(CHEST.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        RouterBlockEntity router = helper.getBlockEntity(CHEST.above());
        router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "resgate").id());
        helper.setBlock(LYING, TestMachines.LYING_SINK.defaultBlockState());
        helper.setBlock(NORMAL, Blocks.CHEST);

        helper.onEachTick(() -> {
            helper.assertTrue(furnace.slots[0].isEmpty(), "a sobra foi para a máquina presa: " + furnace.slots[0]);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(machine).inflate(4)).isEmpty(),
                    "a sobra caiu no mundo");
        });
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, LYING);
                    linkInNetwork(helper, router, NORMAL);
                    // O que mente vem primeiro: a sobra dele é que precisa de resgate.
                    router.configureFace(EditTarget.group(groupOf(router, helper.absolutePos(LYING))), ResourceType.ITEM,
                            Direction.UP, PortMode.INSERT, 5, RedstoneMode.IGNORE);
                    profile(helper, router, NORMAL, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(countOf(helper, NORMAL, Items.STONE), 10, "pedras no remoto normal"))
                .thenExecute(() -> {
                    helper.assertTrue(furnace.slots[1].isEmpty(), "sobrou pedra na saída da máquina presa");
                    TestMachines.reset(machine);
                })
                .thenSucceed();
    }

    /** Um destino remoto sem espaço, depois de recusas seguidas, conta como cheio; a presa que só extrai não conta. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fullRemoteDestinationCounts(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(NORMAL, Blocks.CHEST);
        Container full = container(helper, NORMAL);
        for (int slot = 0; slot < full.getContainerSize(); slot++) {
            full.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        container(helper, CHEST).setItem(0, new ItemStack(Items.DIAMOND, 10));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, NORMAL);
                    profile(helper, router, NORMAL, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(
                        NodeProbe.fullDestinations(router, ResourceType.ITEM, helper.getLevel().getServer().getTickCount()),
                        1, "destinos cheios"))
                .thenExecute(() -> helper.assertValueEqual(count(helper, CHEST), 10, "os diamantes saíram do baú preso"))
                .thenSucceed();
    }

    /**
     * Contar os destinos cheios só lê: um nó cuja face acabou de ganhar o modo Insere, e que o motor
     * ainda não leu, não ganha porta por causa da contagem.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fullDestinationsDoesNotCreatePorts(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    helper.assertValueEqual(NetworkManager.get().destinationSleepInterval(router, ResourceType.ITEM, Direction.UP),
                            -1, "porta antes da contagem");
                    helper.assertValueEqual(
                            NodeProbe.fullDestinations(router, ResourceType.ITEM, helper.getLevel().getServer().getTickCount()),
                            0, "destinos cheios");
                    helper.assertValueEqual(NetworkManager.get().destinationSleepInterval(router, ResourceType.ITEM, Direction.UP),
                            -1, "porta criada pela contagem");
                })
                .thenSucceed();
    }

    /**
     * O Vinculador no modo Máquinas, com uma rede ativa utilizável, vincula uma fornalha pelo clique: o
     * grupo novo nasce com as seis faces de todos os tipos carregados sem rede.
     */
    @GameTest(template = "empty")
    public static void linkerCreatesGroupWithoutNetwork(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        activeNetworkOf(helper, player, "ativa do clique");
                        router.setRangeUpgrade(rangeUpgrade());
                        click(helper, player, ROUTER, false);
                        helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(), RangeLinks.Status.LINKED,
                                "clique na fornalha");
                        assertProfileNetwork(helper, router, FURNACE_A, null, "perfil da fornalha");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** O mesmo pela área: o grupo do barril, vinculado pelo Vincular da tela, também nasce sem rede. */
    @GameTest(template = "empty")
    public static void linkerAreaCreatesGroupWithoutNetwork(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        activeNetworkOf(helper, player, "ativa da área");
                        router.setRangeUpgrade(rangeUpgrade());
                        click(helper, player, ROUTER, false);
                        BlockPos barrel = helper.absolutePos(BARREL);
                        LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.ITEM));
                        LinkerItem.setArea(linker, LinkerArea.firstCorner(helper.getLevel().dimension(), barrel)
                                .withSecond(barrel));
                        RangeLinks.Result area = RangeLinks.linkArea(player, linker);
                        helper.assertTrue(area.ok() && area.linked() == 1, "área do barril: " + area);
                        assertProfileNetwork(helper, router, BARREL, null, "perfil do barril (área)");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Escolher Insere no perfil mantém todas as faces e tipos sem rede, mesmo com rede ativa.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void groupModeLeavesAllFacesWithoutNetwork(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    ItemStack linker = machinesLinker();
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        UUID active = activeNetworkOf(helper, player, "ativa do perfil");
                        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
                        click(helper, player, ROUTER, false);
                        helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(), RangeLinks.Status.LINKED,
                                "clique na fornalha");
                        int group = groupOf(router, helper.absolutePos(FURNACE_A));
                        assertProfileNetwork(helper, router, FURNACE_A, null, "perfil recém-criado");
                        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(menu.containerId,
                                group, BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE))), "não escolheu o grupo");
                        helper.assertTrue(ModPayloads.handleSetFace(player, new SetFacePayload(menu.containerId,
                                ResourceType.ITEM, Direction.UP, PortMode.INSERT, 0, RedstoneMode.IGNORE)),
                                "recusou a face do grupo");
                        for (ResourceType type : LoadedTypes.LIST) {
                            for (Direction face : Direction.values()) {
                                UUID expected = null;
                                helper.assertTrue(Objects.equals(
                                        router.face(EditTarget.group(group), type, face).network(), expected),
                                        "rede de " + type + " em " + face + ": esperava " + expected);
                            }
                        }
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ tirar a rede das faces sem modo

    private static final ResourceLocation FURNACE_ID = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);
    private static final int OTHER_CONTAINER_ID = 54;
    private static final int TABLET_ID = 86;

    /** As faces de um alvo: os tipos carregados nos seis lados. */
    private static int allFaces() {
        return LoadedTypes.LIST.size() * Direction.values().length;
    }

    /**
     * Um grupo antigo, de antes da correção: o upgrade e as duas fornalhas vinculadas com todas as faces de
     * todos os tipos na rede {@code network}. Devolve o id do grupo.
     */
    private static int oldGroup(GameTestHelper helper, RouterBlockEntity router, UUID network) {
        router.setRangeUpgrade(rangeUpgrade());
        helper.assertTrue(router.linkMachine(helper.absolutePos(FURNACE_A), network), "não vinculou a fornalha A");
        helper.assertTrue(router.linkMachine(helper.absolutePos(FURNACE_B), network), "não vinculou a fornalha B");
        return groupOf(router, helper.absolutePos(FURNACE_A));
    }

    private static int clearIdle(ServerPlayer player, int containerId, boolean undo) {
        return ModPayloads.handleClearIdleNetworks(player, new ClearIdleNetworksPayload(containerId, undo));
    }

    private static void selectGroup(GameTestHelper helper, ServerPlayer player, int containerId, int group) {
        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(containerId, group, FURNACE_ID)),
                "não escolheu o grupo");
    }

    /** Confere a rede de todas as faces do alvo: {@code expected} dá a esperada de cada uma. */
    private static void assertNetworks(GameTestHelper helper, RouterBlockEntity router, EditTarget target,
            BiFunction<ResourceType, Direction, UUID> expected, String what) {
        for (ResourceType type : LoadedTypes.LIST) {
            for (Direction side : Direction.values()) {
                UUID want = expected.apply(type, side);
                UUID actual = router.faceNetwork(target, type, side);
                helper.assertTrue(Objects.equals(actual, want),
                        what + " (" + type + ", " + side + "): esperava " + want + ", veio " + actual);
            }
        }
    }

    private static RouterMenu openMenuAs(RouterBlockEntity router, ServerPlayer player, int id) {
        RouterMenu menu = new RouterMenu(id, player.getInventory(), router, RouterSnapshot.capture(router, player));
        player.containerMenu = menu;
        return menu;
    }

    private static void forgetPlayer(GameTestHelper helper, ServerPlayer player) {
        IdleNetworkUndo.forget(player.getUUID());
        removePlayer(helper, player);
    }

    /**
     * No perfil de um grupo antigo (rede em tudo), o botão pelo pacote tira a rede só das faces com modo
     * Nenhum, de todos os tipos, numa mudança só; a face que insere fica com a rede e a presa não muda. O
     * Desfazer fica guardado. Do aviso, o teste confere só a montagem ({@link ModPayloads#idleClearedNotice},
     * o mesmo método que o handler usa: leva o {@code RESTORE_IDLE_NETWORKS} e o container); o envio não é
     * observável aqui, porque o jogador de teste não negocia o canal, e fica com o e2e. Limpar de novo não
     * acha nada e esquece o Desfazer.
     */
    @GameTest(template = "empty")
    public static void idleClearOnProfileKeepsFacesWithMode(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        UUID network = router.networkId(ResourceType.ITEM);
                        EditTarget group = EditTarget.group(oldGroup(helper, router, network));
                        router.configureFace(group, ResourceType.ITEM, Direction.UP, PortMode.INSERT, 0, RedstoneMode.IGNORE);
                        selectGroup(helper, player, menu.containerId, group.id());
                        int version = router.changeVersion();
                        helper.assertValueEqual(clearIdle(player, menu.containerId, false), allFaces() - 1, "faces limpas");
                        helper.assertValueEqual(router.changeVersion(), version + 1, "mudanças no roteador");
                        assertNetworks(helper, router, group,
                                (type, side) -> type == ResourceType.ITEM && side == Direction.UP ? network : null, "perfil");
                        helper.assertValueEqual(router.face(group, ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT,
                                "modo da face que insere");
                        assertNetworks(helper, router, EditTarget.ATTACHED_TARGET, (type, side) -> network, "presa");
                        helper.assertTrue(IdleNetworkUndo.has(player.getUUID()), "o Desfazer não ficou guardado");
                        PanelNoticePayload notice = ModPayloads.idleClearedNotice(menu.containerId, allFaces() - 1);
                        helper.assertValueEqual(notice.undo(), PanelNoticePayload.Undo.RESTORE_IDLE_NETWORKS, "Desfazer do aviso");
                        helper.assertValueEqual(notice.containerId(), menu.containerId, "containerId do aviso");

                        helper.assertValueEqual(clearIdle(player, menu.containerId, false), 0, "limpou de novo");
                        helper.assertFalse(IdleNetworkUndo.has(player.getUUID()), "o Desfazer ficou depois de limpar de novo");
                    } finally {
                        forgetPlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Numa máquina com exceção, limpar mexe só na exceção dela (a face que extrai fica com a rede) e o perfil
     * continua com a rede; numa máquina que segue o perfil não há o que limpar.
     */
    @GameTest(template = "empty")
    public static void idleClearOnException(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        UUID network = router.networkId(ResourceType.ITEM);
                        oldGroup(helper, router, network);
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        BlockPos b = helper.absolutePos(FURNACE_B);
                        EditTarget machineA = EditTarget.machine(router.remoteMachines().index(a));
                        EditTarget machineB = EditTarget.machine(router.remoteMachines().index(b));
                        router.configureFace(machineA, ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT, 0,
                                RedstoneMode.IGNORE);
                        helper.assertTrue(router.remoteMachines().machine(machineA.id()).hasException(), "sem exceção");
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.machine(menu.containerId, machineA.id(), a)), "não escolheu a fornalha A");
                        helper.assertValueEqual(clearIdle(player, menu.containerId, false), allFaces() - 1, "faces limpas");
                        assertNetworks(helper, router, machineA,
                                (type, side) -> type == ResourceType.ITEM && side == Direction.NORTH ? network : null,
                                "exceção");
                        assertProfileNetwork(helper, router, FURNACE_A, network, "perfil");

                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.machine(menu.containerId, machineB.id(), b)), "não escolheu a fornalha B");
                        helper.assertValueEqual(clearIdle(player, menu.containerId, false), 0, "limpou quem segue o perfil");
                        helper.assertFalse(router.remoteMachines().machine(machineB.id()).hasException(),
                                "a fornalha B ganhou exceção");
                        assertNetworks(helper, router, machineB, (type, side) -> network, "fornalha B");
                    } finally {
                        forgetPlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** Na máquina presa o botão não aparece e o pacote não faz nada, nem guarda Desfazer. */
    @GameTest(template = "empty")
    public static void idleClearOnAttachedDoesNothing(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        UUID network = router.networkId(ResourceType.ITEM);
                        oldGroup(helper, router, network);
                        int version = router.changeVersion();
                        helper.assertValueEqual(clearIdle(player, menu.containerId, false), 0, "faces limpas na presa");
                        helper.assertValueEqual(router.changeVersion(), version, "mudou o roteador");
                        assertNetworks(helper, router, EditTarget.ATTACHED_TARGET, (type, side) -> network, "presa");
                        assertProfileNetwork(helper, router, FURNACE_A, network, "perfil");
                        helper.assertFalse(IdleNetworkUndo.has(player.getUUID()), "guardou Desfazer na presa");
                    } finally {
                        forgetPlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * O Desfazer devolve a rede às faces que continuam sem modo e sem rede, e não sobrescreve a face que
     * entrou em outra rede nem a que ganhou modo depois; vale uma vez só.
     */
    @GameTest(template = "empty")
    public static void idleUndoRestoresWithoutOverwriting(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        UUID network = router.networkId(ResourceType.ITEM);
                        UUID other = NetworkSavedData.get(helper.getLevel().getServer())
                                .create(UUID.randomUUID(), "outra do desfazer").id();
                        EditTarget group = EditTarget.group(oldGroup(helper, router, network));
                        selectGroup(helper, player, menu.containerId, group.id());
                        helper.assertValueEqual(clearIdle(player, menu.containerId, false), allFaces(), "faces limpas");
                        router.setFaceNetwork(group, ResourceType.ITEM, Direction.DOWN, other);
                        router.configureFace(group, ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT, 0,
                                RedstoneMode.IGNORE);
                        int version = router.changeVersion();
                        helper.assertValueEqual(clearIdle(player, menu.containerId, true), allFaces() - 2, "faces devolvidas");
                        helper.assertValueEqual(router.changeVersion(), version + 1, "mudanças no Desfazer");
                        assertNetworks(helper, router, group, (type, side) -> type != ResourceType.ITEM ? network
                                : side == Direction.DOWN ? other : side == Direction.NORTH ? null : network, "perfil");
                        helper.assertValueEqual(router.face(group, ResourceType.ITEM, Direction.NORTH).mode(),
                                PortMode.EXTRACT, "modo mudado depois");
                        helper.assertValueEqual(clearIdle(player, menu.containerId, true), -1, "desfez duas vezes");
                    } finally {
                        forgetPlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** Pelo Tablet, com o nó escolhido e o grupo como alvo, limpar e desfazer funcionam como na tela. */
    @GameTest(template = "empty")
    public static void idleClearThroughTablet(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = linkerPlayer(helper, ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        NodeIndex.placedBy(router, player.getUUID());
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        UUID network = router.networkId(ResourceType.ITEM);
                        int group = oldGroup(helper, router, network);
                        TabletMenu menu = new TabletMenu(TABLET_ID, player.getInventory(), player);
                        player.containerMenu = menu;
                        helper.assertTrue(TabletPayloads.handleSelectNode(player, new TabletSelectNodePayload(TABLET_ID,
                                Optional.of(NodeKey.of(router.getLevel(), router.getBlockPos())))), "não escolheu o nó");
                        selectGroup(helper, player, TABLET_ID, group);
                        helper.assertValueEqual(clearIdle(player, TABLET_ID, false), allFaces(), "faces limpas pelo Tablet");
                        assertProfileNetwork(helper, router, FURNACE_A, null, "perfil limpo");
                        helper.assertValueEqual(clearIdle(player, TABLET_ID, true), allFaces(), "faces devolvidas pelo Tablet");
                        assertProfileNetwork(helper, router, FURNACE_A, network, "perfil devolvido");
                    } finally {
                        forgetPlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * O pacote do jogador A com o container do menu aberto do jogador B é ignorado, e o do próprio menu de A
     * já fechado também; o mesmo container, mandado por B, limpa.
     */
    @GameTest(template = "empty")
    public static void idleClearIgnoresOtherOrClosedContainer(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    ServerPlayer other = TestPlayers.inLevel(helper);
                    try {
                        UUID network = router.networkId(ResourceType.ITEM);
                        int group = oldGroup(helper, router, network);
                        other.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
                        RouterMenu otherMenu = openMenuAs(router, other, OTHER_CONTAINER_ID);
                        selectGroup(helper, other, otherMenu.containerId, group);
                        selectGroup(helper, player, menu.containerId, group);
                        helper.assertValueEqual(clearIdle(player, otherMenu.containerId, false), -1,
                                "container do menu de outro jogador");
                        assertProfileNetwork(helper, router, FURNACE_A, network, "perfil depois do container de B");
                        player.doCloseContainer();
                        helper.assertValueEqual(clearIdle(player, menu.containerId, false), -1, "container fechado");
                        assertProfileNetwork(helper, router, FURNACE_A, network, "perfil depois do fechado");
                        helper.assertValueEqual(clearIdle(other, otherMenu.containerId, false), allFaces(),
                                "o mesmo container, pelo dono");
                    } finally {
                        forgetPlayer(helper, other);
                        forgetPlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Fechar o menu esquece o Desfazer ({@code removed}): numa tela aberta de novo, com o mesmo id, no mesmo
     * grupo, o Desfazer não faz nada.
     */
    @GameTest(template = "empty")
    public static void idleUndoAfterClosingDoesNothing(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        UUID network = router.networkId(ResourceType.ITEM);
                        int group = oldGroup(helper, router, network);
                        selectGroup(helper, player, menu.containerId, group);
                        helper.assertValueEqual(clearIdle(player, menu.containerId, false), allFaces(), "faces limpas");
                        player.doCloseContainer();
                        helper.assertFalse(IdleNetworkUndo.has(player.getUUID()), "o Desfazer ficou depois de fechar");
                        RouterMenu reopened = openMenuAs(router, player, menu.containerId);
                        selectGroup(helper, player, reopened.containerId, group);
                        helper.assertValueEqual(clearIdle(player, reopened.containerId, true), -1, "desfez depois de fechar");
                        assertProfileNetwork(helper, router, FURNACE_A, null, "perfil");
                    } finally {
                        forgetPlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Um destino remoto cheio sai da contagem quando a face vai para Nenhum, assim que as rotas são
     * remontadas, mesmo com o sono do destino ainda correndo.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fullDestinationLeavesWhenModeGoesNone(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(NORMAL, Blocks.CHEST);
        Container full = container(helper, NORMAL);
        for (int slot = 0; slot < full.getContainerSize(); slot++) {
            full.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        container(helper, CHEST).setItem(0, new ItemStack(Items.DIAMOND, 10));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, NORMAL);
                    profile(helper, router, NORMAL, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(fullNow(helper, router), 1, "destinos cheios"))
                .thenExecute(() -> {
                    long now = helper.getLevel().getServer().getTickCount();
                    helper.assertTrue(NetworkManager.get().sleepForTests(router, helper.absolutePos(NORMAL),
                            ResourceType.ITEM, Direction.UP, now, 1000), "sem a porta do destino remoto");
                    profile(helper, router, NORMAL, Direction.UP, PortMode.NONE);
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertValueEqual(fullNow(helper, router), 0, "destinos cheios depois do Nenhum"))
                .thenSucceed();
    }

    private static int fullNow(GameTestHelper helper, RouterBlockEntity router) {
        return NodeProbe.fullDestinations(router, ResourceType.ITEM, helper.getLevel().getServer().getTickCount());
    }

    /**
     * O Desfazer não vale num grupo recriado: limpar o grupo, desvincular as duas fornalhas e vincular uma de
     * novo cria outro grupo do mesmo bloco, sem rede; escolhido ele, o Desfazer não devolve nada.
     */
    @GameTest(template = "empty")
    public static void idleUndoIgnoresRecreatedGroup(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        UUID network = router.networkId(ResourceType.ITEM);
                        int group = oldGroup(helper, router, network);
                        selectGroup(helper, player, menu.containerId, group);
                        helper.assertValueEqual(clearIdle(player, menu.containerId, false), allFaces(), "faces limpas");
                        helper.assertTrue(router.unlinkMachine(helper.absolutePos(FURNACE_A)), "não desvinculou A");
                        helper.assertTrue(router.unlinkMachine(helper.absolutePos(FURNACE_B)), "não desvinculou B");
                        helper.assertTrue(router.linkMachine(helper.absolutePos(FURNACE_A)), "não vinculou A de novo");
                        int recreated = groupOf(router, helper.absolutePos(FURNACE_A));
                        helper.assertTrue(recreated != group, "o grupo recriado tem o mesmo id");
                        selectGroup(helper, player, menu.containerId, recreated);
                        helper.assertValueEqual(clearIdle(player, menu.containerId, true), -1, "desfez no grupo recriado");
                        assertProfileNetwork(helper, router, FURNACE_A, null, "perfil do grupo recriado");
                    } finally {
                        forgetPlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** Uma rede apagada depois do limpar não volta pelo Desfazer: a face fica sem rede. */
    @GameTest(template = "empty")
    public static void idleUndoSkipsDeletedNetwork(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
                        UUID doomed = data.create(UUID.randomUUID(), "apagada antes do desfazer").id();
                        int group = oldGroup(helper, router, doomed);
                        selectGroup(helper, player, menu.containerId, group);
                        helper.assertValueEqual(clearIdle(player, menu.containerId, false), allFaces(), "faces limpas");
                        helper.assertTrue(data.remove(doomed), "não apagou a rede");
                        helper.assertValueEqual(clearIdle(player, menu.containerId, true), 0, "faces devolvidas");
                        assertProfileNetwork(helper, router, FURNACE_A, null, "perfil");
                    } finally {
                        forgetPlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * O Desfazer só vale no alvo do limpar: com uma máquina do grupo escolhida, não devolve nada; de volta ao
     * grupo, devolve.
     */
    @GameTest(template = "empty")
    public static void idleUndoNeedsTheSameTarget(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        UUID network = router.networkId(ResourceType.ITEM);
                        int group = oldGroup(helper, router, network);
                        selectGroup(helper, player, menu.containerId, group);
                        helper.assertValueEqual(clearIdle(player, menu.containerId, false), allFaces(), "faces limpas");
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.machine(menu.containerId,
                                router.remoteMachines().index(a), a)), "não escolheu a fornalha A");
                        helper.assertValueEqual(clearIdle(player, menu.containerId, true), -1, "desfez em outro alvo");
                        assertProfileNetwork(helper, router, FURNACE_A, null, "perfil com outro alvo");
                        selectGroup(helper, player, menu.containerId, group);
                        helper.assertValueEqual(clearIdle(player, menu.containerId, true), allFaces(),
                                "desfez de volta ao grupo");
                        assertProfileNetwork(helper, router, FURNACE_A, network, "perfil devolvido");
                    } finally {
                        forgetPlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** Pelo Tablet, com o nó fora do alcance (painel só leitura), limpar não faz nada nem guarda Desfazer. */
    @GameTest(template = "empty")
    public static void idleClearReadOnlyTabletDoesNothing(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = linkerPlayer(helper, ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        NodeIndex.placedBy(router, player.getUUID());
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        UUID network = router.networkId(ResourceType.ITEM);
                        int group = oldGroup(helper, router, network);
                        TabletMenu menu = new TabletMenu(TABLET_ID, player.getInventory(), player);
                        player.containerMenu = menu;
                        helper.assertTrue(TabletPayloads.handleSelectNode(player, new TabletSelectNodePayload(TABLET_ID,
                                Optional.of(NodeKey.of(router.getLevel(), router.getBlockPos())))), "não escolheu o nó");
                        selectGroup(helper, player, TABLET_ID, group);
                        player.moveTo(Vec3.atCenterOf(router.getBlockPos()).add(2000, 0, 0));
                        helper.assertFalse(menu.canEdit(player), "editável fora do alcance");
                        helper.assertValueEqual(clearIdle(player, TABLET_ID, false), -1, "limpou fora do alcance");
                        helper.assertFalse(IdleNetworkUndo.has(player.getUUID()), "guardou Desfazer fora do alcance");
                        assertProfileNetwork(helper, router, FURNACE_A, network, "perfil");
                    } finally {
                        forgetPlayer(helper, player);
                    }
                })
                .thenSucceed();
    }
}
