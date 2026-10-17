package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TabletFlowsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletFlowsPayload.NetworkFlows;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Fluxos do Tablet no servidor: o jogador só olha o amostrador com ela aberta, o {@link TabletFlowsPayload}
 * só sai nela e uma vez por amostra, e leva só as redes que o jogador vê. No lote dos Fluxos
 * ({@code wirelessautomate_flows}), com os outros que olham; cada teste confere só as próprias redes.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TabletFlowsGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final String BATCH = "wirelessautomate_flows";

    private static ServerPlayer playerWithTablet(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(A).above(2)));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        return player;
    }

    /**
     * O Tablet do jogador, sem ser o menu aberto dele: o servidor chama o {@code broadcastChanges} do menu aberto
     * de cada jogador a cada tick, e ele consumiria os Fluxos antes do teste.
     */
    private static TabletMenu openTablet(ServerPlayer player, int containerId) {
        return new TabletMenu(containerId, player.getInventory(), player);
    }

    /** Um baú com um roteador em cima, a face de cima do tipo de itens no modo e na rede. */
    private static RouterBlockEntity chestWithRouter(GameTestHelper helper, BlockPos machine, UUID network,
            PortMode mode) {
        helper.setBlock(machine, Blocks.CHEST);
        BlockPos pos = machine.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        router.setNetworkId(ResourceType.ITEM, network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        return router;
    }

    private static boolean tab(TabletMenu menu, int tab) {
        return menu.setTab(tab);
    }

    private static boolean watching(ServerPlayer player) {
        return NetworkManager.get().flows().isWatching(player.getUUID());
    }

    private static @Nullable NetworkFlows network(TabletFlowsPayload flows, UUID id) {
        for (NetworkFlows n : flows.networks()) {
            if (n.id().equals(id)) {
                return n;
            }
        }
        return null;
    }

    private static boolean hasItems(TabletFlowsPayload flows, UUID id) {
        NetworkFlows n = network(flows, id);
        return n != null && n.types().stream().anyMatch(f -> f.type() == ResourceType.ITEM);
    }

    /**
     * Na Lista ninguém olha e nada sai; na aba Fluxos o jogador olha e o pacote sai com a rede dele, uma vez por
     * amostra; voltar à Lista e fechar o Tablet tiram o jogador do amostrador; reabrir na aba Fluxos olha de novo.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void flowsPayloadOnlyOnFlowsTab(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        UUID network = NetworkSavedData.get(helper.getLevel().getServer())
                .create(player.getUUID(), "teste-fluxos-aba").id();
        RouterBlockEntity from = chestWithRouter(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity to = chestWithRouter(helper, B, network, PortMode.INSERT);
        ((Container) helper.getBlockEntity(A)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        TabletMenu menu = openTablet(player, 90);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(from)
                        && NetworkManager.get().contains(to), "sem onLoad"))
                .thenExecute(() -> helper.assertFalse(watching(player), "olhando na Lista"))
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(menu.pollFlows() == null, "Fluxos na Lista");
                    helper.assertTrue(tab(menu, TabletMenu.FLOWS_TAB), "a aba Fluxos recusada");
                    helper.assertTrue(watching(player), "não olha na aba Fluxos");
                })
                .thenWaitUntil(() -> {
                    TabletFlowsPayload flows = menu.pollFlows();
                    helper.assertTrue(flows != null && hasItems(flows, network), "sem a rede nos Fluxos");
                    // a mesma amostra não sai duas vezes
                    helper.assertTrue(menu.pollFlows() == null, "o mesmo pacote de novo");
                    helper.assertTrue(flows.containerId() == 90 && flows.budgetNanos() > 0, "cabeçalho dos Fluxos");
                })
                .thenExecute(() -> {
                    helper.assertTrue(tab(menu, 0), "a Lista recusada");
                    helper.assertFalse(watching(player), "ainda olha na Lista");
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(menu.pollFlows() == null, "Fluxos depois de voltar à Lista");
                    tab(menu, TabletMenu.FLOWS_TAB);
                    helper.assertTrue(watching(player), "não olha de volta na aba Fluxos");
                    menu.removed(player);
                    helper.assertFalse(watching(player), "olha com o Tablet fechado");
                    // o Tablet reabre na aba Fluxos e volta a olhar
                    TabletMenu again = openTablet(player, 91);
                    helper.assertTrue(again.tab() == TabletMenu.FLOWS_TAB && watching(player), "não reabriu nos Fluxos");
                    again.removed(player);
                    helper.assertFalse(watching(player), "olha depois de fechar de novo");
                })
                .thenSucceed();
    }

    /**
     * Os Fluxos levam a rede do jogador e as públicas, e nunca uma rede privada de outro. As alheias ficam num
     * grupo pausado, sem roteador: rede pausada vai no pacote mesmo sem rotas, marcada como pausada, então a
     * privada só fica de fora pela visibilidade. Nenhum roteador numa rede pública: o nó dela ficaria visível
     * aos jogadores dos outros testes do Tablet. As redes alheias e o grupo saem no fim.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void flowsShowOnlyVisibleNetworks(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID mine = data.create(player.getUUID(), "teste-fluxos-minha").id();
        UUID foreignPrivate = data.create(UUID.randomUUID(), "teste-fluxos-alheia").id();
        WaNetwork publicNetwork = data.create(UUID.randomUUID(), "teste-fluxos-publica").withPublic(true);
        data.update(publicNetwork);
        WaGroup group = data.createGroup(UUID.randomUUID(), "teste-fluxos-grupo").withNetwork(foreignPrivate)
                .withNetwork(publicNetwork.id()).withPaused(true);
        data.updateGroup(group);
        RouterBlockEntity from = chestWithRouter(helper, A, mine, PortMode.EXTRACT);
        RouterBlockEntity to = chestWithRouter(helper, B, mine, PortMode.INSERT);
        TabletMenu menu = openTablet(player, 92);
        TabletFlowsPayload[] seen = new TabletFlowsPayload[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(from)
                        && NetworkManager.get().contains(to), "sem onLoad"))
                .thenExecute(() -> helper.assertTrue(tab(menu, TabletMenu.FLOWS_TAB), "a aba Fluxos recusada"))
                .thenWaitUntil(() -> {
                    TabletFlowsPayload polled = menu.pollFlows();
                    if (polled != null) {
                        seen[0] = polled;
                    }
                    helper.assertTrue(seen[0] != null && hasItems(seen[0], mine), "sem a rede do jogador: " + seen[0]);
                })
                .thenExecute(() -> {
                    TabletFlowsPayload flows = seen[0];
                    helper.assertFalse(network(flows, mine).paused(), "a rede do jogador pausada");
                    helper.assertTrue(network(flows, foreignPrivate) == null, "rede privada alheia nos Fluxos");
                    NetworkFlows shared = network(flows, publicNetwork.id());
                    helper.assertTrue(shared != null && shared.paused() && shared.types().isEmpty(),
                            "rede pública pausada: " + shared);
                })
                .thenExecute(() -> {
                    menu.removed(player);
                    data.removeGroup(group.id());
                    data.remove(foreignPrivate);
                    data.remove(publicNetwork.id());
                    helper.assertFalse(watching(player), "olha com o Tablet fechado");
                })
                .thenSucceed();
    }

    /**
     * As faces sem rede só contam para quem vê o nó na Lista: o roteador sem rede que um jogador colocou entra nos
     * Fluxos dele e não nos de outro jogador comum. Os dois leem a mesma amostra (as duas consultas no mesmo
     * tick), então a diferença entre eles é só esse roteador.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void facesWithoutNetworkOnlyFromVisibleNodes(GameTestHelper helper) {
        ServerPlayer owner = playerWithTablet(helper);
        ServerPlayer other = playerWithTablet(helper);
        helper.setBlock(A, Blocks.CHEST);
        BlockPos pos = A.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        router.setNetworkId(ResourceType.ITEM, null);
        for (Direction face : List.of(Direction.UP, Direction.NORTH, Direction.EAST)) {
            router.setMode(ResourceType.ITEM, face, PortMode.EXTRACT);
        }
        NodeIndex.placedBy(router, owner.getUUID());
        TabletMenu ownerMenu = openTablet(owner, 93);
        TabletMenu otherMenu = openTablet(other, 94);
        int[] counts = {-1, -1};

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad"))
                .thenExecute(() -> {
                    helper.assertTrue(tab(ownerMenu, TabletMenu.FLOWS_TAB) && tab(otherMenu, TabletMenu.FLOWS_TAB),
                            "a aba Fluxos recusada");
                })
                .thenWaitUntil(() -> {
                    TabletFlowsPayload mine = ownerMenu.pollFlows();
                    TabletFlowsPayload theirs = otherMenu.pollFlows();
                    if (mine != null && theirs != null) {
                        counts[0] = mine.withoutNetwork(ResourceType.ITEM);
                        counts[1] = theirs.withoutNetwork(ResourceType.ITEM);
                    }
                    helper.assertTrue(counts[0] >= 3 && counts[0] - counts[1] == 3,
                            "sem rede: do dono " + counts[0] + ", do outro " + counts[1]);
                })
                .thenExecute(() -> {
                    ownerMenu.removed(owner);
                    otherMenu.removed(other);
                })
                .thenSucceed();
    }
}
