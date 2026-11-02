package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.tuner.ConfiguratorTuner;
import io.github.matheusanbs.wirelessautomate.item.tuner.TunerPending;
import io.github.matheusanbs.wirelessautomate.item.tuner.TunerPlan;
import io.github.matheusanbs.wirelessautomate.item.tuner.TunerProbe;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * O Afinador do Configurador: a análise de uma fornalha, de um barril e de uma bateria, o segundo clique que aplica
 * (na máquina ou, com Shift, no grupo), o intervalo mínimo e o prazo, as recusas e as redes que ficam, e a leitura de
 * faces de teste (saída cheia, slot vazio que recusa tudo, energia). O roteador de alcance fica no baú (1, 1, 1) com
 * as máquinas do {@link RangeTestSupport#routerWithMachines}; a fornalha A tem a frente para Leste.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ConfiguratorTunerGameTests {
    private static final int DOWN = Direction.DOWN.ordinal();
    private static final int UP = Direction.UP.ordinal();
    private static final String KEY = "item.wirelessautomate.configurator.tuner.";

    private static ServerPlayer player(GameTestHelper helper, RouterBlockEntity router) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        return player;
    }

    /** O modo que vale no alvo para itens no lado {@code side} da máquina. */
    private static PortMode mode(RouterBlockEntity router, EditTarget target, RemoteSide side) {
        return router.face(target, ResourceType.ITEM, Direction.values()[side.canonical()]).mode();
    }

    private static EditTarget machine(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        return EditTarget.machine(router.remoteMachines().index(helper.absolutePos(relative)));
    }

    /** A chave de tradução do aviso. */
    private static String key(@Nullable Component message) {
        return message != null && message.getContents() instanceof TranslatableContents contents ? contents.getKey()
                : String.valueOf(message);
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
        UUID[] network = new UUID[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    linkInNetwork(helper, router, FURNACE_A);
                    network[0] = router.networkId(ResourceType.ITEM);
                    helper.assertValueEqual(key(ConfiguratorTuner.click(player, helper.absolutePos(FURNACE_A), false)),
                            KEY + "proposal", "aviso do primeiro clique");
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
                    helper.assertValueEqual(mode(router, machine(helper, router, FURNACE_A), RemoteSide.TOP),
                            PortMode.NONE, "o primeiro clique mudou a máquina");
                })
                .thenIdle(TunerPending.CONFIRM_DELAY)
                .thenExecute(() -> {
                    EditTarget target = machine(helper, router, FURNACE_A);
                    helper.assertValueEqual(key(ConfiguratorTuner.click(player, helper.absolutePos(FURNACE_A), false)),
                            KEY + "applied", "aviso do segundo clique");
                    helper.assertTrue(TunerPending.get(player.getUUID()) == null, "a proposta ficou depois de aplicar");
                    assertFurnaceModes(helper, router, target);
                    helper.assertTrue(router.remoteMachines().machine(target.id()).hasException(),
                            "a máquina devia ter ficado com exceção");
                    for (Direction side : Direction.values()) {
                        helper.assertTrue(Objects.equals(router.face(target, ResourceType.ITEM, side).network(),
                                network[0]), "a rede da face " + side + " mudou");
                    }
                })
                .thenSucceed();
    }

    /** O segundo clique cedo demais (o botão segurado) não aplica: a proposta continua esperando. */
    @GameTest(template = "empty")
    public static void quickSecondClickDoesNotApply(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = player(helper, router);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    BlockPos furnace = helper.absolutePos(FURNACE_A);
                    ConfiguratorTuner.click(player, furnace, false);
                    helper.assertTrue(player.getCooldowns().isOnCooldown(ModItems.CONFIGURATOR.get()),
                            "o Configurador não descansou depois da análise");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(ConfiguratorTuner.click(player, helper.absolutePos(FURNACE_A), false) == null,
                            "o clique cedo demais teve aviso");
                    helper.assertValueEqual(mode(router, machine(helper, router, FURNACE_A), RemoteSide.TOP),
                            PortMode.NONE, "o clique cedo demais aplicou");
                    helper.assertTrue(TunerPending.get(player.getUUID()) != null, "a proposta sumiu");
                    TunerPending.forget(player.getUUID());
                })
                .thenSucceed();
    }

    /** O barril (armazenamento): só Cima vira Armazém e as outras faces, a de Extrai inclusive, vão para Nenhum. */
    @GameTest(template = "empty")
    public static void barrelGetsStorageOnOneFaceAndTheOthersNone(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = player(helper, router);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, BARREL);
                    profile(helper, router, BARREL, Direction.DOWN, PortMode.EXTRACT);
                    ConfiguratorTuner.click(player, helper.absolutePos(BARREL), false);
                    TunerPending.Proposal proposal = TunerPending.get(player.getUUID());
                    helper.assertTrue(proposal != null && proposal.modes().containsKey(ResourceType.ITEM),
                            "sem proposta de itens");
                    PortMode[] items = proposal.modes().get(ResourceType.ITEM);
                    for (int face = 0; face < items.length; face++) {
                        helper.assertTrue(items[face] == (face == UP ? PortMode.BOTH : PortMode.NONE),
                                "proposta na face " + face + ": " + items[face]);
                    }
                })
                .thenIdle(TunerPending.CONFIRM_DELAY)
                .thenExecute(() -> {
                    EditTarget target = machine(helper, router, BARREL);
                    ConfiguratorTuner.click(player, helper.absolutePos(BARREL), false);
                    helper.assertValueEqual(mode(router, target, RemoteSide.TOP), PortMode.BOTH, "Cima");
                    helper.assertValueEqual(mode(router, target, RemoteSide.BOTTOM), PortMode.NONE,
                            "Baixo devia sair do Extrai");
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
                    ConfiguratorTuner.click(player, helper.absolutePos(machine), false);
                    TunerPending.Proposal proposal = TunerPending.get(player.getUUID());
                    helper.assertTrue(proposal != null && proposal.modes().containsKey(ResourceType.ENERGY),
                            "sem proposta de energia");
                    helper.assertFalse(proposal.modes().containsKey(ResourceType.ITEM), "itens numa bateria");
                    PortMode[] energy = proposal.modes().get(ResourceType.ENERGY);
                    for (int face = 0; face < energy.length; face++) {
                        helper.assertTrue(energy[face] == (face == UP ? PortMode.BOTH : PortMode.NONE),
                                "proposta na face " + face + ": " + energy[face]);
                    }
                })
                .thenIdle(TunerPending.CONFIRM_DELAY)
                .thenExecute(() -> {
                    ConfiguratorTuner.click(player, helper.absolutePos(machine), false);
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
        UUID[] network = new UUID[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    linkInNetwork(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    network[0] = router.networkId(ResourceType.ITEM);
                    helper.assertValueEqual(groupOf(router, helper.absolutePos(FURNACE_B)),
                            groupOf(router, helper.absolutePos(FURNACE_A)), "as fornalhas em grupos diferentes");
                    ConfiguratorTuner.click(player, helper.absolutePos(FURNACE_A), false);
                })
                .thenIdle(TunerPending.CONFIRM_DELAY)
                .thenExecute(() -> {
                    int group = groupOf(router, helper.absolutePos(FURNACE_A));
                    helper.assertValueEqual(key(ConfiguratorTuner.click(player, helper.absolutePos(FURNACE_A), true)),
                            KEY + "applied_group", "aviso do grupo");
                    assertFurnaceModes(helper, router, EditTarget.group(group));
                    assertFurnaceModes(helper, router, machine(helper, router, FURNACE_B));
                    helper.assertFalse(router.remoteMachines().machine(machine(helper, router, FURNACE_A).id())
                            .hasException(), "a fornalha A ficou com exceção");
                    for (Direction side : Direction.values()) {
                        helper.assertTrue(Objects.equals(
                                router.face(EditTarget.group(group), ResourceType.ITEM, side).network(), network[0]),
                                "a rede da face " + side + " do perfil mudou");
                    }
                })
                .thenSucceed();
    }

    /** Shift na presa: o perfil do grupo dela muda, e ela continua sem exceção. */
    @GameTest(template = "empty")
    public static void shiftOnTheAttachedChangesItsGroupProfile(GameTestHelper helper) {
        BlockPos machine = new BlockPos(1, 1, 1);
        helper.setBlock(machine, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
        helper.setBlock(machine.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(machine.above());
        router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "presa").id());
        ServerPlayer player = player(helper, router);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> ConfiguratorTuner.click(player, helper.absolutePos(machine), false))
                .thenIdle(TunerPending.CONFIRM_DELAY)
                .thenExecute(() -> {
                    helper.assertValueEqual(key(ConfiguratorTuner.click(player, helper.absolutePos(machine), true)),
                            KEY + "applied_group", "aviso do grupo");
                    MachineGroup group = router.remoteMachines().attachedGroup();
                    helper.assertTrue(group != null, "a presa ficou sem grupo");
                    assertFurnaceModes(helper, router, EditTarget.group(group.id()));
                    helper.assertFalse(router.remoteMachines().attached().hasException(), "a presa ficou com exceção");
                    // A presa pela face absoluta: frente Leste, então Trás é Oeste.
                    helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.WEST).mode(), PortMode.INSERT,
                            "Trás da presa");
                    helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.DOWN).mode(), PortMode.EXTRACT,
                            "Baixo da presa");
                })
                .thenSucceed();
    }

    /** Depois do prazo o segundo clique não aplica: refaz a análise. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void expiredProposalDoesNotApply(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = player(helper, router);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    ConfiguratorTuner.click(player, helper.absolutePos(FURNACE_A), false);
                    helper.assertTrue(TunerPending.get(player.getUUID()) != null, "sem proposta");
                })
                .thenIdle(TunerPending.WINDOW + 1)
                .thenExecute(() -> {
                    helper.assertValueEqual(key(ConfiguratorTuner.click(player, helper.absolutePos(FURNACE_A), false)),
                            KEY + "proposal", "o clique depois do prazo devia refazer a análise");
                    helper.assertValueEqual(mode(router, machine(helper, router, FURNACE_A), RemoteSide.TOP),
                            PortMode.NONE, "a proposta vencida foi aplicada");
                    TunerPending.forget(player.getUUID());
                })
                .thenSucceed();
    }

    /** Clicar em outra máquina refaz a análise para ela, sem aplicar na primeira; o clique no ar a esquece. */
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
                    ConfiguratorTuner.click(player, helper.absolutePos(FURNACE_A), false);
                })
                .thenIdle(TunerPending.CONFIRM_DELAY)
                .thenExecute(() -> {
                    BlockPos barrel = helper.absolutePos(BARREL);
                    ConfiguratorTuner.click(player, barrel, false);
                    TunerPending.Proposal proposal = TunerPending.get(player.getUUID());
                    helper.assertTrue(proposal != null && proposal.machine().equals(barrel), "a proposta não é do barril");
                    helper.assertValueEqual(mode(router, machine(helper, router, FURNACE_A), RemoteSide.TOP),
                            PortMode.NONE, "o clique no barril aplicou na fornalha");
                    ConfiguratorTuner.cancel(player);
                    helper.assertTrue(TunerPending.get(player.getUUID()) == null, "o clique no ar não esqueceu a proposta");
                })
                .thenSucceed();
    }

    /** O bloco trocado entre os dois cliques: o segundo refaz a análise para o bloco novo e não aplica a velha. */
    @GameTest(template = "empty")
    public static void replacedBlockBetweenClicksIsAnalyzedAgain(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = player(helper, router);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    ConfiguratorTuner.click(player, helper.absolutePos(FURNACE_A), false);
                    helper.setBlock(FURNACE_A, Blocks.BARREL);
                })
                .thenIdle(TunerPending.CONFIRM_DELAY)
                .thenExecute(() -> {
                    helper.assertValueEqual(key(ConfiguratorTuner.click(player, helper.absolutePos(FURNACE_A), false)),
                            KEY + "proposal", "o segundo clique no bloco trocado devia refazer a análise");
                    TunerPending.Proposal proposal = TunerPending.get(player.getUUID());
                    helper.assertTrue(proposal != null && proposal.block() == Blocks.BARREL, "a proposta não é do barril");
                    helper.assertValueEqual(mode(router, machine(helper, router, FURNACE_A), RemoteSide.BOTTOM),
                            PortMode.NONE, "a proposta da fornalha foi aplicada no barril");
                    TunerPending.forget(player.getUUID());
                })
                .thenSucceed();
    }

    /** Sem roteador que chegue na máquina nada é analisado, com o aviso de vincular. */
    @GameTest(template = "empty")
    public static void machineWithoutRouterIsRefused(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = player(helper, router);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    helper.assertValueEqual(key(ConfiguratorTuner.click(player, helper.absolutePos(FURNACE_B), false)),
                            KEY + "not_linked", "aviso sem roteador");
                    helper.assertTrue(TunerPending.get(player.getUUID()) == null, "analisou uma máquina sem roteador");
                })
                .thenSucceed();
    }

    /**
     * A máquina de outro jogador: o roteador protegido (como num claim) não serve, com o aviso de acesso, e quem não
     * pode construir (o modo aventura) também é recusado, com outro aviso.
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
                        helper.assertValueEqual(key(ConfiguratorTuner.click(player, furnace, false)),
                                KEY + "no_access", "aviso do roteador protegido");
                        helper.assertTrue(TunerPending.get(player.getUUID()) == null,
                                "analisou pelo roteador protegido");
                    } finally {
                        NeoForge.EVENT_BUS.unregister(claim);
                    }
                    player.getAbilities().mayBuild = false;
                    helper.assertValueEqual(key(ConfiguratorTuner.click(player, furnace, false)), KEY + "protected",
                            "aviso de quem não constrói");
                    helper.assertTrue(TunerPending.get(player.getUUID()) == null, "analisou sem poder construir");
                    player.getAbilities().mayBuild = true;
                    ConfiguratorTuner.click(player, furnace, false);
                    helper.assertTrue(TunerPending.get(player.getUUID()) != null, "sem a proteção devia analisar");
                    TunerPending.forget(player.getUUID());
                })
                .thenSucceed();
    }

    /**
     * Faces de uma máquina de teste: a saída cheia (o {@code isItemValid} diz sim, mas o próprio item não entra) é
     * saída de verdade; o slot vazio que recusa tudo é saída fraca; a energia que não recebe nem dá não tem papel, e a
     * ingênua (recebe e dá) fica AMBOS, que o plano trata como entrada numa máquina.
     */
    @GameTest(template = "empty")
    public static void testMachineFacesAreClassified(GameTestHelper helper) {
        List<ItemStack> probes = List.of(new ItemStack(Items.COBBLESTONE), new ItemStack(Items.COAL),
                new ItemStack(Items.IRON_ORE));
        ItemStackHandler fullOutput = new ItemStackHandler(1);
        fullOutput.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        TunerPlan.Face full = TunerProbe.items(fullOutput, probes);
        helper.assertValueEqual(full.role(), TunerPlan.Role.OUT, "saída cheia");
        helper.assertFalse(full.weakOut(), "a saída cheia é de verdade");

        ItemStackHandler refuses = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return false;
            }
        };
        TunerPlan.Face empty = TunerProbe.items(refuses, probes);
        helper.assertValueEqual(empty.role(), TunerPlan.Role.OUT, "slot vazio que recusa tudo");
        helper.assertTrue(empty.weakOut(), "o slot vazio é saída fraca");

        TunerPlan.Face open = TunerProbe.items(new ItemStackHandler(2), probes);
        helper.assertValueEqual(open.role(), TunerPlan.Role.BOTH, "slots livres");
        helper.assertTrue(open.open(), "slots livres são abertos");

        TunerPlan.Face dead = TunerProbe.energy(new EnergyStorage(1000, 0, 0));
        helper.assertValueEqual(dead.role(), TunerPlan.Role.NONE, "energia que não recebe nem dá");
        helper.assertTrue(dead.present(), "a face tem a capability");
        TunerPlan.Face naive = TunerProbe.energy(new EnergyStorage(1000));
        helper.assertValueEqual(naive.role(), TunerPlan.Role.BOTH, "energia ingênua");
        helper.succeed();
    }
}
