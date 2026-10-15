package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Regra D1: escolher Extrai, Insere ou Armazém numa face sem rede põe a face na rede ativa do jogador,
 * na mesma edição; sem rede ativa utilizável, a face fica só com o modo. O aviso com Desfazer
 * ({@link PanelNoticePayload}) tem o formato testado aqui.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ActiveNetworkModeGameTests {
    private static final int CONTAINER_ID = 61;
    private static final BlockPos FURNACE_A = new BlockPos(0, 1, 0);
    private static final BlockPos FURNACE_B = new BlockPos(2, 1, 0);

    private static RouterBlockEntity chestWithRouter(GameTestHelper helper, RouterTier tier) {
        BlockPos machine = new BlockPos(1, 1, 1);
        helper.setBlock(machine, Blocks.CHEST);
        helper.setBlock(machine.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(machine.above());
    }

    @SuppressWarnings("removal")
    private static RouterMenu openMenu(GameTestHelper helper, RouterBlockEntity router, ServerPlayer[] out) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        RouterMenu menu = new RouterMenu(CONTAINER_ID, player.getInventory(), router, RouterSnapshot.capture(router, player));
        player.containerMenu = menu;
        out[0] = player;
        return menu;
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static NetworkSavedData data(GameTestHelper helper) {
        return NetworkSavedData.get(helper.getLevel().getServer());
    }

    private static SetFacePayload setFace(RouterMenu menu, Direction face, PortMode mode) {
        return new SetFacePayload(menu.containerId, ResourceType.ITEM, face, mode, 0, RedstoneMode.IGNORE);
    }

    private static void waitLoaded(GameTestHelper helper, RouterBlockEntity router) {
        helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
    }

    /** Roteador Avançado com o Upgrade de Alcance e duas fornalhas vinculadas (um grupo só). */
    private static RouterBlockEntity routerWithFurnaces(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.setBlock(FURNACE_A, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
        helper.setBlock(FURNACE_B, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
        return router;
    }

    private static void linkFurnaces(GameTestHelper helper, RouterBlockEntity router) {
        router.setRangeUpgrade(new ItemStack(ModItems.RANGE_UPGRADE.get()));
        helper.assertTrue(router.linkMachine(helper.absolutePos(FURNACE_A)), "não vinculou a fornalha A");
        helper.assertTrue(router.linkMachine(helper.absolutePos(FURNACE_B)), "não vinculou a fornalha B");
    }

    @GameTest(template = "empty")
    public static void modeJoinsActiveNetworkInOneChange(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.BASIC);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        WaNetwork active = data(helper).create(player.getUUID(), "ativa-d1");
                        data(helper).setActiveNetwork(player.getUUID(), active.id());
                        helper.assertTrue(router.faceNetwork(ResourceType.ITEM, Direction.UP) == null,
                                "a face já começou numa rede");

                        int before = router.changeVersion();
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.INSERT)),
                                "recusou o modo");
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT, "modo");
                        helper.assertTrue(Objects.equals(router.faceNetwork(ResourceType.ITEM, Direction.UP), active.id()),
                                "a face não entrou na rede ativa");
                        helper.assertValueEqual(router.changeVersion() - before, 1, "mudanças do roteador");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void modeWithoutActiveNetworkLeavesFaceWithoutNetwork(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.BASIC);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        data(helper).setActiveNetwork(player.getUUID(), null);
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.INSERT)),
                                "recusou o modo");
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT, "modo");
                        helper.assertTrue(router.faceNetwork(ResourceType.ITEM, Direction.UP) == null,
                                "a face ganhou uma rede sem rede ativa");
                        helper.assertTrue(data(helper).activeNetwork(player.getUUID()) == null,
                                "o servidor criou uma rede ativa");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void modeIgnoresActiveNetworkThePlayerCannotUse(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.BASIC);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        WaNetwork others = data(helper).create(UUID.randomUUID(), "de-outro-d1");
                        data(helper).setActiveNetwork(player.getUUID(), others.id());
                        helper.assertFalse(others.canUse(player), "o jogador de teste podia usar a rede de outro");
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.EXTRACT)),
                                "recusou o modo");
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.EXTRACT, "modo");
                        helper.assertTrue(router.faceNetwork(ResourceType.ITEM, Direction.UP) == null,
                                "a face entrou numa rede que o jogador não pode usar");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void modeKeepsTheNetworkOfAFaceThatHasOne(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.BASIC);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        WaNetwork active = data(helper).create(player.getUUID(), "ativa-d1");
                        WaNetwork own = data(helper).create(player.getUUID(), "da-face-d1");
                        data(helper).setActiveNetwork(player.getUUID(), active.id());
                        router.setFaceNetwork(ResourceType.ITEM, Direction.UP, own.id());

                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.INSERT)),
                                "recusou o modo");
                        helper.assertTrue(Objects.equals(router.faceNetwork(ResourceType.ITEM, Direction.UP), own.id()),
                                "trocar o modo mudou a rede da face");
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.EXTRACT)),
                                "recusou o segundo modo");
                        helper.assertTrue(Objects.equals(router.faceNetwork(ResourceType.ITEM, Direction.UP), own.id()),
                                "trocar de modo de novo mudou a rede");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void backToNoneKeepsTheNetwork(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.BASIC);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        WaNetwork active = data(helper).create(player.getUUID(), "ativa-d1");
                        data(helper).setActiveNetwork(player.getUUID(), active.id());
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.BOTH)),
                                "recusou o Armazém");
                        helper.assertTrue(Objects.equals(router.faceNetwork(ResourceType.ITEM, Direction.UP), active.id()),
                                "o Armazém não pôs a face na rede ativa");

                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.NONE)),
                                "recusou o Nenhum");
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.NONE, "modo");
                        helper.assertTrue(Objects.equals(router.faceNetwork(ResourceType.ITEM, Direction.UP), active.id()),
                                "voltar para Nenhum tirou a rede");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void rangeTargetsJoinInOneEdit(GameTestHelper helper) {
        RouterBlockEntity router = routerWithFurnaces(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkFurnaces(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        WaNetwork active = data(helper).create(player.getUUID(), "ativa-d1");
                        data(helper).setActiveNetwork(player.getUUID(), active.id());
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        BlockPos b = helper.absolutePos(FURNACE_B);
                        int indexB = router.remoteMachines().index(b);
                        int group = router.remoteMachines().machine(router.remoteMachines().index(a)).group();
                        helper.assertTrue(router.face(EditTarget.group(group), ResourceType.ITEM, Direction.UP).network() == null,
                                "o grupo já começou com rede");

                        // Grupo: o perfil ganha modo e rede de uma vez; ninguém ganha exceção.
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.group(menu.containerId, group,
                                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE))),
                                "não escolheu o grupo");
                        int before = router.changeVersion();
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.INSERT)),
                                "recusou a face do grupo");
                        helper.assertValueEqual(router.changeVersion() - before, 1, "mudanças pelo grupo");
                        helper.assertValueEqual(router.face(EditTarget.group(group), ResourceType.ITEM, Direction.UP).mode(),
                                PortMode.INSERT, "modo do perfil");
                        helper.assertTrue(Objects.equals(
                                router.face(EditTarget.group(group), ResourceType.ITEM, Direction.UP).network(), active.id()),
                                "o perfil não entrou na rede ativa");
                        helper.assertFalse(router.remoteMachines().machine(indexB).hasException(),
                                "editar o perfil criou exceção");

                        // Máquina: uma exceção só, com o modo e a rede (a face DOWN do perfil continua sem os dois).
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.machine(menu.containerId, indexB, b)), "não escolheu a máquina");
                        before = router.changeVersion();
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.DOWN, PortMode.EXTRACT)),
                                "recusou a face da máquina");
                        helper.assertValueEqual(router.changeVersion() - before, 1, "mudanças pela máquina");
                        helper.assertTrue(router.remoteMachines().machine(indexB).hasException(), "não criou a exceção");
                        helper.assertValueEqual(router.face(EditTarget.machine(indexB), ResourceType.ITEM, Direction.DOWN).mode(),
                                PortMode.EXTRACT, "modo da exceção");
                        helper.assertTrue(Objects.equals(
                                router.face(EditTarget.machine(indexB), ResourceType.ITEM, Direction.DOWN).network(),
                                active.id()), "a exceção não entrou na rede ativa");
                        int exceptions = 0;
                        for (var machine : router.remoteMachines().machines()) {
                            exceptions += machine.hasException() ? 1 : 0;
                        }
                        helper.assertValueEqual(exceptions, 1, "exceções criadas");
                        helper.assertTrue(router.face(EditTarget.group(group), ResourceType.ITEM, Direction.DOWN).network() == null,
                                "a exceção da máquina mexeu no perfil do grupo");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void noticePayloadRoundTrips(GameTestHelper helper) {
        PanelNoticePayload sent = new PanelNoticePayload(
                Component.translatable("gui.wirelessautomate.panel.notice.joined", "Frente", "Rede"),
                PanelNoticePayload.Undo.CLEAR_FACE_NETWORK, 9, ResourceType.FLUID, Direction.WEST);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        PanelNoticePayload.STREAM_CODEC.encode(buf, sent);
        PanelNoticePayload read = PanelNoticePayload.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(read.undo(), PanelNoticePayload.Undo.CLEAR_FACE_NETWORK, "desfazer");
        helper.assertValueEqual(read.containerId(), 9, "containerId");
        helper.assertValueEqual(read.resource(), ResourceType.FLUID, "tipo");
        helper.assertValueEqual(read.face(), Direction.WEST, "face");
        helper.assertTrue(read.message().getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("gui.wirelessautomate.panel.notice.joined"), "mensagem");
        helper.succeed();
    }
}
