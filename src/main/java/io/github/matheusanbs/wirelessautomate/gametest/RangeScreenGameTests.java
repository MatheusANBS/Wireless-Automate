package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.RevertMachinePayload;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.UnlinkMachinePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Roteador de alcance, a tela: o alvo de edição, a {@link RangeView} no snapshot e os payloads por alvo, pelos
 * handlers públicos com a {@link RouterMenu} aberta.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeScreenGameTests {
    /** Barril a 7 blocos do roteador (raio do Avançado é 6): fora do raio. */
    private static final BlockPos FAR_BARREL = new BlockPos(1, 9, 1);

    private static ResourceLocation furnaceId() {
        return BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);
    }

    /** Liga o upgrade (depois do {@code onLoad}) e vincula as duas fornalhas e o barril. */
    private static void linkAll(GameTestHelper helper, RouterBlockEntity router) {
        router.setRangeUpgrade(rangeUpgrade());
        link(helper, router, FURNACE_A);
        link(helper, router, FURNACE_B);
        link(helper, router, BARREL);
    }

    private static SetFacePayload setFace(RouterMenu menu, Direction face, PortMode mode) {
        return new SetFacePayload(menu.containerId, ResourceType.ITEM, face, mode, 0, RedstoneMode.IGNORE);
    }

    @GameTest(template = "empty")
    public static void screenGroupTargetEditsProfile(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        int furnaces = groupOf(router, a);
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.group(menu.containerId, furnaces, furnaceId())), "não escolheu o grupo");
                        helper.assertValueEqual(menu.target(), EditTarget.group(furnaces), "alvo no menu");

                        // Mudar uma face (canônica: SOUTH = Trás) muda o perfil, não a máquina presa.
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.SOUTH, PortMode.EXTRACT)),
                                "recusou a face do grupo");
                        helper.assertValueEqual(router.face(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH).mode(),
                                PortMode.EXTRACT, "perfil");
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.SOUTH).mode(), PortMode.NONE,
                                "a máquina presa mudou");
                        helper.assertFalse(router.remoteMachines().machine(router.remoteMachines().index(a)).hasException(),
                                "mudar o perfil criou exceção");

                        // O snapshot leva a visão das remotas, o alvo e as faces do perfil, com o visor na fornalha.
                        RouterSnapshot snapshot = menu.pollSnapshot();
                        helper.assertTrue(snapshot != null, "o snapshot não mudou com o alvo");
                        RangeView range = snapshot.range();
                        helper.assertTrue(range.present(), "sem a visão das remotas com o upgrade");
                        helper.assertValueEqual(range.radius(), Config.machineRadius(RouterTier.ADVANCED), "raio");
                        helper.assertValueEqual(range.max(), Config.maxMachines(RouterTier.ADVANCED), "limite");
                        helper.assertValueEqual(snapshot.target(), EditTarget.group(furnaces), "alvo no snapshot");
                        RangeView.GroupView group = range.group(furnaces);
                        helper.assertTrue(group != null && group.count() == 2 && group.exceptions() == 0
                                && group.block().equals(furnaceId()) && group.icon().is(Items.FURNACE), "grupo das fornalhas");
                        helper.assertValueEqual(range.machines().size(), 3, "máquinas");
                        RangeView.MachineView view = range.machine(router.remoteMachines().index(a));
                        helper.assertTrue(view.pos().equals(a) && view.group() == furnaces && view.distance() == 1
                                && view.state() == RangeView.MachineState.LOADED, "máquina A: " + view);
                        helper.assertValueEqual(snapshot.face(ResourceType.ITEM, Direction.SOUTH).mode(), PortMode.EXTRACT,
                                "face do perfil no snapshot");
                        helper.assertTrue(snapshot.face(ResourceType.ITEM, Direction.DOWN).slots() > 0,
                                "a face de baixo da fornalha sem slots");
                        helper.assertValueEqual(snapshot.face(ResourceType.FLUID, Direction.DOWN).slots(), -1, "fornalha com tanque");
                        helper.assertTrue(snapshot.machineState().is(Blocks.FURNACE)
                                && snapshot.machineState().getValue(AbstractFurnaceBlock.FACING) == Direction.NORTH,
                                "o visor não mostra a fornalha virada para Norte: " + snapshot.machineState());
                        helper.assertTrue(snapshot.machine().is(Items.FURNACE), "ícone do alvo");

                        // Ida e volta pelo codec, com a visão.
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                                helper.getLevel().registryAccess());
                        RouterSnapshot.STREAM_CODEC.encode(buf, snapshot);
                        helper.assertTrue(RouterSnapshot.STREAM_CODEC.decode(buf).equals(snapshot), "snapshot pelo codec");

                        // Os slots de cartão mostram os do grupo, na face canônica; pôr pelo slot grava no grupo.
                        router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                        menu.select(ResourceType.ITEM, Direction.SOUTH);
                        helper.assertTrue(menu.getSlot(0).getItem().is(ModItems.FILTER_CARD.get()), "o slot não mostra o cartão do grupo");
                        menu.getSlot(1).set(itemCard());
                        helper.assertTrue(router.card(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 1)
                                .is(ModItems.FILTER_CARD.get()), "o slot não gravou no grupo");
                        helper.assertTrue(router.card(EditTarget.ATTACHED_TARGET, ResourceType.ITEM, Direction.SOUTH, 1).isEmpty(),
                                "o slot gravou na máquina presa");

                        // Voltar à presa.
                        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.attached(menu.containerId)),
                                "não voltou à presa");
                        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "o slot da presa mostra o cartão do grupo");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void screenMachineTargetCreatesException(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos b = helper.absolutePos(FURNACE_B);
                        int indexB = router.remoteMachines().index(b);
                        int furnaces = groupOf(router, b);
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.machine(menu.containerId, indexB, b)), "não escolheu a máquina");

                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.INSERT)),
                                "recusou a face da máquina");
                        helper.assertTrue(router.remoteMachines().machine(indexB).hasException(), "não criou a exceção");
                        helper.assertValueEqual(router.face(EditTarget.group(furnaces), ResourceType.ITEM, Direction.UP).mode(),
                                PortMode.NONE, "mudar a máquina mudou o perfil");
                        RouterSnapshot snapshot = menu.pollSnapshot();
                        helper.assertTrue(snapshot != null && snapshot.range().machine(indexB).exception()
                                && snapshot.range().group(furnaces).exceptions() == 1, "a exceção não aparece no snapshot");
                        helper.assertValueEqual(snapshot.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT,
                                "face da máquina no snapshot");

                        // Voltar ao perfil: com a posição errada, nada; com a certa, a exceção sai.
                        helper.assertFalse(ModPayloads.handleRevertMachine(player,
                                new RevertMachinePayload(menu.containerId, indexB, b.above())), "reverteu com a posição errada");
                        helper.assertTrue(ModPayloads.handleRevertMachine(player,
                                new RevertMachinePayload(menu.containerId, indexB, b)), "não reverteu");
                        helper.assertFalse(router.remoteMachines().machine(indexB).hasException(), "a exceção ficou");
                        helper.assertValueEqual(router.face(EditTarget.machine(indexB), ResourceType.ITEM, Direction.UP).mode(),
                                PortMode.NONE, "voltar ao perfil não voltou a face");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void screenUnlinkDropsGroupCards(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos barrel = helper.absolutePos(BARREL);
                        int index = router.remoteMachines().index(barrel);
                        int barrels = groupOf(router, barrel);
                        router.setCard(EditTarget.group(barrels), ResourceType.ITEM, Direction.DOWN, 0, itemCard());
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.machine(menu.containerId, index, barrel)), "não escolheu o barril");

                        helper.assertFalse(ModPayloads.handleUnlinkMachine(player,
                                new UnlinkMachinePayload(menu.containerId, index, helper.absolutePos(FURNACE_A))),
                                "desvinculou com a posição de outra máquina");
                        helper.assertTrue(router.remoteMachines().contains(barrel), "o barril saiu pela posição errada");
                        helper.assertTrue(ModPayloads.handleUnlinkMachine(player,
                                new UnlinkMachinePayload(menu.containerId, index, barrel)), "não desvinculou");
                        helper.assertFalse(router.remoteMachines().contains(barrel), "o barril continua vinculado");
                        helper.assertTrue(router.remoteMachines().group(barrels) == null, "o grupo vazio ficou");
                        helper.assertItemEntityPresent(ModItems.FILTER_CARD.get(), ROUTER, 2.0);
                        helper.assertTrue(menu.target().attached(), "o alvo continua na máquina desvinculada");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void screenInvalidTargetIgnored(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        BlockPos b = helper.absolutePos(FURNACE_B);
                        int furnaces = groupOf(router, a);
                        int cid = menu.containerId;

                        // Sem o upgrade, nenhum alvo remoto.
                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(cid, furnaces,
                                furnaceId())), "escolheu um grupo sem o upgrade");
                        router.setRangeUpgrade(rangeUpgrade());

                        // Bloco, posição ou índice que não batem.
                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(cid, furnaces,
                                BuiltInRegistries.BLOCK.getKey(Blocks.BARREL))), "escolheu o grupo com outro bloco");
                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(cid, furnaces + 100,
                                furnaceId())), "escolheu um grupo que não existe");
                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.machine(cid, 0, b)),
                                "escolheu a máquina 0 com a posição da 1");
                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.machine(cid, 5, a)),
                                "escolheu um índice fora da lista");
                        helper.assertTrue(menu.target().attached(), "um alvo inválido mudou o menu");
                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.attached(cid + 1)),
                                "aceitou outro containerId");

                        // A máquina escolhida muda de índice (outra antes dela sai): o alvo segue pela posição.
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.machine(cid, router.remoteMachines().index(b), b)), "não escolheu a B");
                        helper.assertTrue(router.unlinkMachine(a), "não desvinculou a A");
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.EXTRACT)),
                                "recusou a face da B depois de mudar de índice");
                        int indexB = router.remoteMachines().index(b);
                        helper.assertValueEqual(menu.target(), EditTarget.machine(indexB), "o alvo não seguiu a B");
                        helper.assertTrue(router.remoteMachines().machine(indexB).hasException(), "a B não ganhou a exceção");

                        // A máquina escolhida sai: a ação seguinte é ignorada e o alvo volta para a presa.
                        helper.assertTrue(router.unlinkMachine(b), "não desvinculou a B");
                        helper.assertFalse(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.INSERT)),
                                "aplicou a face num alvo que sumiu");
                        helper.assertTrue(menu.target().attached(), "o alvo não voltou para a presa");
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.NONE,
                                "a ação ignorada caiu na máquina presa");
                        // Daí em diante, na presa.
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.INSERT)),
                                "recusou a face da presa");
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT,
                                "a face da presa não mudou");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void snapshotWithoutUpgradeUnchanged(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    router.configureFace(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 4, RedstoneMode.IGNORE);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        // Com máquinas guardadas mas sem o upgrade: sem visão, presa e faces absolutas da presa.
                        RouterSnapshot snapshot = RouterSnapshot.capture(router, player,
                                EditTarget.group(groupOf(router, helper.absolutePos(FURNACE_A))));
                        helper.assertTrue(snapshot.range() == RangeView.EMPTY, "visão das remotas sem o upgrade");
                        helper.assertTrue(snapshot.target().attached(), "alvo remoto sem o upgrade");
                        helper.assertTrue(snapshot.equals(RouterSnapshot.capture(router, player)), "o alvo mudou o snapshot");
                        helper.assertTrue(snapshot.machine().is(Items.CHEST), "o ícone não é o da presa");
                        for (ResourceType type : ResourceType.values()) {
                            for (Direction face : Direction.values()) {
                                FaceConfig config = router.face(type, face);
                                RouterSnapshot.FaceView view = snapshot.face(type, face);
                                helper.assertTrue(view.mode() == config.mode() && view.priority() == config.priority(),
                                        "face " + type + " " + face);
                            }
                        }
                        helper.assertValueEqual(snapshot.face(ResourceType.ITEM, Direction.UP).slots(), 27, "slots do baú");
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                                helper.getLevel().registryAccess());
                        RouterSnapshot.STREAM_CODEC.encode(buf, snapshot);
                        helper.assertTrue(RouterSnapshot.STREAM_CODEC.decode(buf).equals(snapshot), "snapshot pelo codec");
                        // Sem o upgrade, o tempo não recaptura.
                        menu.pollSnapshot();
                        helper.assertTrue(menu.pollSnapshot() == null, "recapturou sem mudança e sem as remotas");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void snapshotMachineStates(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer[] holder = new ServerPlayer[1];
        RouterMenu[] menus = new RouterMenu[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    helper.setBlock(FAR_BARREL, Blocks.BARREL);
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FAR_BARREL);
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    menus[0] = openMenu(helper, router, holder);
                    RangeView range = menus[0].snapshot().range();
                    helper.assertValueEqual(range.machine(0).state(), RangeView.MachineState.OUT_OF_RANGE, "barril longe");
                    helper.assertValueEqual(range.machine(0).distance(), 7, "distância do barril longe");
                    helper.assertValueEqual(range.machine(1).state(), RangeView.MachineState.LOADED, "fornalha A");
                    helper.assertValueEqual(range.used(), 2, "vagas usadas");
                    // A fornalha B vira pedra: sem mexer na versão do roteador, a tela vê em até 20 ticks.
                    helper.setBlock(FURNACE_B, Blocks.STONE);
                })
                .thenExecuteAfter(RouterMenu.SAMPLE_TICKS + 1, () -> {
                    try {
                        menus[0].pollSnapshot();
                        RangeView range = menus[0].snapshot().range();
                        helper.assertValueEqual(range.machine(2).state(), RangeView.MachineState.MISSING, "fornalha que virou pedra");
                        helper.assertValueEqual(range.machine(1).state(), RangeView.MachineState.LOADED, "fornalha A depois");
                    } finally {
                        removePlayer(helper, holder[0]);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void snapshotOverLimit(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "limite").id());
        int max = Config.maxMachines(RouterTier.ADVANCED);
        java.util.List<BlockPos> barrels = new java.util.ArrayList<>();
        for (int i = 0; i <= max; i++) {
            BlockPos pos = new BlockPos(i % 3, 3 + i / 3, i % 3 == 1 ? 0 : 2);
            barrels.add(pos);
            helper.setBlock(pos, Blocks.BARREL);
        }
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    for (BlockPos pos : barrels) {
                        link(helper, router, pos);
                    }
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    try {
                        RangeView range = menu.snapshot().range();
                        helper.assertValueEqual(range.machine(max).state(), RangeView.MachineState.OVER_LIMIT, "a além do limite");
                        helper.assertValueEqual(range.machine(0).state(), RangeView.MachineState.LOADED, "a primeira");
                        helper.assertValueEqual(range.used(), max, "vagas usadas");
                    } finally {
                        removePlayer(helper, holder[0]);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void remoteFilterTargetFollowsTarget(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                        helper.assertTrue(menu.selectTarget(EditTarget.group(furnaces), null, furnaceId()), "não escolheu o grupo");
                        RouterFaceFilterTarget target = new RouterFaceFilterTarget(router, ResourceType.ITEM, Direction.EAST,
                                menu.targetRef());
                        Filter blacklist = Filter.EMPTY.withListMode(Filter.ListMode.BLACKLIST);
                        target.setFilter(blacklist);
                        helper.assertValueEqual(router.face(EditTarget.group(furnaces), ResourceType.ITEM, Direction.EAST)
                                .filter().listMode(), Filter.ListMode.BLACKLIST, "o filtro não foi para o perfil");
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.EAST).filter().listMode(),
                                Filter.ListMode.WHITELIST, "o filtro foi para a presa");
                        helper.assertTrue(target.stillValid(player), "o filtro do grupo fechou");

                        // O grupo some (as duas fornalhas saem): o filtro fecha e não grava.
                        router.unlinkMachine(helper.absolutePos(FURNACE_A));
                        router.unlinkMachine(helper.absolutePos(FURNACE_B));
                        helper.assertFalse(target.stillValid(player), "o filtro de um grupo que sumiu continua aberto");
                        target.setFilter(Filter.EMPTY);
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.EAST).filter().listMode(),
                                Filter.ListMode.WHITELIST, "o filtro de um grupo que sumiu gravou na presa");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    private static void assertAttachedCardsEmpty(GameTestHelper helper, RouterBlockEntity router, String what) {
        for (Direction face : Direction.values()) {
            for (int slot = 0; slot < RouterBlockEntity.CARD_SLOTS; slot++) {
                helper.assertTrue(router.card(EditTarget.ATTACHED_TARGET, ResourceType.ITEM, face, slot).isEmpty(),
                        what + ": cartão na presa em " + face);
            }
        }
    }

    /**
     * Tirar o upgrade com a tela num alvo remoto: o menu volta para a presa, na face onde o roteador está
     * preso (a mesma que o cliente adota, {@code snapshot.facing()}), e os cartões não ficam travados.
     */
    @GameTest(template = "empty")
    public static void screenUpgradeRemovedRestoresFace(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.group(menu.containerId, furnaces, furnaceId())), "não escolheu o grupo");
                        menu.select(ResourceType.ITEM, Direction.SOUTH);
                        router.removeRangeUpgrade();
                        RouterSnapshot snapshot = menu.pollSnapshot();
                        helper.assertTrue(menu.validTarget().attached(), "o alvo não voltou para a presa");
                        helper.assertValueEqual(menu.selectedFace(), router.facing(), "a face não voltou para a da presa");
                        helper.assertTrue(snapshot != null && !snapshot.range().present() && snapshot.target().attached(),
                                "o snapshot ainda traz o alvo remoto");
                        helper.assertFalse(menu.cardsLocked(), "os cartões ficaram travados sem o upgrade");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void screenShiftClickCardGoesToGroup(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                        helper.assertValueEqual(menu.selectedFace(), Direction.UP, "face inicial da presa");
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.group(menu.containerId, furnaces, furnaceId())), "não escolheu o grupo");
                        // A face vai para a Frente canônica e segue no snapshot.
                        helper.assertValueEqual(menu.selectedFace(), Direction.NORTH, "face padrão no remoto");
                        RouterSnapshot snapshot = menu.pollSnapshot();
                        helper.assertTrue(snapshot != null && snapshot.range().face() == Direction.NORTH,
                                "a face padrão não foi no snapshot");

                        player.getInventory().setItem(0, itemCard());
                        int hotbar0 = RouterMenu.UPGRADE_SLOT - 9;
                        menu.clicked(hotbar0, 0, ClickType.QUICK_MOVE, player);
                        helper.assertTrue(router.card(EditTarget.group(furnaces), ResourceType.ITEM, Direction.NORTH, 0)
                                .is(ModItems.FILTER_CARD.get()), "o Shift + clique não pôs o cartão no grupo");
                        helper.assertTrue(menu.getSlot(hotbar0).getItem().isEmpty(), "o cartão ficou no inventário");
                        assertAttachedCardsEmpty(helper, router, "Shift + clique com alvo de grupo");

                        // De volta à presa, a face de antes.
                        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.attached(menu.containerId)),
                                "não voltou à presa");
                        helper.assertValueEqual(menu.selectedFace(), Direction.UP, "a face da presa não voltou");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void screenLostTargetLocksCards(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos barrel = helper.absolutePos(BARREL);
                        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.machine(menu.containerId,
                                router.remoteMachines().index(barrel), barrel)), "não escolheu o barril");
                        helper.assertFalse(menu.cardsLocked(), "cartões travados com o alvo valendo");

                        // O barril sai com um cartão no cursor: o clique no slot não o põe na presa.
                        menu.setCarried(itemCard());
                        helper.assertTrue(router.unlinkMachine(barrel), "não desvinculou o barril");
                        menu.clicked(0, 0, ClickType.PICKUP, player);
                        helper.assertTrue(menu.getCarried().is(ModItems.FILTER_CARD.get()), "o cartão saiu do cursor");
                        helper.assertTrue(menu.target().attached(), "o alvo não voltou para a presa");
                        helper.assertTrue(menu.cardsLocked(), "cartões destravados com o alvo perdido");
                        assertAttachedCardsEmpty(helper, router, "clique com o alvo perdido");

                        // Shift + clique também não.
                        player.getInventory().setItem(0, itemCard());
                        int hotbar0 = RouterMenu.UPGRADE_SLOT - 9;
                        menu.clicked(hotbar0, 0, ClickType.QUICK_MOVE, player);
                        assertAttachedCardsEmpty(helper, router, "Shift + clique com o alvo perdido");

                        // Escolher a presa pela tela destrava.
                        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.attached(menu.containerId)),
                                "não escolheu a presa");
                        helper.assertFalse(menu.cardsLocked(), "cartões travados depois de escolher a presa");
                        menu.setCarried(ItemStack.EMPTY);
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }
}
