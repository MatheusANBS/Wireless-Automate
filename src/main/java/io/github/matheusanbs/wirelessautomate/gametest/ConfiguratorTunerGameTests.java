package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.tuner.ConfiguratorTuner;
import io.github.matheusanbs.wirelessautomate.item.tuner.TunerPending;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O Afinador do Configurador: a análise de uma fornalha, de um barril e de uma bateria, o segundo clique que aplica
 * (na máquina ou, com Shift, no grupo), o prazo, as recusas e as redes que ficam. O roteador de alcance fica no baú
 * (1, 1, 1) com as máquinas do {@link RangeTestSupport#routerWithMachines}; a fornalha A tem a frente para Leste.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ConfiguratorTunerGameTests {
    private static final int DOWN = Direction.DOWN.ordinal();
    private static final int UP = Direction.UP.ordinal();

    private static ServerPlayer player(GameTestHelper helper, RouterBlockEntity router) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        return player;
    }

    /** O modo que vale no alvo para itens no lado {@code side} da máquina. */
    private static PortMode mode(RouterBlockEntity router, EditTarget target, RemoteSide side) {
        return router.face(target, ResourceType.ITEM, Direction.values()[side.canonical()]).mode();
    }

    /** Os modos da fornalha depois de aplicar: Cima e Trás Insere, Baixo Extrai, o resto Nenhum. */
    private static void assertFurnaceModes(GameTestHelper helper, RouterBlockEntity router, EditTarget target) {
        helper.assertValueEqual(mode(router, target, RemoteSide.TOP), PortMode.INSERT, "Cima");
        helper.assertValueEqual(mode(router, target, RemoteSide.BACK), PortMode.INSERT, "Trás (o combustível)");
        helper.assertValueEqual(mode(router, target, RemoteSide.BOTTOM), PortMode.EXTRACT, "Baixo");
        helper.assertValueEqual(mode(router, target, RemoteSide.FRONT), PortMode.NONE, "Frente");
        helper.assertValueEqual(mode(router, target, RemoteSide.LEFT), PortMode.NONE, "Esquerda");
        helper.assertValueEqual(mode(router, target, RemoteSide.RIGHT), PortMode.NONE, "Direita");
    }

    /** A fornalha: a proposta não muda nada, o segundo clique aplica na exceção dela, e as redes ficam. */
    @GameTest(template = "empty")
    public static void furnaceProposalThenSecondClickApplies(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = player(helper, router);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    linkInNetwork(helper, router, FURNACE_A);
                    UUID network = router.networkId(ResourceType.ITEM);
                    BlockPos furnace = helper.absolutePos(FURNACE_A);
                    EditTarget target = EditTarget.machine(router.remoteMachines().index(furnace));

                    ConfiguratorTuner.click(player, furnace, false);
                    TunerPending.Proposal proposal = TunerPending.get(player.getUUID());
                    helper.assertTrue(proposal != null, "sem proposta");
                    PortMode[] items = proposal.modes().get(ResourceType.ITEM);
                    helper.assertTrue(items != null, "a proposta não tem itens");
                    helper.assertValueEqual(items[UP], PortMode.INSERT, "proposta em Cima");
                    helper.assertValueEqual(items[DOWN], PortMode.EXTRACT, "proposta em Baixo");
                    // Frente Leste: Trás é Oeste; os outros lados do combustível ficam em Nenhum (sem duplicar).
                    helper.assertValueEqual(items[Direction.WEST.ordinal()], PortMode.INSERT, "proposta em Trás");
                    helper.assertValueEqual(items[Direction.NORTH.ordinal()], PortMode.NONE, "proposta na Direita");
                    helper.assertValueEqual(items[Direction.SOUTH.ordinal()], PortMode.NONE, "proposta na Esquerda");
                    helper.assertValueEqual(items[Direction.EAST.ordinal()], PortMode.NONE, "proposta na Frente");
                    helper.assertFalse(proposal.modes().containsKey(ResourceType.ENERGY), "energia numa fornalha");
                    helper.assertValueEqual(mode(router, target, RemoteSide.TOP), PortMode.NONE,
                            "o primeiro clique mudou a máquina");

                    ConfiguratorTuner.click(player, furnace, false);
                    helper.assertTrue(TunerPending.get(player.getUUID()) == null, "a proposta ficou depois de aplicar");
                    assertFurnaceModes(helper, router, target);
                    helper.assertTrue(router.remoteMachines().machine(target.id()).hasException(),
                            "a máquina devia ter ficado com exceção");
                    for (Direction side : Direction.values()) {
                        helper.assertTrue(Objects.equals(router.face(target, ResourceType.ITEM, side).network(), network),
                                "a rede da face " + side + " mudou");
                    }
                })
                .thenSucceed();
    }

    /** O barril (armazenamento): só Cima vira Armazém; a face já configurada fica como estava. */
    @GameTest(template = "empty")
    public static void barrelGetsStorageOnOneFaceOnly(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = player(helper, router);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, BARREL);
                    profile(helper, router, BARREL, Direction.DOWN, PortMode.EXTRACT);
                    BlockPos barrel = helper.absolutePos(BARREL);
                    EditTarget target = EditTarget.machine(router.remoteMachines().index(barrel));

                    ConfiguratorTuner.click(player, barrel, false);
                    TunerPending.Proposal proposal = TunerPending.get(player.getUUID());
                    helper.assertTrue(proposal != null && proposal.modes().containsKey(ResourceType.ITEM),
                            "sem proposta de itens");
                    PortMode[] items = proposal.modes().get(ResourceType.ITEM);
                    for (int face = 0; face < items.length; face++) {
                        helper.assertTrue(items[face] == (face == UP ? PortMode.BOTH : null), "proposta na face " + face);
                    }

                    ConfiguratorTuner.click(player, barrel, false);
                    helper.assertValueEqual(mode(router, target, RemoteSide.TOP), PortMode.BOTH, "Cima");
                    helper.assertValueEqual(mode(router, target, RemoteSide.BOTTOM), PortMode.EXTRACT,
                            "Baixo devia ficar como estava");
                    helper.assertValueEqual(mode(router, target, RemoteSide.FRONT), PortMode.NONE, "Frente");
                })
                .thenSucceed();
    }

    /** Uma máquina só de energia (a Bateria, presa): Armazém em Cima, no perfil do grupo da presa sozinha. */
    @GameTest(template = "empty")
    public static void energyOnlyMachineGetsStorageOnTheAttached(GameTestHelper helper) {
        BlockPos machine = new BlockPos(1, 1, 1);
        helper.setBlock(machine, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState());
        helper.setBlock(machine.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(machine.above());
        router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "bateria").id());
        ServerPlayer player = player(helper, router);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    BlockPos battery = helper.absolutePos(machine);
                    ConfiguratorTuner.click(player, battery, false);
                    TunerPending.Proposal proposal = TunerPending.get(player.getUUID());
                    helper.assertTrue(proposal != null && proposal.modes().containsKey(ResourceType.ENERGY),
                            "sem proposta de energia");
                    helper.assertFalse(proposal.modes().containsKey(ResourceType.ITEM), "itens numa bateria");
                    PortMode[] energy = proposal.modes().get(ResourceType.ENERGY);
                    for (int face = 0; face < energy.length; face++) {
                        helper.assertTrue(energy[face] == (face == UP ? PortMode.BOTH : null), "proposta na face " + face);
                    }

                    ConfiguratorTuner.click(player, battery, false);
                    helper.assertValueEqual(router.face(ResourceType.ENERGY, Direction.UP).mode(), PortMode.BOTH,
                            "Cima da presa");
                    helper.assertValueEqual(router.face(ResourceType.ENERGY, Direction.DOWN).mode(), PortMode.NONE,
                            "Baixo da presa");
                    MachineGroup group = router.remoteMachines().attachedGroup();
                    helper.assertTrue(group != null, "a presa ficou sem grupo");
                    helper.assertValueEqual(group.profile().face(ResourceType.ENERGY, RemoteSide.TOP).mode(),
                            PortMode.BOTH, "a presa sozinha devia mudar o perfil");
                    helper.assertFalse(router.remoteMachines().attached().hasException(), "a presa ficou com exceção");
                })
                .thenSucceed();
    }

    /** Shift no segundo clique aplica no perfil do grupo: a outra fornalha segue, e as redes ficam. */
    @GameTest(template = "empty")
    public static void shiftAppliesToTheWholeGroup(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = player(helper, router);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    linkInNetwork(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    UUID network = router.networkId(ResourceType.ITEM);
                    BlockPos furnaceA = helper.absolutePos(FURNACE_A);
                    BlockPos furnaceB = helper.absolutePos(FURNACE_B);
                    int group = groupOf(router, furnaceA);
                    helper.assertValueEqual(groupOf(router, furnaceB), group, "as fornalhas em grupos diferentes");

                    ConfiguratorTuner.click(player, furnaceA, false);
                    ConfiguratorTuner.click(player, furnaceA, true);
                    assertFurnaceModes(helper, router, EditTarget.group(group));
                    assertFurnaceModes(helper, router, EditTarget.machine(router.remoteMachines().index(furnaceB)));
                    helper.assertFalse(router.remoteMachines().machine(router.remoteMachines().index(furnaceA))
                            .hasException(), "a fornalha A ficou com exceção");
                    for (Direction side : Direction.values()) {
                        helper.assertTrue(Objects.equals(
                                router.face(EditTarget.group(group), ResourceType.ITEM, side).network(), network),
                                "a rede da face " + side + " do perfil mudou");
                    }
                })
                .thenSucceed();
    }

    /** Depois do prazo o segundo clique não aplica: refaz a análise. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void expiredProposalDoesNotApply(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = player(helper, router);
        BlockPos[] furnace = new BlockPos[1];
        EditTarget[] target = new EditTarget[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    furnace[0] = helper.absolutePos(FURNACE_A);
                    target[0] = EditTarget.machine(router.remoteMachines().index(furnace[0]));
                    ConfiguratorTuner.click(player, furnace[0], false);
                    helper.assertTrue(TunerPending.get(player.getUUID()) != null, "sem proposta");
                })
                .thenIdle(TunerPending.WINDOW + 1)
                .thenExecute(() -> {
                    ConfiguratorTuner.click(player, furnace[0], false);
                    helper.assertValueEqual(mode(router, target[0], RemoteSide.TOP), PortMode.NONE,
                            "a proposta vencida foi aplicada");
                    helper.assertTrue(TunerPending.get(player.getUUID()) != null, "o clique não refez a análise");
                })
                .thenSucceed();
    }

    /** Clicar em outra máquina refaz a análise para ela, sem aplicar na primeira. */
    @GameTest(template = "empty")
    public static void clickingAnotherMachineAnalyzesIt(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = player(helper, router);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, BARREL);
                    BlockPos furnace = helper.absolutePos(FURNACE_A);
                    BlockPos barrel = helper.absolutePos(BARREL);
                    ConfiguratorTuner.click(player, furnace, false);
                    ConfiguratorTuner.click(player, barrel, false);
                    TunerPending.Proposal proposal = TunerPending.get(player.getUUID());
                    helper.assertTrue(proposal != null && proposal.machine().equals(barrel), "a proposta não é do barril");
                    EditTarget target = EditTarget.machine(router.remoteMachines().index(furnace));
                    helper.assertValueEqual(mode(router, target, RemoteSide.TOP), PortMode.NONE,
                            "o clique no barril aplicou na fornalha");
                    ConfiguratorTuner.cancel(player);
                    helper.assertTrue(TunerPending.get(player.getUUID()) == null, "o clique no ar não esqueceu a proposta");
                })
                .thenSucceed();
    }

    /** Sem roteador que chegue na máquina nada é analisado. */
    @GameTest(template = "empty")
    public static void machineWithoutRouterIsRefused(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = player(helper, router);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    ConfiguratorTuner.click(player, helper.absolutePos(FURNACE_B), false);
                    helper.assertTrue(TunerPending.get(player.getUUID()) == null, "analisou uma máquina sem roteador");
                })
                .thenSucceed();
    }

    /**
     * A máquina de outro jogador: o roteador protegido (como num claim) não serve, e quem não pode construir (o modo
     * aventura) também é recusado.
     */
    @GameTest(template = "empty")
    public static void protectedRouterOrPlayerIsRefused(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = player(helper, router);
        BlockPos routerPos = helper.absolutePos(ROUTER);
        Consumer<PlayerInteractEvent.RightClickBlock> claim = event -> {
            if (event.getEntity() == player && event.getPos().equals(routerPos)) {
                event.setCanceled(true);
            }
        };
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    BlockPos furnace = helper.absolutePos(FURNACE_A);
                    try {
                        NeoForge.EVENT_BUS.addListener(claim);
                        ConfiguratorTuner.click(player, furnace, false);
                        helper.assertTrue(TunerPending.get(player.getUUID()) == null,
                                "analisou pelo roteador protegido");
                    } finally {
                        NeoForge.EVENT_BUS.unregister(claim);
                    }
                    player.getAbilities().mayBuild = false;
                    ConfiguratorTuner.click(player, furnace, false);
                    helper.assertTrue(TunerPending.get(player.getUUID()) == null, "analisou sem poder construir");
                    player.getAbilities().mayBuild = true;
                    ConfiguratorTuner.click(player, furnace, false);
                    helper.assertTrue(TunerPending.get(player.getUUID()) != null, "sem a proteção devia analisar");
                    TunerPending.forget(player.getUUID());
                })
                .thenSucceed();
    }
}
