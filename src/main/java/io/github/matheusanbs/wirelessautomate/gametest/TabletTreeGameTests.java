package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.Attached;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.Group;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.Machine;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.More;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.Row;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.menu.TabletTree;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletLivePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPagePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPayloads;
import io.github.matheusanbs.wirelessautomate.packet.TabletSelectNodePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletTreePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletTreePayload.Action;
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
 * A árvore da Lista do Tablet ({@link TabletTree}, {@link TabletTreePayload}): abrir, recolher e "Mostrar todas" mudam
 * só a página de quem pediu, com as linhas na ordem da tela e o teto de linhas; a visibilidade e os limites; e o
 * estado e a vazão das linhas de máquina (movendo, entrada cheia e descarregada), com o pacote pequeno.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TabletTreeGameTests {
    private TabletTreeGameTests() {
    }

    private static final ResourceLocation CHEST_ID = BuiltInRegistries.BLOCK.getKey(Blocks.CHEST);
    private static final ResourceLocation BARREL_ID = BuiltInRegistries.BLOCK.getKey(Blocks.BARREL);

    private static void eq(GameTestHelper helper, @Nullable Object actual, @Nullable Object expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperado " + expected + ", veio " + actual);
    }

    private static NodeKey key(RouterBlockEntity router) {
        return NodeKey.of(router.getLevel(), router.getBlockPos());
    }

    /** Um nome que só este teste usa, para a consulta achar só o roteador dele. */
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

    private static TabletMenu openTablet(ServerPlayer player, int containerId, String name) {
        TabletMenu menu = new TabletMenu(containerId, player.getInventory(), player);
        player.containerMenu = menu;
        menu.setQuery(Query.of(name));
        return menu;
    }

    private static boolean tree(ServerPlayer player, TabletMenu menu, Action action, NodeKey node, int group) {
        return TabletPayloads.handleTree(player, new TabletTreePayload(menu.containerId, action, node, group));
    }

    /** Força a remontagem e devolve a página. */
    private static TabletSnapshot.Page fresh(TabletMenu menu) {
        menu.setQuery(menu.query());
        menu.pollSnapshot();
        return menu.snapshot().page();
    }

    /** Vincula sem olhar o mundo (posições sem bloco) e avisa o índice: só para a ordem e o teto das linhas. */
    private static void fakeLinks(RouterBlockEntity router, GameTestHelper helper, int count, ResourceLocation block,
            int z) {
        RemoteMachines remotes = router.remoteMachines();
        for (int i = 0; i < count; i++) {
            remotes.link(helper.absolutePos(new BlockPos(20 + i, 1, z)), block, -1, null);
        }
        NodeIndex.track(router);
    }

    /** Página pelo codec, ida e volta. */
    private static void roundTrip(GameTestHelper helper, TabletSnapshot.Page page) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        TabletSnapshot.Page.STREAM_CODEC.encode(buf, page);
        helper.assertTrue(TabletSnapshot.Page.STREAM_CODEC.decode(buf).equals(page), "página com a árvore pelo codec");
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void openCollapseAndShowAllChangeOnlyThatPage(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        ServerPlayer a = playerWithTablet(helper);
        ServerPlayer b = playerWithTablet(helper);
        String name = uniqueName(helper, "arvo");
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(b.getUUID(), "arvore").id());
                    router.setName(name);
                    fakeLinks(router, helper, 10, CHEST_ID, 0);
                    fakeLinks(router, helper, 2, BARREL_ID, 1);
                    NodeIndex.placedBy(router, a.getUUID());
                    NodeKey key = key(router);
                    RemoteMachines remotes = router.remoteMachines();
                    int chests = remotes.machine(0).group();
                    int barrels = remotes.machine(10).group();

                    TabletMenu menuB = openTablet(b, 141, name);
                    TabletMenu menuA = openTablet(a, 140, name);
                    eq(helper, fresh(menuA).nodes().size(), 1, "nós da consulta");
                    eq(helper, menuA.snapshot().page().rows(), TabletRows.EMPTY, "linhas antes de abrir");
                    menuB.pollPayloads();

                    helper.assertTrue(tree(a, menuA, Action.OPEN, key, 0), "não abriu o roteador");
                    helper.assertFalse(menuB.pollPayloads().stream()
                                    .anyMatch(p -> p instanceof TabletPagePayload || p instanceof TabletLivePayload),
                            "a ação do outro jogador mandou página");
                    TabletSnapshot.Page page = fresh(menuA);
                    List<Row> rows = page.rows().rows();
                    eq(helper, rows.size(), 1 + 1 + 8 + 1 + 1 + 2, "linhas abertas");
                    helper.assertTrue(rows.get(0) instanceof Attached attached && attached.parent() == 0
                            && attached.side().equals(java.util.Optional.of(Direction.DOWN)), "a presa vem primeiro");
                    helper.assertTrue(rows.get(1) instanceof Group group && group.id() == chests && group.total() == 10
                            && group.block().equals(CHEST_ID) && !group.collapsed() && !group.showAll(), "o grupo dos baús");
                    for (int i = 0; i < 8; i++) {
                        helper.assertTrue(rows.get(2 + i) instanceof Machine machine && machine.index() == i
                                && machine.pos() == remotes.machine(i).pos().asLong(), "máquina " + i + " fora de ordem");
                    }
                    helper.assertTrue(rows.get(10) instanceof More more && more.group() == chests && more.hidden() == 2,
                            "o mais 2");
                    helper.assertTrue(rows.get(11) instanceof Group group && group.id() == barrels, "o grupo dos barris");
                    helper.assertTrue(rows.get(12) instanceof Machine m10 && m10.index() == 10
                            && rows.get(13) instanceof Machine m11 && m11.index() == 11, "as máquinas dos barris");
                    eq(helper, page.rows().lives().size(), 10, "um estado por linha de máquina");
                    // Sem o Upgrade de Alcance as remotas não movem.
                    helper.assertTrue(page.rows().lives().stream().allMatch(live -> live.status() == MachineStatus.NO_UPGRADE),
                            "estado sem o upgrade");
                    roundTrip(helper, page);
                    eq(helper, fresh(menuB).rows(), TabletRows.EMPTY, "a página do outro jogador abriu");

                    helper.assertTrue(tree(a, menuA, Action.SHOW_ALL, key, chests), "não mostrou todas");
                    helper.assertTrue(tree(a, menuA, Action.COLLAPSE_GROUP, key, barrels), "não recolheu");
                    rows = fresh(menuA).rows().rows();
                    eq(helper, rows.size(), 1 + 1 + 10 + 1, "linhas com todas e o grupo recolhido");
                    helper.assertTrue(rows.get(1) instanceof Group group && group.showAll(), "a marca do Mostrar todas");
                    helper.assertTrue(rows.get(11) instanceof Machine machine && machine.index() == 9, "a última dos baús");
                    helper.assertTrue(rows.get(12) instanceof Group group && group.id() == barrels && group.collapsed(),
                            "o grupo recolhido");
                    eq(helper, fresh(menuB).rows(), TabletRows.EMPTY, "a página do outro jogador mudou");

                    helper.assertTrue(tree(a, menuA, Action.SHOW_FEW, key, chests)
                            && tree(a, menuA, Action.EXPAND_GROUP, key, barrels), "não voltou o grupo");
                    eq(helper, fresh(menuA).rows().rows().size(), 14, "linhas depois de voltar");
                    helper.assertTrue(tree(a, menuA, Action.CLOSE, key, 0), "não fechou");
                    eq(helper, fresh(menuA).rows(), TabletRows.EMPTY, "linhas depois de fechar");
                    a.containerMenu = a.inventoryMenu;
                    b.containerMenu = b.inventoryMenu;
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void invisibleNodeAndUnknownGroupRefused(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        ServerPlayer stranger = playerWithTablet(helper);
        String name = uniqueName(helper, "arvi");
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "alheia").id());
                    router.setName(name);
                    fakeLinks(router, helper, 2, CHEST_ID, 0);
                    NodeKey key = key(router);
                    int group = router.remoteMachines().machine(0).group();
                    TabletMenu menu = openTablet(stranger, 142, name);
                    helper.assertFalse(tree(stranger, menu, Action.OPEN, key, 0), "abriu um nó que o jogador não vê");
                    helper.assertFalse(tree(stranger, menu, Action.COLLAPSE_GROUP, key, group), "recolheu num nó invisível");
                    helper.assertFalse(tree(stranger, menu, Action.SHOW_ALL, key, group), "mostrou todas num nó invisível");
                    eq(helper, fresh(menu).rows(), TabletRows.EMPTY, "linhas de um nó invisível");

                    NodeIndex.placedBy(router, stranger.getUUID());
                    helper.assertTrue(tree(stranger, menu, Action.OPEN, key, 0), "não abriu depois de ver");
                    helper.assertFalse(tree(stranger, menu, Action.COLLAPSE_GROUP, key, group + 1000), "recolheu um grupo que não existe");
                    stranger.containerMenu = stranger.inventoryMenu;
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void openAndGroupLimits(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        ServerPlayer player = playerWithTablet(helper);
        String name = uniqueName(helper, "arvl");
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setName(name);
                    fakeLinks(router, helper, 2, CHEST_ID, 0);
                    NodeIndex.placedBy(router, player.getUUID());
                    NodeKey key = key(router);
                    int group = router.remoteMachines().machine(0).group();
                    TabletMenu menu = openTablet(player, 143, name);
                    TabletTree tree = menu.treeState();
                    helper.assertTrue(tree != null, "sem árvore no servidor");
                    List<NodeKey> fakes = new ArrayList<>();
                    for (int i = 0; i < TabletTree.MAX_OPEN; i++) {
                        NodeKey fake = new NodeKey(helper.getLevel().dimension(), new BlockPos(i, -50, 7));
                        fakes.add(fake);
                        tree.openForTests(fake);
                    }
                    helper.assertFalse(tree(player, menu, Action.OPEN, key, 0), "abriu acima do teto");
                    helper.assertTrue(tree(player, menu, Action.CLOSE, fakes.get(0), 0), "não fechou");
                    helper.assertTrue(tree(player, menu, Action.OPEN, key, 0), "não abriu com espaço");

                    for (int i = 0; i < TabletTree.MAX_GROUPS; i++) {
                        tree.collapseForTests(new TabletTree.GroupKey(key, 5000 + i));
                    }
                    helper.assertFalse(tree(player, menu, Action.COLLAPSE_GROUP, key, group), "recolheu acima do teto");
                    helper.assertTrue(tree(player, menu, Action.EXPAND_GROUP, key, 5000), "não expandiu");
                    helper.assertTrue(tree(player, menu, Action.COLLAPSE_GROUP, key, group), "não recolheu com espaço");

                    // A montagem tira dos abertos os nós que não estão no índice, com os grupos deles.
                    tree.collapseForTests(new TabletTree.GroupKey(fakes.get(2), 1));
                    eq(helper, tree.openCountForTests(), TabletTree.MAX_OPEN, "abertos antes da montagem");
                    eq(helper, tree.groupCountForTests(), TabletTree.MAX_GROUPS + 1, "grupos antes da montagem");
                    TabletSnapshot.Page page = fresh(menu);
                    eq(helper, tree.openCountForTests(), 1, "abertos depois da montagem");
                    eq(helper, tree.groupCountForTests(), TabletTree.MAX_GROUPS, "grupos depois da montagem");
                    helper.assertTrue(tree(player, menu, Action.OPEN, fakes.get(1), 0) == false,
                            "abriu um nó fora do índice");
                    helper.assertTrue(page.rows().rows().get(1) instanceof Group g && g.collapsed(), "o grupo recolhido");
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    /**
     * O teto de 256 conta as linhas: um roteador aberto com 300 máquinas e "Mostrar todas" enche a página, e o
     * segundo nó da consulta não cabe, mas continua contado.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pageLimitCountsRows(GameTestHelper helper) {
        RouterBlockEntity first = chestWithRouter(helper, RouterTier.ADVANCED);
        BlockPos otherChest = new BlockPos(0, 1, 0);
        helper.setBlock(otherChest, Blocks.CHEST);
        helper.setBlock(otherChest.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        RouterBlockEntity second = helper.getBlockEntity(otherChest.above());
        ServerPlayer player = playerWithTablet(helper);
        String name = uniqueName(helper, "arvt");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, first);
                    waitLoaded(helper, second);
                })
                .thenExecute(() -> {
                    first.setName(name + " a");
                    second.setName(name + " b");
                    fakeLinks(first, helper, 300, CHEST_ID, 0);
                    NodeIndex.placedBy(first, player.getUUID());
                    NodeIndex.placedBy(second, player.getUUID());
                    NodeKey key = key(first);
                    TabletMenu menu = openTablet(player, 144, name);
                    eq(helper, fresh(menu).nodes().size(), 2, "os dois nós antes de abrir");
                    helper.assertTrue(tree(player, menu, Action.OPEN, key, 0)
                            && tree(player, menu, Action.SHOW_ALL, key, first.remoteMachines().machine(0).group()),
                            "não abriu com todas");
                    TabletSnapshot.Page page = fresh(menu);
                    eq(helper, page.nodes().size() + page.rows().rows().size(), TabletSnapshot.MAX_NODES, "linhas na página");
                    eq(helper, page.nodes().size(), 1, "o segundo nó coube");
                    eq(helper, page.nodes().get(0).key(), key, "o nó aberto");
                    eq(helper, page.matchingNodes(), 2, "nós que casam");
                    eq(helper, page.rows().lives().size(), TabletSnapshot.MAX_NODES - 3, "estados das máquinas à vista");
                    roundTrip(helper, page);
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ o estado e a vazão

    /** O roteador num baú com pedra, extraindo para um baú remoto vinculado que recebe por cima. */
    private static RouterBlockEntity movingRouter(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        return router;
    }

    private static void startMoving(GameTestHelper helper, RouterBlockEntity router, ServerPlayer player, String name) {
        router.setName(name);
        router.setRangeUpgrade(rangeUpgrade());
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        linkInNetwork(helper, router, REMOTE_CHEST);
        profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
        NodeIndex.placedBy(router, player.getUUID());
    }

    private static @Nullable MachineLive machineLive(TabletMenu menu) {
        List<MachineLive> lives = menu.snapshot().page().rows().lives();
        return lives.isEmpty() ? null : lives.get(0);
    }

    /**
     * Uma máquina vinculada recebendo itens: depois de duas amostras, a linha dela fica "movendo" com vazão; a mudança
     * só de estado e vazão sai sozinha, no pacote pequeno.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void movingMachineHasRateInTheSmallPacket(GameTestHelper helper) {
        RouterBlockEntity router = movingRouter(helper);
        ServerPlayer player = playerWithTablet(helper);
        String name = uniqueName(helper, "arvm");
        TabletMenu[] menu = new TabletMenu[1];
        AtomicBoolean livePacket = new AtomicBoolean();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    startMoving(helper, router, player, name);
                    menu[0] = openTablet(player, 145, name);
                    helper.assertTrue(tree(player, menu[0], Action.OPEN, key(router), 0), "não abriu");
                    // Fora da tela do jogador, o servidor não sincroniza o menu sozinho: os pacotes ficam para o teste.
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
                        if (payload instanceof TabletLivePayload live && !page && live.machines().size() == 1
                                && live.machines().get(0).status() == MachineStatus.MOVING) {
                            livePacket.set(true);
                        }
                    }
                    MachineLive live = machineLive(menu[0]);
                    helper.assertTrue(live != null && live.status() == MachineStatus.MOVING
                            && live.received(ResourceType.ITEM) > 0 && livePacket.get(),
                            "a máquina não ficou movendo no pacote pequeno: " + live);
                })
                .thenExecute(() -> {
                    MachineLive live = machineLive(menu[0]);
                    eq(helper, live.given(ResourceType.ITEM), 0L, "o baú remoto só recebe");
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    /** Um destino remoto sem espaço, depois de recusas seguidas, dá "entrada cheia" na linha da máquina. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void fullDestinationShowsInputFull(GameTestHelper helper) {
        RouterBlockEntity router = movingRouter(helper);
        Container remote = container(helper, REMOTE_CHEST);
        for (int slot = 0; slot < remote.getContainerSize(); slot++) {
            remote.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        container(helper, CHEST).setItem(0, new ItemStack(Items.DIAMOND, 10));
        ServerPlayer player = playerWithTablet(helper);
        String name = uniqueName(helper, "arvc");
        TabletMenu[] menu = new TabletMenu[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    startMoving(helper, router, player, name);
                    menu[0] = openTablet(player, 146, name);
                    helper.assertTrue(tree(player, menu[0], Action.OPEN, key(router), 0), "não abriu");
                })
                .thenWaitUntil(() -> {
                    menu[0].pollSnapshot();
                    MachineLive live = machineLive(menu[0]);
                    helper.assertTrue(live != null && live.status() == MachineStatus.FULL, "sem entrada cheia: " + live);
                })
                .thenExecute(() -> player.containerMenu = player.inventoryMenu)
                .thenSucceed();
    }

    /** Com o roteador descarregado, as linhas de máquina continuam, do índice, como descarregadas e sem vazão. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void unloadedRouterKeepsMachineRows(GameTestHelper helper) {
        RouterBlockEntity router = movingRouter(helper);
        ServerPlayer player = playerWithTablet(helper);
        String name = uniqueName(helper, "arvd");
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    startMoving(helper, router, player, name);
                    TabletMenu menu = openTablet(player, 147, name);
                    helper.assertTrue(tree(player, menu, Action.OPEN, key(router), 0), "não abriu");
                    int loadedRows = fresh(menu).rows().rows().size();
                    NodeIndex.untrack(router);
                    try {
                        TabletSnapshot.Page page = fresh(menu);
                        eq(helper, page.rows().rows().size(), loadedRows, "linhas com o roteador descarregado");
                        helper.assertTrue(page.rows().rows().get(0) instanceof Attached attached
                                && attached.side().isEmpty(), "o lado da presa sem o roteador");
                        eq(helper, page.rows().lives(), List.of(MachineLive.still(MachineStatus.UNLOADED)),
                                "a máquina descarregada");
                    } finally {
                        NodeIndex.track(router);
                        player.containerMenu = player.inventoryMenu;
                    }
                })
                .thenSucceed();
    }

    /**
     * A Lista escolhe o nó e, logo depois, o alvo. Com A escolhido, a escolha recusada de um B que o jogador não vê
     * larga A, e o grupo de A pedido em seguida não muda o alvo de A.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void refusedChoiceDropsPreviousNode(GameTestHelper helper) {
        RouterBlockEntity a = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.CHEST);
        helper.setBlock(new BlockPos(0, 2, 0), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        RouterBlockEntity b = helper.getBlockEntity(new BlockPos(0, 2, 0));
        ServerPlayer player = playerWithTablet(helper);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, a);
                    waitLoaded(helper, b);
                })
                .thenExecute(() -> {
                    NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
                    a.setNetworkId(data.create(player.getUUID(), "escolhida").id());
                    b.setNetworkId(data.create(UUID.randomUUID(), "alheia").id());
                    a.setRangeUpgrade(rangeUpgrade());
                    fakeLinks(a, helper, 2, CHEST_ID, 0);
                    NodeIndex.placedBy(a, player.getUUID());
                    NodeIndex.track(b);
                    int group = a.remoteMachines().machine(0).group();
                    TabletMenu menu = openTablet(player, 148, uniqueName(helper, "arve"));
                    try {
                        helper.assertTrue(TabletPayloads.handleSelectNode(player,
                                new TabletSelectNodePayload(148, Optional.of(key(a)))), "não escolheu A");
                        helper.assertFalse(TabletPayloads.handleSelectNode(player,
                                new TabletSelectNodePayload(148, Optional.of(key(b)))), "escolheu um nó que não vê");
                        eq(helper, menu.chosenNode(), null, "a recusa não largou A");
                        helper.assertFalse(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.group(148, group, CHEST_ID)), "o grupo de A valeu sem nó escolhido");
                        helper.assertTrue(menu.targetRef().target().attached(), "o alvo de A mudou");
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                })
                .thenSucceed();
    }
}
