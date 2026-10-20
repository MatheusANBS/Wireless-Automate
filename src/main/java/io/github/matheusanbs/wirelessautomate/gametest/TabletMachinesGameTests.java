package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows.Block;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows.Machine;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows.More;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows.Row;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachines;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.TabletLivePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMachinesPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMachinesPayload.Action;
import io.github.matheusanbs.wirelessautomate.packet.TabletPagePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPayloads;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
 * A visão por máquinas da Lista do Tablet ({@link TabletMachines}, {@link TabletMachinesPayload}): as ações, os tetos e
 * só a página de quem pediu; a ordem por bloco, roteador e #N com a presa; os blocos recolhidos acima de 256 linhas e
 * o teto da página; a consulta por máquina (texto, tipo e rede); o "live" de uma máquina movendo; a ordem por bloco
 * compartilhada só com alguém olhando; e lembrar ao reabrir. Cada jogador só vê os roteadores que colocou.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TabletMachinesGameTests {
    private TabletMachinesGameTests() {
    }

    /** A ordem por bloco é uma só no servidor: o teste que confere quando ela existe roda sozinho. */
    private static final String ORDER_BATCH = "wirelessautomate_tablet_machines_order";
    private static final ResourceLocation CHEST_ID = BuiltInRegistries.BLOCK.getKey(Blocks.CHEST);
    private static final ResourceLocation BARREL_ID = BuiltInRegistries.BLOCK.getKey(Blocks.BARREL);
    private static final ResourceLocation FURNACE_ID = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);

    private static void eq(GameTestHelper helper, @Nullable Object actual, @Nullable Object expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperado " + expected + ", veio " + actual);
    }

    private static NodeKey key(RouterBlockEntity router) {
        return NodeKey.of(router.getLevel(), router.getBlockPos());
    }

    private static String uniqueName(GameTestHelper helper, String prefix) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        return prefix + Math.floorMod(pos.getX() * 31 + pos.getZ(), 1_000_000);
    }

    private static ServerPlayer playerWithTablet(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 4, 1.5)));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        return player;
    }

    private static TabletMenu openTablet(ServerPlayer player, int containerId) {
        TabletMenu menu = new TabletMenu(containerId, player.getInventory(), player);
        player.containerMenu = menu;
        return menu;
    }

    private static boolean act(ServerPlayer player, TabletMenu menu, Action action, @Nullable ResourceLocation block) {
        return TabletPayloads.handleMachines(player, new TabletMachinesPayload(menu.containerId, action,
                Optional.ofNullable(block)));
    }

    /** Força a remontagem e devolve as linhas da visão por máquinas. */
    private static TabletMachineRows fresh(TabletMenu menu) {
        menu.setQuery(menu.query());
        menu.pollSnapshot();
        return menu.snapshot().page().machineRows();
    }

    private static TabletMachineRows query(TabletMenu menu, Query query) {
        menu.setQuery(query);
        menu.pollSnapshot();
        return menu.snapshot().page().machineRows();
    }

    /** Vincula sem olhar o mundo (posições sem bloco) e avisa o índice: só para a ordem e os tetos. */
    private static void fakeLinks(RouterBlockEntity router, GameTestHelper helper, int count, ResourceLocation block,
            int z) {
        RemoteMachines remotes = router.remoteMachines();
        for (int i = 0; i < count; i++) {
            remotes.link(helper.absolutePos(new BlockPos(20 + i, 1, z)), block, -1, null);
        }
        NodeIndex.track(router);
    }

    /** Um segundo roteador Avançado, num baú em (0, 1, 0). */
    private static RouterBlockEntity secondRouter(GameTestHelper helper) {
        BlockPos chest = new BlockPos(0, 1, 0);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(chest.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        return helper.getBlockEntity(chest.above());
    }

    private static void roundTrip(GameTestHelper helper, TabletSnapshot.Page page) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        TabletSnapshot.Page.STREAM_CODEC.encode(buf, page);
        helper.assertTrue(TabletSnapshot.Page.STREAM_CODEC.decode(buf).equals(page), "página por máquinas pelo codec");
    }

    private static boolean machine(Row row, int router, int index) {
        return row instanceof Machine machine && !machine.attached() && machine.router() == router
                && machine.index() == index;
    }

    private static boolean attached(Row row, int router) {
        return row instanceof Machine machine && machine.attached() && machine.router() == router;
    }

    private static boolean block(Row row, ResourceLocation id, int machines, int routers, boolean collapsed) {
        return row instanceof Block block && block.block().equals(id) && block.machines() == machines
                && block.routers() == routers && block.collapsed() == collapsed;
    }

    /** As linhas de máquina, como "presa" ou o índice. */
    private static List<String> machines(TabletMachineRows rows) {
        List<String> found = new ArrayList<>();
        for (Row row : rows.rows()) {
            if (row instanceof Machine machine) {
                found.add(machine.attached() ? "presa" : "#" + machine.index());
            }
        }
        return found;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void actionsOrderAndLimitsChangeOnlyThatPage(GameTestHelper helper) {
        RouterBlockEntity first = chestWithRouter(helper, RouterTier.ADVANCED);
        RouterBlockEntity second = secondRouter(helper);
        ServerPlayer a = playerWithTablet(helper);
        ServerPlayer b = playerWithTablet(helper);
        String name = uniqueName(helper, "maqo");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, first);
                    waitLoaded(helper, second);
                })
                .thenExecute(() -> {
                    first.setName(name + " a");
                    second.setName(name + " b");
                    fakeLinks(first, helper, 10, CHEST_ID, 0);
                    fakeLinks(first, helper, 2, BARREL_ID, 1);
                    fakeLinks(second, helper, 1, BARREL_ID, 2);
                    NodeIndex.placedBy(first, a.getUUID());
                    NodeIndex.placedBy(second, a.getUUID());

                    TabletMenu menuB = openTablet(b, 151);
                    TabletMenu menuA = openTablet(a, 150);
                    eq(helper, fresh(menuA), TabletMachineRows.EMPTY, "linhas por máquina na visão por roteadores");
                    menuB.pollPayloads();
                    helper.assertFalse(act(b, menuB, Action.OPEN_BLOCK, CHEST_ID), "abriu um bloco fora da visão");
                    helper.assertFalse(TabletPayloads.handleMachines(a, new TabletMachinesPayload(999, Action.MACHINES,
                            Optional.empty())), "valeu com outro containerId");

                    helper.assertTrue(act(a, menuA, Action.MACHINES, null), "não entrou na visão por máquinas");
                    helper.assertFalse(menuB.pollPayloads().stream()
                                    .anyMatch(p -> p instanceof TabletPagePayload || p instanceof TabletLivePayload),
                            "a ação do outro jogador mandou página");
                    TabletMachineRows rows = fresh(menuA);
                    TabletSnapshot.Page page = menuA.snapshot().page();
                    helper.assertTrue(rows.active() && page.nodes().isEmpty(), "a página por máquinas levou nós");
                    eq(helper, rows.routers().size(), 2, "roteadores na paleta");
                    eq(helper, rows.routers().get(0).key(), key(first), "o primeiro roteador pela ordem da Lista");
                    eq(helper, rows.routers().get(1).key(), key(second), "o segundo roteador");
                    List<Row> list = rows.rows();
                    eq(helper, list.size(), 14, "linhas");
                    helper.assertTrue(block(list.get(0), BARREL_ID, 3, 2, false), "o cabeçalho dos barris: " + list.get(0));
                    helper.assertTrue(machine(list.get(1), 0, 10) && machine(list.get(2), 0, 11)
                            && machine(list.get(3), 1, 0), "os barris por roteador e #N");
                    helper.assertTrue(((Machine) list.get(1)).number() == 1 && ((Machine) list.get(2)).number() == 2
                            && ((Machine) list.get(3)).number() == 1, "o #N é a posição no grupo, como na árvore");
                    helper.assertTrue(block(list.get(4), CHEST_ID, 12, 2, false), "o cabeçalho dos baús: " + list.get(4));
                    helper.assertTrue(attached(list.get(5), 0), "a presa antes das vinculadas");
                    for (int i = 0; i < 7; i++) {
                        helper.assertTrue(machine(list.get(6 + i), 0, i), "baú " + i + " fora de ordem");
                    }
                    helper.assertTrue(list.get(13) instanceof More more && more.block().equals(CHEST_ID)
                            && more.hidden() == 4, "o mais 4");
                    eq(helper, rows.lives().size(), 11, "um estado por linha de máquina");
                    eq(helper, rows.totalRows(), 14, "linhas sem o teto");
                    eq(helper, rows.machines(), 15, "máquinas que casam");
                    eq(helper, rows.blocks(), 2, "blocos");
                    eq(helper, menuA.snapshot().machines(), NodeIndex.get(helper.getLevel().getServer()).machines(),
                            "a soma das máquinas no cabeçalho");
                    roundTrip(helper, page);
                    eq(helper, fresh(menuB), TabletMachineRows.EMPTY, "a página do outro jogador mudou de visão");

                    helper.assertTrue(act(a, menuA, Action.CLOSE_BLOCK, CHEST_ID), "não recolheu");
                    list = fresh(menuA).rows();
                    eq(helper, list.size(), 5, "linhas com os baús recolhidos");
                    helper.assertTrue(block(list.get(4), CHEST_ID, 12, 2, true), "o bloco recolhido");
                    helper.assertTrue(act(a, menuA, Action.OPEN_BLOCK, CHEST_ID)
                            && act(a, menuA, Action.SHOW_ALL, CHEST_ID), "não abriu com todas");
                    list = fresh(menuA).rows();
                    eq(helper, list.size(), 17, "linhas com todas");
                    helper.assertTrue(machine(list.get(15), 0, 9) && attached(list.get(16), 1),
                            "a presa do segundo roteador depois das vinculadas do primeiro");
                    helper.assertFalse(act(a, menuA, Action.OPEN_BLOCK, null), "ação de bloco sem bloco");

                    // O teto de cada conjunto: os baús já estão nos abertos.
                    int refused = -1;
                    for (int i = 0; i < TabletMachines.MAX_BLOCKS + 10 && refused < 0; i++) {
                        if (!act(a, menuA, Action.OPEN_BLOCK, WirelessAutomate.id("bloco_" + i))) {
                            refused = i;
                        }
                    }
                    eq(helper, refused, TabletMachines.MAX_BLOCKS - 1, "o primeiro recusado acima do teto");
                    helper.assertTrue(act(a, menuA, Action.CLOSE_BLOCK, WirelessAutomate.id("bloco_0"))
                            && act(a, menuA, Action.OPEN_BLOCK, WirelessAutomate.id("bloco_x")), "não abriu com espaço");
                    helper.assertTrue(act(a, menuA, Action.ROUTERS, null), "não voltou aos roteadores");
                    helper.assertFalse(act(a, menuA, Action.SHOW_ALL, BARREL_ID), "ação de bloco na visão por roteadores");
                    helper.assertTrue(fresh(menuA).equals(TabletMachineRows.EMPTY) && !menuA.snapshot().nodes().isEmpty(),
                            "a visão por roteadores não voltou");
                    menuA.removed(a);
                    menuB.removed(b);
                    a.containerMenu = a.inventoryMenu;
                    b.containerMenu = b.inventoryMenu;
                })
                .thenSucceed();
    }

    /**
     * Com a visão inteira acima de 256 linhas, os blocos começam recolhidos; o "Mostrar todas" de um bloco enche a
     * página até o teto, que conta as linhas; e a busca pelo bloco deixa a visão pequena, com o bloco aberto.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void blocksCollapseAboveTheCapAndPageLimit(GameTestHelper helper) {
        RouterBlockEntity first = chestWithRouter(helper, RouterTier.ADVANCED);
        RouterBlockEntity second = secondRouter(helper);
        ServerPlayer player = playerWithTablet(helper);
        String name = uniqueName(helper, "maqt");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, first);
                    waitLoaded(helper, second);
                })
                .thenExecute(() -> {
                    first.setName(name + " a");
                    second.setName(name + " b");
                    fakeLinks(first, helper, 300, CHEST_ID, 0);
                    for (int j = 0; j < 30; j++) {
                        fakeLinks(second, helper, 9, WirelessAutomate.id("bloco_teste_" + j), 10 + j);
                    }
                    NodeIndex.placedBy(first, player.getUUID());
                    NodeIndex.placedBy(second, player.getUUID());
                    TabletMenu menu = openTablet(player, 152);
                    helper.assertTrue(act(player, menu, Action.MACHINES, null), "não entrou na visão por máquinas");
                    TabletMachineRows rows = fresh(menu);
                    eq(helper, rows.rows().size(), 31, "só os cabeçalhos");
                    helper.assertTrue(rows.rows().stream().allMatch(row -> row instanceof Block block && block.collapsed()),
                            "algum bloco aberto acima de 256 linhas");
                    // A ordem dos blocos é a dos ids (o caminho antes do namespace), como a da Lista: os de teste antes dos baús.
                    helper.assertTrue(block(rows.rows().get(30), CHEST_ID, 302, 2, true),
                            "o cabeçalho dos baús: " + rows.rows().get(30));
                    eq(helper, rows.machines(), 572, "máquinas");
                    eq(helper, rows.blocks(), 31, "blocos");

                    helper.assertTrue(act(player, menu, Action.OPEN_BLOCK, CHEST_ID)
                            && act(player, menu, Action.SHOW_ALL, CHEST_ID), "não abriu com todas");
                    rows = fresh(menu);
                    eq(helper, rows.rows().size(), TabletSnapshot.MAX_NODES, "linhas no teto");
                    eq(helper, rows.totalRows(), 31 + 302, "linhas sem o teto");
                    helper.assertTrue(attached(rows.rows().get(31), 0) && machine(rows.rows().get(32), 0, 0)
                            && machine(rows.rows().get(255), 0, 223), "a ordem dentro do bloco cortado");
                    eq(helper, rows.lives().size(), TabletSnapshot.MAX_NODES - 31, "estados das máquinas à vista");
                    roundTrip(helper, menu.snapshot().page());

                    helper.assertTrue(act(player, menu, Action.SHOW_FEW, CHEST_ID), "não voltou a poucas");
                    eq(helper, fresh(menu).rows().size(), 31 + 8 + 1, "linhas com poucas");

                    rows = query(menu, Query.of("bloco_teste_7"));
                    eq(helper, rows.rows().size(), 1 + 8 + 1, "a busca pelo bloco, aberto");
                    helper.assertTrue(rows.rows().get(0) instanceof Block block && !block.collapsed()
                            && block.block().equals(WirelessAutomate.id("bloco_teste_7")), "o bloco da busca");
                    menu.removed(player);
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    /**
     * A consulta por máquina: o tipo e a rede pelos papéis e redes de cada máquina (a fornalha de um roteador que
     * também move fluidos não aparece em "Só Fluidos"), e o texto pelo rótulo, pelo bloco e pelo nome do roteador.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void queryByMachine(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = playerWithTablet(helper);
        String name = uniqueName(helper, "maqc");
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setName(name);
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    UUID profileNet = NetworkSavedData.get(helper.getLevel().getServer())
                            .create(player.getUUID(), "fornos").id();
                    EditTarget furnaces = EditTarget.group(groupOf(router, helper.absolutePos(FURNACE_A)));
                    router.configureFace(furnaces, ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE);
                    router.setFaceNetwork(furnaces, ResourceType.ITEM, Direction.UP, profileNet);
                    router.configureFace(ResourceType.FLUID, Direction.NORTH, PortMode.INSERT, 0, RedstoneMode.IGNORE);
                    int b = router.remoteMachines().index(helper.absolutePos(FURNACE_B));
                    helper.assertTrue(router.renameTarget(EditTarget.machine(b), "Forno dois"), "não renomeou");
                    NodeIndex.placedBy(router, player.getUUID());

                    TabletMenu menu = openTablet(player, 153);
                    helper.assertTrue(act(player, menu, Action.MACHINES, null), "não entrou na visão por máquinas");
                    eq(helper, machines(fresh(menu)), List.of("#2", "presa", "#0", "#1"), "todas as máquinas");

                    eq(helper, machines(query(menu, Query.DEFAULT.withType(Optional.of(ResourceType.FLUID)))),
                            List.of("presa"), "Só Fluidos");
                    eq(helper, machines(query(menu, Query.DEFAULT.withType(Optional.of(ResourceType.ITEM)))),
                            List.of("#0", "#1"), "Só Itens");
                    eq(helper, machines(query(menu, Query.DEFAULT.withNetworks(List.of(profileNet)))), List.of("#0", "#1"),
                            "a rede do perfil");
                    eq(helper, machines(query(menu, Query.of("dois"))), List.of("#" + b), "o rótulo");
                    eq(helper, machines(query(menu, Query.of(name))), List.of("#2", "presa", "#0", "#1"),
                            "o nome do roteador");
                    eq(helper, machines(query(menu, Query.of("furnace"))), List.of("#0", "#1"), "o id do bloco");
                    eq(helper, machines(query(menu, Query.of("zzz").withTextMachines(List.of(FURNACE_ID)))),
                            List.of("#0", "#1"), "o nome do bloco no idioma do cliente");
                    menu.removed(player);
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    /** Uma máquina vinculada recebendo itens fica "movendo", com vazão, no pacote pequeno da visão por máquinas. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void movingMachineLiveInMachineView(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        ServerPlayer player = playerWithTablet(helper);
        TabletMenu[] menu = new TabletMenu[1];
        AtomicBoolean livePacket = new AtomicBoolean();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    linkInNetwork(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                    NodeIndex.placedBy(router, player.getUUID());
                    menu[0] = openTablet(player, 154);
                    helper.assertTrue(act(player, menu[0], Action.MACHINES, null), "não entrou na visão por máquinas");
                    player.containerMenu = player.inventoryMenu;
                    menu[0].pollPayloads();
                })
                .thenWaitUntil(() -> {
                    Container source = container(helper, CHEST);
                    source.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    container(helper, REMOTE_CHEST).clearContent();
                    List<CustomPacketPayload> payloads = menu[0].pollPayloads();
                    boolean page = payloads.stream().anyMatch(p -> p instanceof TabletPagePayload);
                    for (CustomPacketPayload payload : payloads) {
                        if (payload instanceof TabletLivePayload live && !page && live.machines().size() == 2
                                && live.machines().get(1).status() == MachineStatus.MOVING) {
                            livePacket.set(true);
                        }
                    }
                    List<MachineLive> lives = menu[0].snapshot().page().machineRows().lives();
                    MachineLive remote = lives.size() == 2 ? lives.get(1) : null;
                    helper.assertTrue(remote != null && remote.status() == MachineStatus.MOVING
                            && remote.received(ResourceType.ITEM) > 0 && livePacket.get(),
                            "a máquina não ficou movendo no pacote pequeno: " + remote);
                })
                .thenExecute(() -> {
                    menu[0].removed(player);
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    /**
     * A ordem por bloco compartilhada só existe com alguém em Máquinas: sai duas amostras depois de o último trocar
     * de visão, e na hora quando ele fecha o Tablet.
     */
    @GameTest(template = "empty", batch = ORDER_BATCH, timeoutTicks = 300)
    public static void machineOrderOnlyWhileWatching(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        ServerPlayer player = playerWithTablet(helper);
        TabletMenu[] menu = new TabletMenu[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    fakeLinks(router, helper, 3, BARREL_ID, 0);
                    NodeIndex.placedBy(router, player.getUUID());
                    menu[0] = openTablet(player, 155);
                    TabletMachines machines = menu[0].machinesState();
                    fresh(menu[0]);
                    helper.assertFalse(machines.orderExistsForTests(), "a ordem por bloco sem ninguém em Máquinas");
                    helper.assertTrue(act(player, menu[0], Action.MACHINES, null), "não entrou na visão por máquinas");
                    fresh(menu[0]);
                    helper.assertTrue(machines.orderExistsForTests(), "a ordem por bloco não foi montada");
                    helper.assertTrue(act(player, menu[0], Action.ROUTERS, null), "não voltou aos roteadores");
                    fresh(menu[0]);
                    helper.assertTrue(machines.orderExistsForTests(), "a ordem saiu antes das duas amostras");
                })
                .thenWaitUntil(() -> {
                    menu[0].pollSnapshot();
                    helper.assertFalse(menu[0].machinesState().orderExistsForTests(), "a ordem por bloco ficou");
                })
                .thenExecute(() -> {
                    helper.assertTrue(act(player, menu[0], Action.MACHINES, null), "não voltou às máquinas");
                    fresh(menu[0]);
                    helper.assertTrue(menu[0].machinesState().orderExistsForTests(), "a ordem não voltou");
                    menu[0].removed(player);
                    player.containerMenu = player.inventoryMenu;
                    helper.assertFalse(menu[0].machinesState().orderExistsForTests(), "a ordem ficou depois de fechar");
                })
                .thenSucceed();
    }

    /** O Tablet lembra a visão e os blocos ao reabrir, vai com eles no buffer de abertura, e o logout limpa. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rememberViewAndBlocks(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        ServerPlayer player = playerWithTablet(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    fakeLinks(router, helper, 3, BARREL_ID, 0);
                    NodeIndex.placedBy(router, player.getUUID());
                    TabletMenu menu = openTablet(player, 156);
                    helper.assertTrue(act(player, menu, Action.MACHINES, null)
                            && act(player, menu, Action.CLOSE_BLOCK, CHEST_ID)
                            && act(player, menu, Action.SHOW_ALL, BARREL_ID), "as ações não valeram");
                    menu.removed(player);
                    TabletMenu.State saved = TabletMenu.savedState(player.getUUID());
                    helper.assertTrue(saved != null, "o estado não foi guardado");
                    eq(helper, saved.machines(), new TabletMachines.Saved(true, List.of(), List.of(CHEST_ID),
                            List.of(BARREL_ID)), "a visão e os blocos guardados");

                    TabletMenu reopened = openTablet(player, 157);
                    helper.assertTrue(reopened.machinesState().active(), "reabriu na visão por roteadores");
                    TabletMachineRows rows = reopened.snapshot().page().machineRows();
                    helper.assertTrue(rows.active() && block(rows.rows().get(4), CHEST_ID, 1, 1, true),
                            "a primeira página sem os blocos lembrados: " + rows.rows());
                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                            helper.getLevel().registryAccess());
                    reopened.writeOpenData(buf);
                    TabletMenu client = new TabletMenu(157, player.getInventory(), buf);
                    eq(helper, client.snapshot().page().machineRows(), rows, "a visão no buffer de abertura");
                    reopened.removed(player);
                    player.containerMenu = player.inventoryMenu;

                    TabletMenu.forget(player.getUUID());
                    helper.assertTrue(TabletMenu.savedState(player.getUUID()) == null, "o logout não limpou");
                    TabletMenu fresh = openTablet(player, 158);
                    helper.assertFalse(fresh.machinesState().active(), "abriu em Máquinas depois do logout");
                    fresh.removed(player);
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }
}
