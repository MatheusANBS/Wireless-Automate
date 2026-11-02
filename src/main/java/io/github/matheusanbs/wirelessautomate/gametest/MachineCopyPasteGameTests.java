package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.MachineClipboard;
import io.github.matheusanbs.wirelessautomate.menu.MachineCopyPaste;
import io.github.matheusanbs.wirelessautomate.menu.MachinePasteUndo;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.MachineCopyPayload;
import io.github.matheusanbs.wirelessautomate.packet.MachinePastePayload;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.RangePayloads;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.netty.buffer.Unpooled;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Etapa 2 do alcance, copiar e colar o perfil por máquina ({@link MachineCopyPayload}, {@link MachinePastePayload}):
 * máquina → máquina como exceção, a exceção igual ao perfil some, a presa sozinha muda o perfil, o grupo inteiro com
 * as exceções contadas, a rede proibida que não entra, outro bloco, o Desfazer (e recusado quando o alvo mudou), o
 * nome da cópia no snapshot e a cópia que some no logout.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class MachineCopyPasteGameTests {
    private static String key(PanelNoticePayload notice) {
        return notice.message().getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    private static int index(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        return router.remoteMachines().index(helper.absolutePos(relative));
    }

    private static RemoteMachine machine(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        return router.remoteMachines().machine(index(helper, router, relative));
    }

    private static void selectMachine(GameTestHelper helper, ServerPlayer player, RouterBlockEntity router,
            BlockPos relative) {
        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.machine(CONTAINER_ID,
                index(helper, router, relative), helper.absolutePos(relative))), "não escolheu " + relative);
    }

    private static void selectGroup(GameTestHelper helper, ServerPlayer player, RouterBlockEntity router, int group) {
        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(CONTAINER_ID, group,
                router.remoteMachines().group(group).block())), "não escolheu o grupo " + group);
    }

    private static void selectAttached(GameTestHelper helper, ServerPlayer player) {
        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.attached(CONTAINER_ID)),
                "não escolheu a presa");
    }

    private static boolean copy(ServerPlayer player) {
        return RangePayloads.handleMachineCopy(player, new MachineCopyPayload(CONTAINER_ID, false));
    }

    /** A fornalha da posição com uma exceção: Cima insere (no quadro canônico). */
    private static void exceptionUp(GameTestHelper helper, RouterBlockEntity router, BlockPos relative, PortMode mode) {
        router.configureFace(EditTarget.machine(index(helper, router, relative)), ResourceType.ITEM, Direction.UP, mode,
                0, RedstoneMode.IGNORE);
    }

    private static PortMode upOf(MachineConfig config) {
        return config.face(ResourceType.ITEM, RemoteSide.TOP).mode();
    }

    private static void cleanUp(ServerPlayer player) {
        MachineClipboard.forget(player.getUUID());
        MachinePasteUndo.forget(player.getUUID());
    }

    /**
     * Máquina → máquina: a exceção de A vira a exceção de B; colar o perfil do grupo em B tira a exceção (igual ao
     * perfil). O nome da cópia vai no snapshot (e passa pelo codec) e some ao esquecer.
     */
    @GameTest(template = "empty")
    public static void pasteMachineOnMachine(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    exceptionUp(helper, router, FURNACE_A, PortMode.INSERT);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        menu.pollSnapshot();
                        helper.assertTrue(menu.snapshot().clipboard().getString().isEmpty(), "cópia antes de copiar");
                        selectMachine(helper, player, router, FURNACE_A);
                        helper.assertTrue(copy(player), "não copiou");
                        MachineClipboard.Entry entry = MachineClipboard.get(player.getUUID());
                        helper.assertTrue(entry != null && upOf(entry.config()) == PortMode.INSERT, "a cópia não é a exceção");
                        RouterSnapshot snapshot = menu.pollSnapshot();
                        helper.assertTrue(snapshot != null && !snapshot.clipboard().getString().isEmpty(),
                                "o nome da cópia não foi no snapshot");
                        helper.assertTrue(snapshot.clipboard().getContents() instanceof TranslatableContents name
                                && name.getKey().equals("gui.wirelessautomate.router.range.machine.name"),
                                "nome da cópia: " + snapshot.clipboard());
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                                helper.getLevel().registryAccess());
                        RouterSnapshot.STREAM_CODEC.encode(buf, snapshot);
                        helper.assertTrue(RouterSnapshot.STREAM_CODEC.decode(buf).equals(snapshot), "snapshot pelo codec");
                        helper.assertTrue(menu.pollSnapshot() == null, "snapshot de novo sem mudança");

                        selectMachine(helper, player, router, FURNACE_B);
                        helper.assertTrue(RangePayloads.handleMachinePaste(player,
                                MachinePastePayload.paste(CONTAINER_ID, false)), "não colou");
                        RemoteMachine b = machine(helper, router, FURNACE_B);
                        helper.assertTrue(b.hasException() && upOf(b.exception()) == PortMode.INSERT, "B sem a exceção de A");
                        helper.assertTrue(MachinePasteUndo.has(player.getUUID()), "sem Desfazer");

                        int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                        selectGroup(helper, player, router, furnaces);
                        helper.assertTrue(copy(player), "não copiou o perfil");
                        selectMachine(helper, player, router, FURNACE_B);
                        MachineCopyPaste.Pasted pasted = MachineCopyPaste.paste(player, menu, false);
                        helper.assertTrue(pasted != null && pasted.changed(), "não colou o perfil");
                        helper.assertFalse(machine(helper, router, FURNACE_B).hasException(),
                                "a exceção igual ao perfil ficou");
                        helper.assertValueEqual(key(pasted.notice(CONTAINER_ID)), "gui.wirelessautomate.panel.notice.range.pasted",
                                "aviso");
                        helper.assertValueEqual(pasted.notice(CONTAINER_ID).undo(), PanelNoticePayload.Undo.UNDO_MACHINE_PASTE,
                                "Desfazer do aviso");

                        helper.assertTrue(RangePayloads.handleMachineCopy(player, new MachineCopyPayload(CONTAINER_ID, true)),
                                "não esqueceu");
                        helper.assertTrue(MachineClipboard.get(player.getUUID()) == null, "a cópia ficou");
                        RouterSnapshot forgotten = menu.pollSnapshot();
                        helper.assertTrue(forgotten != null && forgotten.clipboard().getString().isEmpty(),
                                "o snapshot ficou com a cópia");
                        helper.assertFalse(RangePayloads.handleMachinePaste(player,
                                MachinePastePayload.paste(CONTAINER_ID, false)), "colou sem cópia");
                    } finally {
                        cleanUp(player);
                    }
                })
                .thenSucceed();
    }

    /**
     * A presa (um baú) sozinha no grupo recebe a cópia de uma fornalha: outro bloco é permitido e muda o perfil do
     * grupo dela, sem exceção; o Desfazer volta o perfil de antes (com as redes).
     */
    @GameTest(template = "empty")
    public static void pasteOnLoneAttachedChangesProfile(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    profile(helper, router, FURNACE_A, Direction.UP, PortMode.EXTRACT);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        selectMachine(helper, player, router, FURNACE_A);
                        helper.assertTrue(copy(player), "não copiou");
                        selectAttached(helper, player);
                        RemoteMachines remotes = router.remoteMachines();
                        MachineConfig before = remotes.attachedGroup().profile().copy();
                        MachineCopyPaste.Pasted pasted = MachineCopyPaste.paste(player, menu, false);
                        helper.assertTrue(pasted != null && pasted.changed(), "não colou na presa");
                        helper.assertValueEqual(pasted.notice(CONTAINER_ID).undo(), PanelNoticePayload.Undo.UNDO_MACHINE_PASTE,
                                "Desfazer do aviso");
                        helper.assertValueEqual(remotes.memberCount(remotes.attached().group()), 1, "a presa não está só");
                        helper.assertFalse(remotes.attached().hasException(), "virou exceção");
                        helper.assertValueEqual(upOf(remotes.attachedGroup().profile()), PortMode.EXTRACT,
                                "o perfil do grupo da presa");
                        helper.assertTrue(remotes.attachedGroup().block().equals(BuiltInRegistries.BLOCK.getKey(Blocks.CHEST)),
                                "o grupo da presa mudou de bloco");
                        helper.assertTrue(RangePayloads.handleMachinePaste(player, MachinePastePayload.undo(CONTAINER_ID)),
                                "não desfez");
                        helper.assertTrue(remotes.attachedGroup().profile().sameAs(before), "o perfil antigo não voltou");
                    } finally {
                        cleanUp(player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Shift (grupo inteiro) numa máquina: a cópia vai para o perfil; a exceção de A, agora igual, some; a de B, que
     * difere, fica e é contada no aviso.
     */
    @GameTest(template = "empty")
    public static void pasteWholeGroupCountsExceptions(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    exceptionUp(helper, router, FURNACE_A, PortMode.INSERT);
                    exceptionUp(helper, router, FURNACE_B, PortMode.EXTRACT);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        selectMachine(helper, player, router, FURNACE_A);
                        helper.assertTrue(copy(player), "não copiou");
                        MachineCopyPaste.Pasted pasted = MachineCopyPaste.paste(player, menu, true);
                        helper.assertTrue(pasted != null && pasted.changed(), "não colou no grupo");
                        int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                        helper.assertValueEqual(upOf(router.remoteMachines().group(furnaces).profile()), PortMode.INSERT,
                                "perfil do grupo");
                        helper.assertFalse(machine(helper, router, FURNACE_A).hasException(), "a exceção igual ficou");
                        helper.assertTrue(machine(helper, router, FURNACE_B).hasException(), "a exceção diferente saiu");
                        helper.assertValueEqual(pasted.exceptionsKept(), 1, "exceções mantidas");
                        helper.assertValueEqual(pasted.notice(CONTAINER_ID).message().getSiblings().size(), 2, "partes do aviso");
                    } finally {
                        cleanUp(player);
                    }
                })
                .thenSucceed();
    }

    /**
     * A rede da cópia que o jogador não pode usar (privada de outro dono) não entra: a face fica com a rede que
     * tinha, e o aviso conta a face; a rede dele entra.
     */
    @GameTest(template = "empty")
    public static void pasteKeepsForbiddenNetwork(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    WaNetwork own = data.create(player.getUUID(), "Própria " + player.getUUID());
                    WaNetwork foreign = data.create(UUID.randomUUID(), "Privada " + player.getUUID());
                    try {
                        int a = index(helper, router, FURNACE_A);
                        router.setFaceNetwork(EditTarget.machine(a), ResourceType.ITEM, Direction.UP, foreign.id());
                        router.setFaceNetwork(EditTarget.machine(a), ResourceType.ITEM, Direction.DOWN, own.id());
                        selectMachine(helper, player, router, FURNACE_A);
                        helper.assertTrue(copy(player), "não copiou");
                        selectMachine(helper, player, router, FURNACE_B);
                        MachineCopyPaste.Pasted pasted = MachineCopyPaste.paste(player, menu, false);
                        helper.assertTrue(pasted != null && pasted.changed(), "não colou");
                        MachineConfig b = machine(helper, router, FURNACE_B).exception();
                        helper.assertTrue(b != null, "B sem exceção");
                        helper.assertTrue(b.face(ResourceType.ITEM, RemoteSide.TOP).network() == null,
                                "a rede proibida entrou");
                        helper.assertTrue(Objects.equals(b.face(ResourceType.ITEM, RemoteSide.BOTTOM).network(), own.id()),
                                "a rede própria não entrou");
                        helper.assertValueEqual(pasted.keptFaces(), 1, "faces com a rede de antes");
                    } finally {
                        cleanUp(player);
                        data.remove(own.id());
                        data.remove(foreign.id());
                    }
                })
                .thenSucceed();
    }

    /**
     * O Desfazer volta a exceção de antes; colar de novo e mudar o alvo depois faz o Desfazer não mexer em nada (e
     * ele vale uma vez só).
     */
    @GameTest(template = "empty")
    public static void pasteUndoAndRefusedWhenChanged(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    exceptionUp(helper, router, FURNACE_A, PortMode.INSERT);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        selectMachine(helper, player, router, FURNACE_A);
                        helper.assertTrue(copy(player), "não copiou");
                        selectMachine(helper, player, router, FURNACE_B);
                        helper.assertTrue(RangePayloads.handleMachinePaste(player,
                                MachinePastePayload.paste(CONTAINER_ID, false)), "não colou");
                        helper.assertTrue(machine(helper, router, FURNACE_B).hasException(), "B sem exceção");
                        helper.assertTrue(RangePayloads.handleMachinePaste(player, MachinePastePayload.undo(CONTAINER_ID)),
                                "não desfez");
                        helper.assertFalse(machine(helper, router, FURNACE_B).hasException(), "o Desfazer não voltou B");
                        helper.assertFalse(RangePayloads.handleMachinePaste(player, MachinePastePayload.undo(CONTAINER_ID)),
                                "desfez duas vezes");

                        helper.assertTrue(RangePayloads.handleMachinePaste(player,
                                MachinePastePayload.paste(CONTAINER_ID, false)), "não colou de novo");
                        exceptionUp(helper, router, FURNACE_B, PortMode.BOTH);
                        PanelNoticePayload notice = MachineCopyPaste.undo(player, (RouterMenu) player.containerMenu);
                        helper.assertTrue(notice != null, "sem aviso do Desfazer");
                        helper.assertValueEqual(key(notice), "gui.wirelessautomate.panel.notice.range.paste_undo_refused",
                                "aviso do Desfazer recusado");
                        RemoteMachine b = machine(helper, router, FURNACE_B);
                        helper.assertTrue(b.hasException() && upOf(b.exception()) == PortMode.BOTH,
                                "o Desfazer apagou a mudança de depois");
                    } finally {
                        cleanUp(player);
                    }
                })
                .thenSucceed();
    }

    /** A cópia e o Desfazer somem quando o jogador sai do servidor. */
    @GameTest(template = "empty")
    public static void clipboardForgottenOnLogout(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        selectMachine(helper, player, router, FURNACE_A);
                        helper.assertTrue(copy(player), "não copiou");
                        exceptionUp(helper, router, FURNACE_A, PortMode.INSERT);
                        helper.assertTrue(copy(player), "não copiou de novo");
                        selectMachine(helper, player, router, FURNACE_B);
                        helper.assertTrue(RangePayloads.handleMachinePaste(player,
                                MachinePastePayload.paste(CONTAINER_ID, false)), "não colou");
                        helper.assertTrue(MachinePasteUndo.has(player.getUUID()), "sem Desfazer");
                        removePlayer(helper, player);
                        helper.assertTrue(MachineClipboard.get(player.getUUID()) == null, "a cópia ficou depois do logout");
                        helper.assertFalse(MachinePasteUndo.has(player.getUUID()), "o Desfazer ficou depois do logout");
                    } finally {
                        cleanUp(player);
                    }
                })
                .thenSucceed();
    }

    /**
     * A presa sem membro (o roteador novo, sem nada configurado) recebe o colar: o membro nasce no grupo do bloco da
     * frente antes do colar, e o Desfazer existe e volta o perfil vazio.
     */
    @GameTest(template = "empty")
    public static void pasteOnAttachedWithoutMember(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.setBlock(FURNACE_A, Blocks.FURNACE.defaultBlockState()
                .setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    profile(helper, router, FURNACE_A, Direction.UP, PortMode.EXTRACT);
                    RemoteMachines remotes = router.remoteMachines();
                    helper.assertTrue(remotes.attached() == null, "a presa nasceu com membro");
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        selectMachine(helper, player, router, FURNACE_A);
                        helper.assertTrue(copy(player), "não copiou");
                        selectAttached(helper, player);
                        MachineCopyPaste.Pasted pasted = MachineCopyPaste.paste(player, menu, false);
                        helper.assertTrue(pasted != null && pasted.changed(), "não colou na presa");
                        helper.assertTrue(pasted.undo(), "colar na presa sem membro sem Desfazer");
                        helper.assertTrue(remotes.attached() != null, "o membro não nasceu");
                        helper.assertValueEqual(upOf(remotes.attachedGroup().profile()), PortMode.EXTRACT, "perfil colado");
                        helper.assertTrue(RangePayloads.handleMachinePaste(player, MachinePastePayload.undo(CONTAINER_ID)),
                                "não desfez");
                        helper.assertTrue(remotes.attachedGroup().profile().isDefault(), "o perfil vazio não voltou");
                    } finally {
                        cleanUp(player);
                    }
                })
                .thenSucceed();
    }

    /**
     * A face de B numa rede de outro dono, que o jogador não edita: o colar não a tira de lá (canLeave), mesmo com a
     * rede da cópia sendo dele; o aviso conta a face.
     */
    @GameTest(template = "empty")
    public static void pasteDoesNotLeaveForeignNetwork(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    WaNetwork own = data.create(player.getUUID(), "Própria " + player.getUUID());
                    WaNetwork foreign = data.create(UUID.randomUUID(), "Alheia " + player.getUUID());
                    try {
                        router.setFaceNetwork(EditTarget.machine(index(helper, router, FURNACE_A)), ResourceType.ITEM,
                                Direction.UP, own.id());
                        router.setFaceNetwork(EditTarget.machine(index(helper, router, FURNACE_B)), ResourceType.ITEM,
                                Direction.UP, foreign.id());
                        selectMachine(helper, player, router, FURNACE_A);
                        helper.assertTrue(copy(player), "não copiou");
                        selectMachine(helper, player, router, FURNACE_B);
                        MachineCopyPaste.Pasted pasted = MachineCopyPaste.paste(player, menu, false);
                        helper.assertTrue(pasted != null, "recusou o colar");
                        MachineConfig b = machine(helper, router, FURNACE_B).exception();
                        helper.assertTrue(b != null && Objects.equals(b.face(ResourceType.ITEM, RemoteSide.TOP).network(),
                                foreign.id()), "o colar tirou a face da rede alheia");
                        helper.assertValueEqual(pasted.keptFaces(), 1, "faces com a rede de antes");
                        helper.assertValueEqual(pasted.notice(CONTAINER_ID).message().getSiblings().size(), 2,
                                "partes do aviso");
                    } finally {
                        cleanUp(player);
                        data.remove(own.id());
                        data.remove(foreign.id());
                    }
                })
                .thenSucceed();
    }

    /** O Desfazer de outro menu (outro containerId) é recusado e o guardado fica; fechar o menu o apaga. */
    @GameTest(template = "empty")
    public static void pasteUndoOtherMenuAndClose(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    exceptionUp(helper, router, FURNACE_A, PortMode.INSERT);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        selectMachine(helper, player, router, FURNACE_A);
                        helper.assertTrue(copy(player), "não copiou");
                        selectMachine(helper, player, router, FURNACE_B);
                        helper.assertTrue(RangePayloads.handleMachinePaste(player,
                                MachinePastePayload.paste(CONTAINER_ID, false)), "não colou");
                        helper.assertFalse(RangePayloads.handleMachinePaste(player,
                                MachinePastePayload.undo(CONTAINER_ID + 1)), "desfez de outro menu");
                        helper.assertTrue(MachinePasteUndo.has(player.getUUID()), "o outro menu apagou o Desfazer");
                        helper.assertTrue(machine(helper, router, FURNACE_B).hasException(), "o outro menu desfez");
                        menu.removed(player);
                        helper.assertFalse(MachinePasteUndo.has(player.getUUID()), "o Desfazer ficou depois de fechar");
                    } finally {
                        cleanUp(player);
                    }
                })
                .thenSucceed();
    }
}
