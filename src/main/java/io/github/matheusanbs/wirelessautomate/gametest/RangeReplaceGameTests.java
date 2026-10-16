package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RemoteRouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.UpdateMachinePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Roteador de alcance, a máquina trocada no lugar: o estado {@link RangeView.MachineState#REPLACED} no
 * snapshot e o Atualizar ({@code RouterBlockEntity.updateReplaced} e o {@link UpdateMachinePayload}). Uma
 * fornalha trocada por um alto-forno é trocada; por pedra, sumida.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeReplaceGameTests {
    /** Alto-forno do lado das fornalhas, para o grupo do bloco novo já existir. */
    private static final BlockPos BLAST = new BlockPos(2, 1, 2);
    /** Uma terceira fornalha, para o grupo antigo ficar com uma máquina. */
    private static final BlockPos FURNACE_C = new BlockPos(2, 1, 1);

    private static ResourceLocation id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    private static void blastAt(GameTestHelper helper, BlockPos relative, Direction facing) {
        helper.setBlock(relative, Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, facing));
    }

    private static int index(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        return router.remoteMachines().index(helper.absolutePos(relative));
    }

    private static RemoteMachine machine(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        return router.remoteMachines().machine(index(helper, router, relative));
    }

    private static @Nullable ResourceLocation replacedBy(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        return router.replacedBy(machine(helper, router, relative), router.machineReach()[index(helper, router, relative)]);
    }

    private static PortMode profileMode(RouterBlockEntity router, int group, Direction side) {
        return router.face(EditTarget.group(group), ResourceType.ITEM, side).mode();
    }

    @GameTest(template = "empty")
    public static void replacedStateInSnapshot(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    blastAt(helper, FURNACE_A, Direction.WEST);
                    helper.setBlock(FURNACE_B, Blocks.STONE);
                    ServerPlayer player = TestPlayers.inLevel(helper);
                    try {
                        RouterSnapshot snapshot = RouterSnapshot.capture(router, player);
                        RangeView range = snapshot.range();
                        RangeView.MachineView a = range.machine(index(helper, router, FURNACE_A));
                        RangeView.MachineView b = range.machine(index(helper, router, FURNACE_B));
                        RangeView.MachineView barrel = range.machine(index(helper, router, BARREL));
                        helper.assertValueEqual(a.state(), RangeView.MachineState.REPLACED, "fornalha trocada por alto-forno");
                        helper.assertTrue(Objects.equals(a.replacedBy(), id(Blocks.BLAST_FURNACE)), "replacedBy: " + a.replacedBy());
                        helper.assertValueEqual(b.state(), RangeView.MachineState.MISSING, "fornalha que virou pedra");
                        helper.assertTrue(b.replacedBy() == null, "a sumida com replacedBy");
                        helper.assertValueEqual(barrel.state(), RangeView.MachineState.LOADED, "barril");
                        helper.assertTrue(barrel.replacedBy() == null, "a carregada com replacedBy");
                        helper.assertTrue(Objects.equals(replacedBy(helper, router, FURNACE_A),
                                id(Blocks.BLAST_FURNACE)), "o roteador não vê a trocada");

                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                                helper.getLevel().registryAccess());
                        RouterSnapshot.STREAM_CODEC.encode(buf, snapshot);
                        helper.assertTrue(RouterSnapshot.STREAM_CODEC.decode(buf).equals(snapshot), "snapshot pelo codec");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** Sozinha no grupo: o grupo passa a ser do bloco novo, com id, nome, perfil e cartões; a frente é relida. */
    @GameTest(template = "empty")
    public static void updateLoneMachineMovesGroup(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, BARREL);
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    MachineGroup group = router.remoteMachines().group(furnaces);
                    group.setName("Fornos");
                    profile(helper, router, FURNACE_A, Direction.SOUTH, PortMode.INSERT);
                    router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                    blastAt(helper, FURNACE_A, Direction.WEST);

                    int index = index(helper, router, FURNACE_A);
                    RemoteMachines.Regroup result = router.updateReplaced(List.of(index));
                    helper.assertValueEqual(result.destinations().get(index), furnaces, "destino");
                    helper.assertTrue(result.cards().isEmpty(), "cartões soltos");
                    helper.assertTrue(router.remoteMachines().group(furnaces) == group, "o grupo não é o mesmo");
                    helper.assertTrue(group.block().equals(id(Blocks.BLAST_FURNACE)), "bloco do grupo: " + group.block());
                    helper.assertValueEqual(group.name(), "Fornos", "nome");
                    helper.assertValueEqual(profileMode(router, furnaces, Direction.SOUTH), PortMode.INSERT, "perfil");
                    helper.assertTrue(router.card(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0)
                            .is(ModItems.FILTER_CARD.get()), "o cartão saiu");
                    helper.assertValueEqual(machine(helper, router, FURNACE_A).front(), Direction.WEST.ordinal(), "frente");
                    helper.assertTrue(replacedBy(helper, router, FURNACE_A) == null, "continua trocada");
                })
                .thenSucceed();
    }

    /** Uma de duas: nasce um grupo do bloco novo com a cópia do perfil, sem nome e sem cartões; o antigo fica. */
    @GameTest(template = "empty")
    public static void updateOneOfTwoCopiesProfile(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    router.remoteMachines().group(furnaces).setName("Fornos");
                    profile(helper, router, FURNACE_A, Direction.SOUTH, PortMode.INSERT);
                    router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                    blastAt(helper, FURNACE_A, Direction.WEST);

                    router.updateReplaced(List.of(index(helper, router, FURNACE_A)));
                    int blasts = groupOf(router, helper.absolutePos(FURNACE_A));
                    MachineGroup group = router.remoteMachines().group(blasts);
                    helper.assertTrue(blasts != furnaces && group.block().equals(id(Blocks.BLAST_FURNACE)), "grupo novo");
                    helper.assertValueEqual(group.name(), "", "nome do grupo novo");
                    helper.assertValueEqual(profileMode(router, blasts, Direction.SOUTH), PortMode.INSERT, "cópia do perfil");
                    helper.assertTrue(router.card(EditTarget.group(blasts), ResourceType.ITEM, Direction.SOUTH, 0).isEmpty(),
                            "o grupo novo levou o cartão");
                    MachineGroup old = router.remoteMachines().group(furnaces);
                    helper.assertTrue(old != null && old.block().equals(id(Blocks.FURNACE)), "o grupo antigo saiu");
                    helper.assertValueEqual(old.name(), "Fornos", "nome do antigo");
                    helper.assertTrue(router.card(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0)
                            .is(ModItems.FILTER_CARD.get()), "o antigo perdeu o cartão");
                    helper.assertValueEqual(groupOf(router, helper.absolutePos(FURNACE_B)), furnaces, "a outra fornalha");

                    // A cópia é funda: mudar o perfil novo não muda o antigo.
                    router.configureFace(EditTarget.group(blasts), ResourceType.ITEM, Direction.SOUTH, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE);
                    helper.assertValueEqual(profileMode(router, furnaces, Direction.SOUTH), PortMode.INSERT,
                            "o perfil antigo mudou com o novo");
                })
                .thenSucceed();
    }

    /**
     * Para um bloco que já tem grupo: as trocadas entram nele e seguem o perfil dele, a exceção continua, e o
     * grupo antigo, que esvaziou, sai com os cartões caindo no roteador. É também o grupo inteiro de uma vez.
     */
    @GameTest(template = "empty")
    public static void updateJoinsExistingGroup(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    blastAt(helper, BLAST, Direction.NORTH);
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BLAST);
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    int blasts = groupOf(router, helper.absolutePos(BLAST));
                    router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                    profile(helper, router, BLAST, Direction.UP, PortMode.INSERT);
                    int indexA = index(helper, router, FURNACE_A);
                    router.configureFace(EditTarget.machine(indexA), ResourceType.ITEM, Direction.DOWN, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE);
                    blastAt(helper, FURNACE_A, Direction.EAST);
                    blastAt(helper, FURNACE_B, Direction.SOUTH);

                    RemoteMachines.Regroup result = router.updateReplaced(List.of(indexA, index(helper, router, FURNACE_B)));
                    helper.assertValueEqual(groupOf(router, helper.absolutePos(FURNACE_A)), blasts, "A no grupo existente");
                    helper.assertValueEqual(groupOf(router, helper.absolutePos(FURNACE_B)), blasts, "B no grupo existente");
                    helper.assertTrue(router.remoteMachines().group(furnaces) == null, "o grupo vazio ficou");
                    helper.assertValueEqual(result.cards().size(), 1, "cartões do grupo que saiu");
                    helper.assertItemEntityPresent(ModItems.FILTER_CARD.get(), ROUTER, 2.0);
                    helper.assertTrue(machine(helper, router, FURNACE_A).hasException(), "a exceção sumiu");
                    helper.assertValueEqual(router.face(EditTarget.machine(indexA), ResourceType.ITEM, Direction.DOWN).mode(),
                            PortMode.EXTRACT, "exceção de A");
                    helper.assertValueEqual(router.face(EditTarget.machine(index(helper, router, FURNACE_B)), ResourceType.ITEM,
                            Direction.UP).mode(), PortMode.INSERT, "B não segue o perfil do grupo novo");
                })
                .thenSucceed();
    }

    /** O grupo inteiro pelo payload, para o mesmo bloco: o grupo troca de bloco e continua escolhido. */
    @GameTest(template = "empty")
    public static void updateWholeGroupKeepsTarget(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.group(menu.containerId, furnaces, id(Blocks.FURNACE))), "não escolheu o grupo");
                        blastAt(helper, FURNACE_A, Direction.EAST);
                        blastAt(helper, FURNACE_B, Direction.SOUTH);

                        helper.assertFalse(ModPayloads.handleUpdateMachine(player,
                                UpdateMachinePayload.group(menu.containerId, furnaces, id(Blocks.BLAST_FURNACE))),
                                "atualizou com o bloco errado");
                        helper.assertTrue(ModPayloads.handleUpdateMachine(player,
                                UpdateMachinePayload.group(menu.containerId, furnaces, id(Blocks.FURNACE))), "não atualizou");
                        helper.assertTrue(router.remoteMachines().group(furnaces).block().equals(id(Blocks.BLAST_FURNACE)),
                                "o grupo não trocou de bloco");
                        helper.assertValueEqual(groupOf(router, helper.absolutePos(FURNACE_B)), furnaces, "B saiu do grupo");
                        helper.assertValueEqual(menu.target(), EditTarget.group(furnaces), "alvo");
                        helper.assertTrue(Objects.equals(menu.targetRef().block(), id(Blocks.BLAST_FURNACE)),
                                "o alvo não tem o bloco novo");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** O grupo escolhido sai: o alvo vai para o grupo de destino. Uma máquina escolhida continua escolhida. */
    @GameTest(template = "empty")
    public static void updateTargetFollowsDestination(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    blastAt(helper, BLAST, Direction.NORTH);
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BLAST);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        int indexA = router.remoteMachines().index(a);
                        int furnaces = groupOf(router, a);
                        int blasts = groupOf(router, helper.absolutePos(BLAST));

                        // A máquina escolhida continua escolhida.
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.machine(menu.containerId, indexA, a)), "não escolheu a máquina");
                        blastAt(helper, FURNACE_A, Direction.EAST);
                        helper.assertTrue(ModPayloads.handleUpdateMachine(player,
                                UpdateMachinePayload.machine(menu.containerId, indexA, a)), "não atualizou a máquina");
                        helper.assertValueEqual(menu.target(), EditTarget.machine(indexA), "alvo da máquina");
                        helper.assertValueEqual(groupOf(router, a), blasts, "A no grupo dos altos-fornos");

                        // O grupo escolhido esvazia e sai: o alvo vai para o grupo de destino.
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.group(menu.containerId, furnaces, id(Blocks.FURNACE))), "não escolheu o grupo");
                        blastAt(helper, FURNACE_B, Direction.SOUTH);
                        helper.assertTrue(ModPayloads.handleUpdateMachine(player,
                                UpdateMachinePayload.group(menu.containerId, furnaces, id(Blocks.FURNACE))), "não atualizou o grupo");
                        helper.assertTrue(router.remoteMachines().group(furnaces) == null, "o grupo vazio ficou");
                        helper.assertValueEqual(menu.target(), EditTarget.group(blasts), "alvo depois do grupo sair");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * O Atualizar pelo roteador aberto à distância ({@link RemoteRouterMenu}, outro {@code RouterEditHost}):
     * a máquina atualizada continua escolhida e o grupo que sai passa o alvo para o grupo de destino.
     */
    @GameTest(template = "empty")
    public static void updateFromRemoteRouterMenu(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    blastAt(helper, BLAST, Direction.NORTH);
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BLAST);
                    ServerPlayer player = TestPlayers.inLevel(helper);
                    try {
                        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
                        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
                        NodeIndex.placedBy(router, player.getUUID());
                        RemoteRouterMenu menu = new RemoteRouterMenu(CONTAINER_ID, player.getInventory(), router,
                                RouterSnapshot.capture(router, player));
                        player.containerMenu = menu;
                        helper.assertTrue(menu.canEdit(player), "o roteador à distância não edita");

                        BlockPos a = helper.absolutePos(FURNACE_A);
                        int indexA = router.remoteMachines().index(a);
                        int furnaces = groupOf(router, a);
                        int blasts = groupOf(router, helper.absolutePos(BLAST));
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.machine(CONTAINER_ID, indexA, a)), "não escolheu a máquina");
                        blastAt(helper, FURNACE_A, Direction.EAST);
                        helper.assertTrue(ModPayloads.handleUpdateMachine(player,
                                UpdateMachinePayload.machine(CONTAINER_ID, indexA, a)), "não atualizou a máquina");
                        helper.assertValueEqual(menu.target(), EditTarget.machine(indexA), "alvo da máquina");
                        helper.assertValueEqual(groupOf(router, a), blasts, "A no grupo dos altos-fornos");

                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.group(CONTAINER_ID, furnaces, id(Blocks.FURNACE))), "não escolheu o grupo");
                        blastAt(helper, FURNACE_B, Direction.SOUTH);
                        helper.assertTrue(ModPayloads.handleUpdateMachine(player,
                                UpdateMachinePayload.group(CONTAINER_ID, furnaces, id(Blocks.FURNACE))), "não atualizou o grupo");
                        helper.assertTrue(router.remoteMachines().group(furnaces) == null, "o grupo vazio ficou");
                        helper.assertValueEqual(menu.target(), EditTarget.group(blasts), "alvo depois do grupo sair");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** O payload com a posição errada é recusado, e uma sumida (pedra) não atualiza. */
    @GameTest(template = "empty")
    public static void updateRefusesWrongPosAndMissing(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        BlockPos b = helper.absolutePos(FURNACE_B);
                        int indexA = router.remoteMachines().index(a);
                        int indexB = router.remoteMachines().index(b);
                        int furnaces = groupOf(router, a);
                        blastAt(helper, FURNACE_A, Direction.EAST);
                        helper.setBlock(FURNACE_B, Blocks.STONE);

                        helper.assertFalse(ModPayloads.handleUpdateMachine(player,
                                UpdateMachinePayload.machine(menu.containerId, indexA, b)), "atualizou com a posição errada");
                        helper.assertValueEqual(groupOf(router, a), furnaces, "A mudou de grupo pela posição errada");
                        helper.assertFalse(ModPayloads.handleUpdateMachine(player,
                                UpdateMachinePayload.machine(menu.containerId, indexB, b)), "atualizou a sumida");
                        helper.assertTrue(router.updateReplaced(List.of(indexB)).isEmpty(), "o roteador atualizou a sumida");
                        helper.assertValueEqual(groupOf(router, b), furnaces, "a sumida mudou de grupo");
                        helper.assertTrue(router.remoteMachines().group(furnaces).block().equals(id(Blocks.FURNACE)),
                                "o grupo trocou de bloco com uma sumida");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** Duas de três para um bloco sem grupo: nasce um grupo só, com as duas, e o antigo fica com a terceira. */
    @GameTest(template = "empty")
    public static void updateTwoOfThreeOneNewGroup(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    helper.setBlock(FURNACE_C, Blocks.FURNACE);
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, FURNACE_C);
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    blastAt(helper, FURNACE_A, Direction.EAST);
                    blastAt(helper, FURNACE_B, Direction.SOUTH);

                    router.updateReplaced(List.of(index(helper, router, FURNACE_A), index(helper, router, FURNACE_B)));
                    int blasts = groupOf(router, helper.absolutePos(FURNACE_A));
                    helper.assertTrue(blasts != furnaces, "A ficou no grupo antigo");
                    helper.assertValueEqual(groupOf(router, helper.absolutePos(FURNACE_B)), blasts, "B noutro grupo novo");
                    helper.assertValueEqual(groupOf(router, helper.absolutePos(FURNACE_C)), furnaces, "C saiu do grupo antigo");
                    long blastGroups = router.remoteMachines().groups().stream()
                            .filter(group -> group.block().equals(id(Blocks.BLAST_FURNACE))).count();
                    helper.assertValueEqual(blastGroups, 1L, "grupos dos altos-fornos");
                })
                .thenSucceed();
    }

    /**
     * O grupo escolhido que só trocou de bloco continua escolhido, com a mesma face, na tela de quem clicou e
     * numa segunda tela aberta no mesmo grupo.
     */
    @GameTest(template = "empty")
    public static void updateKeepsGroupTargetAndFace(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    ServerPlayer[] first = new ServerPlayer[1];
                    ServerPlayer[] second = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, first);
                    RouterMenu other = openMenu(helper, router, second);
                    try {
                        int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                        helper.assertTrue(ModPayloads.handleSelectTarget(first[0],
                                SelectTargetPayload.group(menu.containerId, furnaces, id(Blocks.FURNACE))), "não escolheu o grupo");
                        helper.assertTrue(ModPayloads.handleSelectTarget(second[0],
                                SelectTargetPayload.group(other.containerId, furnaces, id(Blocks.FURNACE))),
                                "não escolheu o grupo na outra tela");
                        menu.select(ResourceType.ITEM, Direction.SOUTH);
                        other.select(ResourceType.ITEM, Direction.SOUTH);
                        blastAt(helper, FURNACE_A, Direction.EAST);

                        helper.assertTrue(ModPayloads.handleUpdateMachine(first[0],
                                UpdateMachinePayload.group(menu.containerId, furnaces, id(Blocks.FURNACE))), "não atualizou");
                        helper.assertValueEqual(menu.target(), EditTarget.group(furnaces), "alvo de quem clicou");
                        helper.assertValueEqual(menu.selectedFace(), Direction.SOUTH, "face de quem clicou");
                        helper.assertTrue(Objects.equals(menu.targetRef().block(), id(Blocks.BLAST_FURNACE)),
                                "o alvo não tem o bloco novo");
                        helper.assertValueEqual(other.validTarget(), EditTarget.group(furnaces), "alvo da outra tela");
                        helper.assertValueEqual(other.selectedFace(), Direction.SOUTH, "face da outra tela");
                    } finally {
                        removePlayer(helper, first[0]);
                        removePlayer(helper, second[0]);
                    }
                })
                .thenSucceed();
    }

    /** Sem o upgrade valendo, o payload é recusado e a trocada fica no grupo. */
    @GameTest(template = "empty")
    public static void updateRefusedWithoutUpgrade(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        int index = router.remoteMachines().index(a);
                        int furnaces = groupOf(router, a);
                        blastAt(helper, FURNACE_A, Direction.EAST);
                        router.setRangeUpgrade(ItemStack.EMPTY);

                        helper.assertFalse(ModPayloads.handleUpdateMachine(holder[0],
                                UpdateMachinePayload.machine(menu.containerId, index, a)), "atualizou a máquina sem o upgrade");
                        helper.assertFalse(ModPayloads.handleUpdateMachine(holder[0],
                                UpdateMachinePayload.group(menu.containerId, furnaces, id(Blocks.FURNACE))),
                                "atualizou o grupo sem o upgrade");
                        helper.assertTrue(router.remoteMachines().group(furnaces).block().equals(id(Blocks.FURNACE)),
                                "o grupo trocou de bloco sem o upgrade");
                    } finally {
                        removePlayer(helper, holder[0]);
                    }
                })
                .thenSucceed();
    }

    /**
     * O motor move para a máquina atualizada na face certa: a fornalha remota (frente Norte) vira um alto-forno
     * com frente Leste, e o perfil "Trás → Inserir" passa a ser a face Oeste dele.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void engineMovesToUpdated(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
        container(helper, CHEST).setItem(0, new ItemStack(Items.COAL, 8));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    blastAt(helper, REMOTE_CHEST, Direction.EAST);
                    helper.assertFalse(router.updateReplaced(List.of(index(helper, router, REMOTE_CHEST))).isEmpty(),
                            "não atualizou");
                    // Trás, no quadro canônico (frente = Norte), é o Sul.
                    profile(helper, router, REMOTE_CHEST, Direction.SOUTH, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(container(helper, REMOTE_CHEST).getItem(1).getCount() == 8,
                        "o carvão não chegou ao combustível do alto-forno"))
                .thenExecute(() -> {
                    helper.assertTrue(remotePort(helper, router, REMOTE_CHEST, Direction.WEST), "Trás da frente Leste não é Oeste");
                    helper.assertFalse(remotePort(helper, router, REMOTE_CHEST, Direction.NORTH), "porta na face da frente antiga");
                })
                .thenSucceed();
    }
}
