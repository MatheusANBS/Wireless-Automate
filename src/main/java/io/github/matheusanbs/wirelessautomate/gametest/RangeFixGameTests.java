package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeProbe;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Correções do roteador de alcance: o resgate da sobra na máquina da porta que recebe, o destino
 * cheio que conta as remotas sem criar porta e o grupo vinculado pelo Vinculador, que nasce sem rede.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeFixGameTests {
    private static final BlockPos LYING = new BlockPos(0, 1, 0);
    private static final BlockPos NORMAL = new BlockPos(2, 1, 2);

    /**
     * A presa é uma máquina de entrada e saída (a sobra não volta pelo slot de saída) e os dois destinos
     * são remotos: o que o destino que mente recusa tem de ir para o remoto normal, e não para a
     * máquina presa, onde o resgate antigo (que lia o nó e não a porta) a devolveria pela entrada.
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
     * Regra D1 no perfil de um grupo sem rede: escolher Insere numa face põe só aquela face (aquele tipo)
     * na rede ativa, na mesma edição; as outras faces e os outros tipos continuam sem rede.
     */
    @GameTest(template = "empty")
    public static void groupModeJoinsActiveNetworkOnlyThatFace(GameTestHelper helper) {
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
                                UUID expected = type == ResourceType.ITEM && face == Direction.UP ? active : null;
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
}
