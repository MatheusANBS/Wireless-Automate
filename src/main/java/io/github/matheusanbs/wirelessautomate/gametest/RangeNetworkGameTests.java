package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Roteador de alcance, a rede por face: o Vinculador vincula sem rede, o perfil em duas redes e a exceção
 * que muda a rede de uma face.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeNetworkGameTests {
    /**
     * Uma segunda fornalha, vinculada com outra rede ativa, entra no grupo que já existe e o perfil não
     * muda (nem a rede que o dono pôs nele, nem o resto); ela não ganha exceção.
     */
    @GameTest(template = "empty")
    public static void existingGroupKeepsNetworks(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        UUID a = activeNetworkOf(helper, player, "ativa A");
                        router.setRangeUpgrade(rangeUpgrade());
                        click(helper, player, ROUTER, false);
                        helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(), RangeLinks.Status.LINKED,
                                "clique na fornalha A");
                        profile(helper, router, FURNACE_A, Direction.SOUTH, PortMode.EXTRACT);
                        int group = groupOf(router, helper.absolutePos(FURNACE_A));
                        router.setFaceNetwork(EditTarget.group(group), ResourceType.ITEM, Direction.SOUTH, a);
                        MachineConfig before = router.remoteMachines().group(group).profile().copy();

                        UUID b = activeNetworkOf(helper, player, "ativa B");
                        helper.assertValueEqual(toggle(helper, player, FURNACE_B).status(), RangeLinks.Status.LINKED,
                                "clique na fornalha B");
                        helper.assertValueEqual(groupOf(router, helper.absolutePos(FURNACE_B)), group, "outro grupo");
                        helper.assertTrue(router.remoteMachines().group(group).profile().sameAs(before),
                                "o perfil do grupo mudou");
                        helper.assertTrue(Objects.equals(router.face(EditTarget.group(group), ResourceType.ITEM,
                                Direction.SOUTH).network(), a), "a rede do perfil mudou (ativa " + b + ")");
                        RemoteMachines remotes = router.remoteMachines();
                        MachineConfig exception = remotes.machine(remotes.index(helper.absolutePos(FURNACE_B))).exception();
                        helper.assertTrue(exception != null && exception.face(ResourceType.ITEM, RemoteSide.BACK).mode() == PortMode.NONE
                                && exception.face(ResourceType.ITEM, RemoteSide.BACK).network() == null,
                                "a fornalha B não entrou isolada, sem herdar o perfil");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Sem rede ativa o grupo nasce sem rede, e o vínculo não cria rede; com a rede ativa privada de
     * outro dono (que o jogador não pode usar), também sem rede.
     */
    @GameTest(template = "empty")
    public static void noActiveNetworkGroupWithoutNetwork(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
                        helper.assertTrue(data.activeNetwork(player.getUUID()) == null, "jogador novo com rede ativa");
                        router.setRangeUpgrade(rangeUpgrade());
                        click(helper, player, ROUTER, false);
                        helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(), RangeLinks.Status.LINKED,
                                "clique na fornalha");
                        assertProfileNetwork(helper, router, FURNACE_A, null, "perfil sem rede ativa");
                        helper.assertTrue(data.activeNetwork(player.getUUID()) == null, "o vínculo deixou uma rede ativa");
                        for (var network : data.networks()) {
                            helper.assertFalse(network.owner().equals(player.getUUID()),
                                    "o vínculo criou a rede " + network.name());
                        }

                        UUID foreign = data.create(UUID.randomUUID(), "alheia").id();
                        data.setActiveNetwork(player.getUUID(), foreign);
                        helper.assertValueEqual(toggle(helper, player, BARREL).status(), RangeLinks.Status.LINKED,
                                "clique no barril");
                        assertProfileNetwork(helper, router, BARREL, null, "perfil com rede ativa alheia");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Perfil em duas redes: Trás da fornalha em Extrair na rede X e Cima em Inserir na rede Y. A
     * fornalha remota dá o carvão para um destino de X (a presa) e recebe a terra de uma origem de Y
     * (um barril remoto); nada cruza de uma rede para a outra.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void profileInTwoNetworks(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID x = data.create(UUID.randomUUID(), "X").id();
        UUID y = data.create(UUID.randomUUID(), "Y").id();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, BARREL);
                    container(helper, FURNACE_A).setItem(1, new ItemStack(Items.COAL, 4));
                    container(helper, BARREL).setItem(0, new ItemStack(Items.DIRT, 6));
                    router.setFaceNetwork(ResourceType.ITEM, Direction.UP, x);
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    EditTarget furnaces = EditTarget.group(groupOf(router, helper.absolutePos(FURNACE_A)));
                    router.configureFace(furnaces, ResourceType.ITEM, Direction.SOUTH, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE);
                    router.setFaceNetwork(furnaces, ResourceType.ITEM, Direction.SOUTH, x);
                    router.configureFace(furnaces, ResourceType.ITEM, Direction.UP, PortMode.INSERT, 0,
                            RedstoneMode.IGNORE);
                    router.setFaceNetwork(furnaces, ResourceType.ITEM, Direction.UP, y);
                    EditTarget barrels = EditTarget.group(groupOf(router, helper.absolutePos(BARREL)));
                    router.configureFace(barrels, ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE);
                    router.setFaceNetwork(barrels, ResourceType.ITEM, Direction.UP, y);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(countOf(helper, CHEST, Items.COAL) == 4, "o carvão não chegou à presa (X)");
                    helper.assertTrue(countOf(helper, FURNACE_A, Items.DIRT) == 6, "a terra não chegou à fornalha (Y)");
                })
                .thenExecute(() -> {
                    helper.assertTrue(countOf(helper, CHEST, Items.DIRT) == 0, "terra de Y na presa de X");
                    helper.assertTrue(countOf(helper, BARREL, Items.COAL) == 0, "carvão de X no barril de Y");
                })
                .thenSucceed();
    }

    /**
     * A exceção de uma máquina com outra rede numa face move por essa rede; as outras máquinas do
     * grupo, pela do perfil. A fornalha A (exceção: Trás em Y) manda o carvão ao barril de Y; a B (sem
     * exceção: Trás em X, a do perfil) manda à presa de X.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void exceptionOverridesProfileNetwork(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID x = data.create(UUID.randomUUID(), "X").id();
        UUID y = data.create(UUID.randomUUID(), "Y").id();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    container(helper, FURNACE_A).setItem(1, new ItemStack(Items.COAL, 3));
                    container(helper, FURNACE_B).setItem(1, new ItemStack(Items.COAL, 5));
                    router.setFaceNetwork(ResourceType.ITEM, Direction.UP, x);
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    EditTarget furnaces = EditTarget.group(groupOf(router, helper.absolutePos(FURNACE_A)));
                    router.configureFace(furnaces, ResourceType.ITEM, Direction.SOUTH, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE);
                    router.setFaceNetwork(furnaces, ResourceType.ITEM, Direction.SOUTH, x);
                    RemoteMachines remotes = router.remoteMachines();
                    int a = remotes.index(helper.absolutePos(FURNACE_A));
                    router.setFaceNetwork(EditTarget.machine(a), ResourceType.ITEM, Direction.SOUTH, y);
                    helper.assertTrue(remotes.machine(a).exception() != null, "a fornalha A sem exceção");
                    helper.assertTrue(remotes.machine(remotes.index(helper.absolutePos(FURNACE_B))).exception() == null,
                            "a fornalha B ganhou exceção");
                    EditTarget barrels = EditTarget.group(groupOf(router, helper.absolutePos(BARREL)));
                    router.configureFace(barrels, ResourceType.ITEM, Direction.UP, PortMode.INSERT, 0,
                            RedstoneMode.IGNORE);
                    router.setFaceNetwork(barrels, ResourceType.ITEM, Direction.UP, y);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(countOf(helper, BARREL, Items.COAL) == 3, "o carvão da A não chegou ao barril (Y)");
                    helper.assertTrue(countOf(helper, CHEST, Items.COAL) == 5, "o carvão da B não chegou à presa (X)");
                })
                .thenSucceed();
    }
}
