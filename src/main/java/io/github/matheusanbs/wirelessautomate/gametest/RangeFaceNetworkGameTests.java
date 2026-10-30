package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.FURNACE_A;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.FURNACE_B;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.ClearIdleNetworksPayload;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.SetNetworkPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * A rede de uma face de máquina remota trocada e tirada pela tela do roteador, sempre pelo handler do
 * {@link SetNetworkPayload} (e do "Tirar redes"), nos caminhos em que a mudança poderia não pegar: índice da
 * máquina que muda, rede apagada, máquina isolada (com exceção) e o grupo dela, rede pública de outro dono,
 * máquina girada e o alvo trocado antes do snapshot.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeFaceNetworkGameTests {
    /** Roteador Avançado no baú, com as fornalhas A (frente Leste) e B (frente Sul) em volta, sem vincular. */
    private static RouterBlockEntity router(GameTestHelper helper) {
        RouterBlockEntity router = RangeTestSupport.chestWithRouter(helper, RouterTier.ADVANCED);
        helper.setBlock(FURNACE_A, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
        helper.setBlock(FURNACE_B, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
        return router;
    }

    /** Liga o Upgrade de Alcance e abre a tela para um jogador de teste perto do roteador. */
    private static RouterMenu open(GameTestHelper helper, RouterBlockEntity router, ServerPlayer[] out) {
        router.setRangeUpgrade(RangeTestSupport.rangeUpgrade());
        helper.assertTrue(router.rangeActive(), "o upgrade de alcance não vale no Avançado");
        return RangeTestSupport.openMenu(helper, router, out);
    }

    private static SetNetworkPayload set(RouterMenu menu, Direction face, @Nullable UUID network) {
        return new SetNetworkPayload(menu.containerId, ResourceType.ITEM, face, Optional.ofNullable(network));
    }

    private static boolean selectMachine(ServerPlayer player, RouterMenu menu, RouterBlockEntity router, BlockPos pos) {
        return ModPayloads.handleSelectTarget(player,
                SelectTargetPayload.machine(menu.containerId, router.remoteMachines().index(pos), pos));
    }

    private static boolean selectGroup(ServerPlayer player, RouterMenu menu, RouterBlockEntity router, BlockPos pos) {
        RemoteMachines remotes = router.remoteMachines();
        int group = remotes.machine(remotes.index(pos)).group();
        return ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(menu.containerId, group,
                BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE)));
    }

    private static EditTarget machine(RouterBlockEntity router, BlockPos pos) {
        return EditTarget.machine(router.remoteMachines().index(pos));
    }

    private static EditTarget group(RouterBlockEntity router, BlockPos pos) {
        RemoteMachines remotes = router.remoteMachines();
        return EditTarget.group(remotes.machine(remotes.index(pos)).group());
    }

    /** A rede que a tela mostraria agora na face {@code face} do alvo do menu (o snapshot do servidor). */
    private static @Nullable UUID shown(RouterBlockEntity router, ServerPlayer player, RouterMenu menu, Direction face) {
        return RouterSnapshot.capture(router, player, menu.validTarget(), menu.selectedFace())
                .network(ResourceType.ITEM, face).orElse(null);
    }

    private static void assertNetwork(GameTestHelper helper, @Nullable UUID actual, @Nullable UUID expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperava " + expected + ", veio " + actual);
    }

    /** Desvincular outra máquina antes da editada muda o índice dela: a rede vai para a mesma máquina. */
    @GameTest(template = "empty")
    public static void networkFollowsTheMachineAfterIndexShift(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        helper.startSequence()
                .thenWaitUntil(() -> RangeTestSupport.waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = open(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        BlockPos b = helper.absolutePos(FURNACE_B);
                        helper.assertTrue(router.linkMachine(a) && router.linkMachine(b), "não vinculou");
                        UUID own = NetworkSavedData.get(player.server).create(player.getUUID(), "indice").id();
                        helper.assertTrue(selectMachine(player, menu, router, b), "não escolheu a B");
                        helper.assertTrue(ModPayloads.handleSetNetwork(player, set(menu, Direction.NORTH, own)), "recusou pôr");
                        helper.assertTrue(router.unlinkMachine(a), "não desvinculou a A");
                        helper.assertTrue(ModPayloads.handleSetNetwork(player, set(menu, Direction.NORTH, null)),
                                "recusou tirar depois do índice mudar");
                        assertNetwork(helper, router.faceNetwork(machine(router, b), ResourceType.ITEM, Direction.NORTH), null,
                                "a B continuou na rede");
                        assertNetwork(helper, shown(router, player, menu, Direction.NORTH), null, "a tela mostra a rede");
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                })
                .thenSucceed();
    }

    /** Uma face numa rede apagada sai dela pelo "Sem rede" (a tela já mostra "Sem rede", mas a face guarda o UUID). */
    @GameTest(template = "empty")
    public static void deletedNetworkLeavesTheGroupFace(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        helper.startSequence()
                .thenWaitUntil(() -> RangeTestSupport.waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = open(helper, router, holder);
                    ServerPlayer player = holder[0];
                    NetworkSavedData data = NetworkSavedData.get(player.server);
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        helper.assertTrue(router.linkMachine(a), "não vinculou");
                        UUID gone = data.create(player.getUUID(), "apagada").id();
                        router.setFaceNetwork(group(router, a), ResourceType.ITEM, Direction.UP, gone);
                        helper.assertTrue(data.remove(gone), "não apagou a rede");
                        helper.assertTrue(selectGroup(player, menu, router, a), "não escolheu o grupo");
                        assertNetwork(helper, shown(router, player, menu, Direction.UP), null, "a tela não mostra Sem rede");
                        helper.assertTrue(ModPayloads.handleSetNetwork(player, set(menu, Direction.UP, null)),
                                "recusou tirar a rede apagada");
                        assertNetwork(helper, router.faceNetwork(group(router, a), ResourceType.ITEM, Direction.UP), null,
                                "o perfil guardou a rede apagada");
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                })
                .thenSucceed();
    }

    /** Uma máquina que entrou isolada (com exceção vazia) troca e tira a rede da face pela tela. */
    @GameTest(template = "empty")
    public static void isolatedMachineSwitchesAndClearsItsNetwork(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        helper.startSequence()
                .thenWaitUntil(() -> RangeTestSupport.waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = open(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        BlockPos b = helper.absolutePos(FURNACE_B);
                        helper.assertTrue(router.linkMachineIsolated(a) && router.linkMachineIsolated(b), "não vinculou");
                        helper.assertTrue(router.remoteMachines().machine(router.remoteMachines().index(b)).hasException(),
                                "a B não entrou isolada");
                        NetworkSavedData data = NetworkSavedData.get(player.server);
                        UUID x = data.create(player.getUUID(), "x").id();
                        UUID y = data.create(player.getUUID(), "y").id();
                        helper.assertTrue(selectMachine(player, menu, router, b), "não escolheu a B");
                        helper.assertTrue(ModPayloads.handleSetNetwork(player, set(menu, Direction.EAST, x)), "recusou x");
                        assertNetwork(helper, shown(router, player, menu, Direction.EAST), x, "x na tela");
                        helper.assertTrue(ModPayloads.handleSetNetwork(player, set(menu, Direction.EAST, y)), "recusou trocar");
                        assertNetwork(helper, shown(router, player, menu, Direction.EAST), y, "a troca não pegou");
                        helper.assertTrue(ModPayloads.handleSetNetwork(player, set(menu, Direction.EAST, null)), "recusou tirar");
                        assertNetwork(helper, shown(router, player, menu, Direction.EAST), null, "o tirar não pegou");
                        assertNetwork(helper, router.faceNetwork(group(router, b), ResourceType.ITEM, Direction.EAST), null,
                                "a edição da máquina foi para o perfil");
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                })
                .thenSucceed();
    }

    /**
     * Retrato (hipótese "editar no grupo com a máquina em exceção"): a rede trocada no grupo não chega à máquina
     * que entrou isolada pelo Vinculador, que tem exceção própria; só à primeira, que segue o perfil.
     */
    @GameTest(template = "empty")
    public static void groupNetworkDoesNotReachTheIsolatedMachine(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        helper.startSequence()
                .thenWaitUntil(() -> RangeTestSupport.waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = open(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        BlockPos b = helper.absolutePos(FURNACE_B);
                        helper.assertTrue(router.linkMachineIsolated(a) && router.linkMachineIsolated(b), "não vinculou");
                        UUID x = NetworkSavedData.get(player.server).create(player.getUUID(), "grupo").id();
                        helper.assertTrue(selectGroup(player, menu, router, a), "não escolheu o grupo");
                        helper.assertTrue(ModPayloads.handleSetNetwork(player, set(menu, Direction.NORTH, x)), "recusou");
                        assertNetwork(helper, router.faceNetwork(machine(router, a), ResourceType.ITEM, Direction.NORTH), x,
                                "a A (sem exceção) não seguiu o perfil");
                        assertNetwork(helper, router.faceNetwork(machine(router, b), ResourceType.ITEM, Direction.NORTH), null,
                                "a B (isolada) seguiu o perfil");
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                })
                .thenSucceed();
    }

    /**
     * Retrato (hipótese da recusa calada): uma face de remota numa rede pública de outro dono não sai dela pela tela,
     * nem para "Sem rede" nem para uma rede do jogador; o handler devolve {@code false} e nada avisa o jogador.
     */
    @GameTest(template = "empty")
    public static void foreignPublicNetworkHoldsTheRemoteFace(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        helper.startSequence()
                .thenWaitUntil(() -> RangeTestSupport.waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = open(helper, router, holder);
                    ServerPlayer player = holder[0];
                    NetworkSavedData data = NetworkSavedData.get(player.server);
                    WaNetwork foreign = data.create(UUID.randomUUID(), "publica-alheia");
                    data.update(foreign.withPublic(true));
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        helper.assertTrue(router.linkMachine(a), "não vinculou");
                        UUID own = data.create(player.getUUID(), "minha").id();
                        helper.assertTrue(selectGroup(player, menu, router, a), "não escolheu o grupo");
                        helper.assertTrue(ModPayloads.handleSetNetwork(player, set(menu, Direction.UP, foreign.id())),
                                "não pôs na pública");
                        helper.assertFalse(ModPayloads.handleSetNetwork(player, set(menu, Direction.UP, null)),
                                "tirou de uma pública alheia");
                        helper.assertFalse(ModPayloads.handleSetNetwork(player, set(menu, Direction.UP, own)),
                                "trocou de uma pública alheia");
                        assertNetwork(helper, router.faceNetwork(group(router, a), ResourceType.ITEM, Direction.UP),
                                foreign.id(), "a face saiu da pública");
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                        data.remove(foreign.id());
                    }
                })
                .thenSucceed();
    }

    /**
     * "Tirar redes" numa exceção que fica igual ao perfil no meio da varredura (a exceção some e a face seguinte é
     * lida do perfil): as faces sem modo da máquina saem da rede, o perfil não muda e o Desfazer devolve as duas.
     */
    @GameTest(template = "empty")
    public static void clearIdleOnAnExceptionThatMatchesTheProfileMidway(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        helper.startSequence()
                .thenWaitUntil(() -> RangeTestSupport.waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = open(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        BlockPos b = helper.absolutePos(FURNACE_B);
                        helper.assertTrue(router.linkMachine(a) && router.linkMachine(b), "não vinculou");
                        NetworkSavedData data = NetworkSavedData.get(player.server);
                        UUID x = data.create(player.getUUID(), "perfil").id();
                        UUID y = data.create(player.getUUID(), "excecao").id();
                        router.setFaceNetwork(group(router, a), ResourceType.ITEM, Direction.UP, x);
                        router.setFaceNetwork(machine(router, b), ResourceType.ITEM, Direction.DOWN, y);
                        helper.assertTrue(selectMachine(player, menu, router, b), "não escolheu a B");
                        int cleared = ModPayloads.handleClearIdleNetworks(player, new ClearIdleNetworksPayload(menu.containerId, false));
                        helper.assertTrue(cleared == 2, "Tirar redes limpou " + cleared + " faces, esperava 2");
                        assertNetwork(helper, router.faceNetwork(machine(router, b), ResourceType.ITEM, Direction.UP), null,
                                "a B ficou na rede do perfil");
                        assertNetwork(helper, router.faceNetwork(machine(router, b), ResourceType.ITEM, Direction.DOWN), null,
                                "a B ficou na rede da exceção");
                        assertNetwork(helper, router.faceNetwork(group(router, a), ResourceType.ITEM, Direction.UP), x,
                                "o perfil perdeu a rede");
                        int restored = ModPayloads.handleClearIdleNetworks(player, new ClearIdleNetworksPayload(menu.containerId, true));
                        helper.assertTrue(restored == 2, "o Desfazer devolveu " + restored + " faces, esperava 2");
                        assertNetwork(helper, router.faceNetwork(machine(router, b), ResourceType.ITEM, Direction.UP), x,
                                "o Desfazer não devolveu a do perfil");
                        assertNetwork(helper, router.faceNetwork(machine(router, b), ResourceType.ITEM, Direction.DOWN), y,
                                "o Desfazer não devolveu a da exceção");
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                })
                .thenSucceed();
    }

    /** Numa máquina girada (frente Leste), a face canônica editada é a que a tela mostra e o lado guardado é o certo. */
    @GameTest(template = "empty")
    public static void rotatedMachineKeepsTheEditedSide(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        helper.startSequence()
                .thenWaitUntil(() -> RangeTestSupport.waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = open(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        helper.assertTrue(router.linkMachine(a), "não vinculou");
                        UUID own = NetworkSavedData.get(player.server).create(player.getUUID(), "girada").id();
                        helper.assertTrue(selectMachine(player, menu, router, a), "não escolheu a A");
                        // canônica Sul = Trás, seja qual for a frente da máquina
                        helper.assertTrue(ModPayloads.handleSetNetwork(player, set(menu, Direction.SOUTH, own)), "recusou");
                        assertNetwork(helper, shown(router, player, menu, Direction.SOUTH), own, "a tela não mostra na Trás");
                        RemoteMachines remotes = router.remoteMachines();
                        assertNetwork(helper, remotes.effective(remotes.machine(remotes.index(a)))
                                .face(ResourceType.ITEM, RemoteSide.BACK).network(), own, "o lado guardado não é a Trás");
                        helper.assertTrue(ModPayloads.handleSetNetwork(player, set(menu, Direction.SOUTH, null)), "recusou tirar");
                        assertNetwork(helper, shown(router, player, menu, Direction.SOUTH), null, "o tirar não pegou");
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                })
                .thenSucceed();
    }

    /**
     * Retrato (hipótese do alvo trocado antes do snapshot): o {@link SetNetworkPayload} não diz o alvo que a tela
     * mostrava; um pedido que sai antes de o snapshot do alvo novo chegar vale no alvo novo do servidor.
     */
    @GameTest(template = "empty")
    public static void networkEditAfterATargetSwitchLandsOnTheServerTarget(GameTestHelper helper) {
        RouterBlockEntity router = router(helper);
        helper.startSequence()
                .thenWaitUntil(() -> RangeTestSupport.waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = open(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        BlockPos b = helper.absolutePos(FURNACE_B);
                        helper.assertTrue(router.linkMachine(a) && router.linkMachine(b), "não vinculou");
                        UUID own = NetworkSavedData.get(player.server).create(player.getUUID(), "corrida").id();
                        helper.assertTrue(selectGroup(player, menu, router, a), "não escolheu o grupo");
                        // a tela pediu a B e, ainda mostrando o grupo, escolheu a rede
                        helper.assertTrue(selectMachine(player, menu, router, b), "não escolheu a B");
                        helper.assertTrue(ModPayloads.handleSetNetwork(player, set(menu, Direction.NORTH, own)), "recusou");
                        assertNetwork(helper, router.faceNetwork(group(router, a), ResourceType.ITEM, Direction.NORTH), null,
                                "a rede foi para o grupo");
                        assertNetwork(helper, router.faceNetwork(machine(router, b), ResourceType.ITEM, Direction.NORTH), own,
                                "a rede não foi para a B");
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                })
                .thenSucceed();
    }
}
