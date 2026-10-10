package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.RemoteRouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.RoleFilter;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TabletActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMoveNodesPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletOpenNodePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPayloads;
import io.github.matheusanbs.wirelessautomate.packet.TabletQueryPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Tablet de rede: o índice de nós ({@link NodeIndex}), o snapshot do {@link TabletMenu} (só o que o
 * jogador pode ver) e os handlers dos pacotes (mover nós, ações de redes e grupos, abrir à
 * distância). Os testes do lote dividem o índice e as redes, então cada um cria as suas e confere
 * pertinência, nunca contagens totais.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TabletGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);

    /** Baú em {@code machine} com um roteador em cima (facing=UP), com todas as abas em {@code network}. */
    private static RouterBlockEntity chestWithRouter(GameTestHelper helper, BlockPos machine, @Nullable UUID network) {
        helper.setBlock(machine, Blocks.CHEST);
        BlockPos pos = machine.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, pos);
        router.setNetworkId(network);
        return router;
    }

    /** Como {@code assertValueEqual}, mas sem derrubar o servidor quando o valor lido é nulo. */
    private static void eq(GameTestHelper helper, @Nullable Object actual, @Nullable Object expected, String what) {
        helper.assertTrue(java.util.Objects.equals(actual, expected), what + ": esperado " + expected + ", veio " + actual);
    }

    private static NodeKey key(RouterBlockEntity router) {
        return NodeKey.of(router.getLevel(), router.getBlockPos());
    }

    private static NodeIndex index(GameTestHelper helper) {
        return NodeIndex.get(helper.getLevel().getServer());
    }

    private static NetworkSavedData data(GameTestHelper helper) {
        return NetworkSavedData.get(helper.getLevel().getServer());
    }

    /** Jogador falso com um Tablet no inventário, perto de {@code pos}. */
    @SuppressWarnings("removal")
    private static ServerPlayer playerWithTablet(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(pos).above(2)));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        return player;
    }

    private static TabletMenu openTablet(ServerPlayer player, int containerId) {
        TabletMenu menu = new TabletMenu(containerId, player.getInventory(), player);
        player.containerMenu = menu;
        return menu;
    }

    private static void close(ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
    }

    /** Força a remontagem do snapshot e o devolve. */
    private static TabletSnapshot fresh(TabletMenu menu) {
        menu.setQuery(menu.query());
        menu.pollSnapshot();
        return menu.snapshot();
    }

    private static boolean listed(TabletSnapshot snapshot, NodeKey key) {
        return snapshot.nodes().stream().anyMatch(node -> node.key().equals(key));
    }

    private static @Nullable NodeView node(TabletSnapshot snapshot, NodeKey key) {
        return snapshot.nodes().stream().filter(node -> node.key().equals(key)).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ índice

    @GameTest(template = "empty")
    public static void indexFollowsTheRouter(GameTestHelper helper) {
        UUID network = data(helper).create(UUID.randomUUID(), "teste-indice").id();
        RouterBlockEntity router = chestWithRouter(helper, A, network);
        NodeKey key = key(router);
        router.configureFace(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0, RedstoneMode.IGNORE);
        router.setMode(ResourceType.FLUID, Direction.NORTH, PortMode.BOTH);
        UUID placer = UUID.randomUUID();
        NodeIndex.placedBy(router, placer);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad"))
                .thenExecute(() -> {
                    NodeIndex.Entry entry = index(helper).entry(key);
                    helper.assertTrue(entry != null, "roteador fora do índice");
                    helper.assertTrue(entry.loaded(), "carregado");
                    eq(helper, entry.network(ResourceType.ITEM), network, "rede de itens");
                    eq(helper, entry.tier(), RouterTier.BASIC, "tier");
                    eq(helper, entry.placedBy(), placer, "quem colocou");
                    eq(helper, entry.machine().getPath(), "chest", "máquina");
                    helper.assertTrue((entry.roles() & NodeIndex.role(ResourceType.ITEM, NodeIndex.EXTRACT)) != 0,
                            "papel extrai itens");
                    helper.assertTrue((entry.roles() & NodeIndex.role(ResourceType.FLUID, NodeIndex.STORAGE)) != 0,
                            "papel armazém de fluidos");
                    helper.assertTrue((entry.roles() & NodeIndex.role(ResourceType.ITEM, NodeIndex.INSERT)) == 0,
                            "papel insere itens a mais");

                    // nome, tier e rede de uma aba atualizam o índice
                    router.setName("Baú do índice");
                    RouterBlock.tryUpgrade(helper.getLevel(), router.getBlockPos(), RouterTier.ADVANCED);
                    router.setNetworkId(ResourceType.ENERGY, null);
                    eq(helper, entry.name(), "Baú do índice", "nome");
                    eq(helper, entry.tier(), RouterTier.ADVANCED, "tier depois do núcleo");
                    helper.assertTrue(entry.network(ResourceType.ENERGY) == null, "energia sem rede");

                    // ida e volta pelo arquivo
                    CompoundTag saved = index(helper).save(new CompoundTag());
                    NodeIndex.Entry loaded = NodeIndex.load(saved).entry(key);
                    helper.assertTrue(loaded != null, "nó perdido ao salvar");
                    eq(helper, loaded.name(), "Baú do índice", "nome salvo");
                    eq(helper, loaded.roles(), entry.roles(), "papéis salvos");
                    eq(helper, loaded.network(ResourceType.ITEM), network, "rede salva");
                    eq(helper, loaded.placedBy(), placer, "dono salvo");
                    helper.assertFalse(loaded.loaded(), "carregado não é salvo");

                    // descarregar mantém o nó no índice, como descarregado
                    router.onChunkUnloaded();
                    helper.assertFalse(entry.loaded(), "descarregado continua carregado");
                    helper.assertTrue(index(helper).entry(key) != null, "descarregar tirou do índice");
                    router.onLoad();
                    helper.assertTrue(entry.loaded(), "recarregado");
                })
                .thenExecute(() -> helper.setBlock(A.above(), Blocks.AIR))
                .thenExecute(() -> {
                    helper.assertTrue(index(helper).entry(key) == null, "roteador quebrado ainda no índice");
                    helper.succeed();
                });
    }

    // ------------------------------------------------------------------ snapshot

    @GameTest(template = "empty")
    public static void tabletShowsOnlyWhatThePlayerCanSee(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        NetworkSavedData data = data(helper);
        WaNetwork own = data.create(player.getUUID(), "teste-tablet-propria");
        WaNetwork foreign = data.create(UUID.randomUUID(), "teste-tablet-alheia");
        RouterBlockEntity mine = chestWithRouter(helper, A, own.id());
        mine.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity theirs = chestWithRouter(helper, B, foreign.id());
        TabletMenu menu = openTablet(player, 70);
        try {
            TabletSnapshot snapshot = fresh(menu);
            helper.assertFalse(snapshot.operator(), "jogador falso é operador");
            helper.assertTrue(listed(snapshot, key(mine)), "nó da rede própria fora da lista");
            helper.assertFalse(listed(snapshot, key(theirs)), "nó da rede alheia na lista");
            helper.assertTrue(snapshot.network(own.id()).isPresent(), "rede própria fora do snapshot");
            helper.assertTrue(snapshot.network(foreign.id()).isEmpty(), "rede alheia privada no snapshot");
            NodeView view = node(snapshot, key(mine));
            eq(helper, view.network(ResourceType.ITEM), Optional.of(own.id()), "rede do nó");
            helper.assertTrue(view.status() != NodeStatus.UNLOADED, "nó carregado aparece descarregado");

            // papel e busca
            helper.assertTrue(TabletPayloads.handleQuery(player, new TabletQueryPayload(70,
                    new Query("", RoleFilter.INSERT, Optional.empty(), 0))), "consulta recusada");
            menu.pollSnapshot();
            helper.assertFalse(listed(menu.snapshot(), key(mine)), "filtro por papel deixou passar");
            TabletPayloads.handleQuery(player, new TabletQueryPayload(70, new Query("propria", RoleFilter.EXTRACT, Optional.empty(), 0)));
            menu.pollSnapshot();
            helper.assertTrue(listed(menu.snapshot(), key(mine)), "busca pelo nome da rede");
            TabletPayloads.handleQuery(player, new TabletQueryPayload(70, new Query("nada-assim", RoleFilter.ALL, Optional.empty(), 0)));
            menu.pollSnapshot();
            helper.assertTrue(menu.snapshot().nodes().isEmpty(), "busca sem resultado trouxe nós");
            helper.assertFalse(TabletPayloads.handleQuery(player, new TabletQueryPayload(71, Query.DEFAULT)),
                    "consulta com outro containerId");

            // rede alheia pública aparece (para escolher), mas os nós dela não
            data.update(foreign.withPublic(true));
            TabletPayloads.handleQuery(player, new TabletQueryPayload(70, Query.DEFAULT));
            menu.pollSnapshot();
            helper.assertTrue(menu.snapshot().network(foreign.id()).map(n -> n.isPublic() && !n.manageable())
                    .orElse(false), "rede pública alheia");
            helper.assertFalse(listed(menu.snapshot(), key(theirs)), "nó de rede pública alheia na lista");

            // quem colocou vê o nó mesmo fora das redes dele
            NodeIndex.placedBy(theirs, player.getUUID());
            menu.setQuery(Query.DEFAULT);
            menu.pollSnapshot();
            helper.assertTrue(listed(menu.snapshot(), key(theirs)), "nó colocado pelo jogador fora da lista");

            // sem o Tablet o menu não vale mais
            player.getInventory().setItem(0, ItemStack.EMPTY);
            helper.assertFalse(menu.stillValid(player), "menu válido sem o Tablet");
            helper.succeed();
        } finally {
            close(player);
        }
    }

    @GameTest(template = "empty")
    public static void listFiltersByType(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        UUID network = data(helper).create(player.getUUID(), "teste-filtro-tipo").id();
        RouterBlockEntity items = chestWithRouter(helper, A, network);
        items.configureFace(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0, RedstoneMode.IGNORE);
        RouterBlockEntity energy = chestWithRouter(helper, B, network);
        energy.configureFace(ResourceType.ENERGY, Direction.UP, PortMode.INSERT, 0, RedstoneMode.IGNORE);
        NodeIndex.placedBy(items, player.getUUID());
        NodeIndex.placedBy(energy, player.getUUID());
        TabletMenu menu = openTablet(player, 81);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(energy), "sem onLoad"))
                .thenExecute(() -> {
                    menu.setQuery(new Query("", RoleFilter.ALL, Optional.of(ResourceType.ENERGY), 0));
                    TabletSnapshot s = fresh(menu);
                    helper.assertTrue(listed(s, key(energy)), "nó de energia fora do filtro de energia");
                    helper.assertFalse(listed(s, key(items)), "nó só de itens no filtro de energia");
                    helper.assertTrue(s.query().type().equals(Optional.of(ResourceType.ENERGY)), "filtro perdido");
                    helper.assertTrue(s.network(network).map(n -> n.types().size() == ResourceType.values().length)
                            .orElse(false), "estatística sem um item por tipo");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void forcedRebuildsAreLimitedPerPlayer(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        TabletMenu menu = openTablet(player, 78);
        // Pedidos seguidos no mesmo tick: só as fichas do jogador remontam na hora.
        int requests = TabletMenu.FORCED_PER_SECOND * 2;
        int built = 0;
        for (int i = 0; i < requests; i++) {
            menu.setQuery(new Query("limite-" + i, RoleFilter.ALL, Optional.empty(), 0));
            menu.pollSnapshot();
            if (menu.snapshot().query().search().equals("limite-" + i)) {
                built++;
            }
        }
        helper.assertTrue(built > 0 && built <= TabletMenu.FORCED_PER_SECOND, "remontagens forçadas: " + built);
        helper.assertTrue(menu.pollSnapshot() == null, "remontou sem ficha");

        // Fechar e abrir de novo não devolve as fichas.
        TabletMenu again = openTablet(player, 79);
        again.setQuery(new Query("reaberto", RoleFilter.ALL, Optional.empty(), 0));
        again.pollSnapshot();
        helper.assertFalse(again.snapshot().query().search().equals("reaberto"), "reabrir zerou o limite");

        helper.startSequence()
                // o pedido não se perde: é atendido quando a ficha volta
                .thenWaitUntil(() -> {
                    again.pollSnapshot();
                    helper.assertTrue(again.snapshot().query().search().equals("reaberto"), "pedido perdido");
                })
                .thenExecute(() -> {
                    // cabeçalho e página vão à parte; o cliente junta os dois
                    TabletSnapshot server = again.snapshot();
                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer());
                    TabletSnapshot.HEADER_CODEC.encode(buf, server);
                    TabletSnapshot.Page.STREAM_CODEC.encode(buf, server.page());
                    TabletMenu client = new TabletMenu(80, player.getInventory(), TabletSnapshot.HEADER_CODEC.decode(buf));
                    helper.assertTrue(client.snapshot().nodes().isEmpty(), "cabeçalho trouxe nós");
                    client.applyPage(TabletSnapshot.Page.STREAM_CODEC.decode(buf));
                    client.applyHeader(server);
                    helper.assertTrue(client.snapshot().equals(server), "cliente diferente do servidor");
                    close(player);
                    helper.succeed();
                });
    }

    // ------------------------------------------------------------------ mover nós

    @GameTest(template = "empty")
    public static void moveNodesByTabOrAll(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        NetworkSavedData data = data(helper);
        WaNetwork first = data.create(player.getUUID(), "teste-mover-1");
        WaNetwork second = data.create(player.getUUID(), "teste-mover-2");
        WaNetwork foreign = data.create(UUID.randomUUID(), "teste-mover-alheia");
        RouterBlockEntity a = chestWithRouter(helper, A, first.id());
        RouterBlockEntity b = chestWithRouter(helper, B, first.id());
        TabletMenu menu = openTablet(player, 72);
        try {
            List<NodeKey> both = List.of(key(a), key(b));
            int moved = TabletPayloads.handleMoveNodes(player, new TabletMoveNodesPayload(72, both,
                    Optional.of(ResourceType.ITEM), Optional.of(second.id())));
            eq(helper, moved, 2, "nós movidos");
            for (RouterBlockEntity router : List.of(a, b)) {
                eq(helper, router.networkId(ResourceType.ITEM), second.id(), "itens na segunda rede");
                eq(helper, router.networkId(ResourceType.FLUID), first.id(), "fluidos ficaram");
                eq(helper, router.networkId(ResourceType.ENERGY), first.id(), "energia ficou");
            }
            eq(helper, index(helper).entry(key(a)).network(ResourceType.ITEM), second.id(),
                    "índice depois de mover");

            // rede alheia privada: recusado e nada muda
            eq(helper, TabletPayloads.handleMoveNodes(player, new TabletMoveNodesPayload(72, both,
                    Optional.empty(), Optional.of(foreign.id()))), -1, "aceitou rede alheia privada");
            eq(helper, a.networkId(ResourceType.FLUID), first.id(), "mudou com recusa");
            // pública: aceito
            data.update(foreign.withPublic(true));
            helper.assertTrue(io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.capture(a, player).networks()
                    .stream().anyMatch(e -> e.id().equals(foreign.id()) && !e.owned()), "rede pública fora do seletor da aba");
            helper.assertTrue(io.github.matheusanbs.wirelessautomate.packet.ModPayloads.canUse(player,
                    data.network(foreign.id())), "canUse recusou rede pública");
            eq(helper, TabletPayloads.handleMoveNodes(player, new TabletMoveNodesPayload(72, List.of(key(a)),
                    Optional.of(ResourceType.ENERGY), Optional.of(foreign.id()))), 1, "rede pública alheia");
            eq(helper, a.networkId(ResourceType.ENERGY), foreign.id(), "energia na rede pública");

            // todas as abas, sem rede
            eq(helper, TabletPayloads.handleMoveNodes(player, new TabletMoveNodesPayload(72, List.of(key(b)),
                    Optional.empty(), Optional.empty())), 1, "tirar da rede");
            helper.assertFalse(b.hasNetwork(), "b ainda em alguma rede");

            // químicos, outro containerId e nó invisível
            eq(helper, TabletPayloads.handleMoveNodes(player, new TabletMoveNodesPayload(72, both,
                    Optional.of(ResourceType.CHEMICAL), Optional.of(first.id()))), -1, "químicos");
            eq(helper, TabletPayloads.handleMoveNodes(player, new TabletMoveNodesPayload(73, both,
                    Optional.empty(), Optional.of(first.id()))), -1, "outro containerId");
            RouterBlockEntity hidden = chestWithRouter(helper, new BlockPos(2, 1, 0),
                    data.create(UUID.randomUUID(), "teste-mover-oculta").id());
            UUID before = hidden.networkId(ResourceType.ITEM);
            eq(helper, TabletPayloads.handleMoveNodes(player, new TabletMoveNodesPayload(72,
                    List.of(key(hidden)), Optional.empty(), Optional.of(first.id()))), 0, "moveu nó que não vê");
            eq(helper, hidden.networkId(ResourceType.ITEM), before, "nó invisível mudou");
            helper.succeed();
        } finally {
            close(player);
        }
    }

    // ------------------------------------------------------------------ redes, grupos e pausa

    @GameTest(template = "empty")
    public static void networkActionsRespectOwnershipAndPrivacy(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        NetworkSavedData data = data(helper);
        UUID me = player.getUUID();
        TabletMenu menu = openTablet(player, 74);
        try {
            helper.assertTrue(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.NETWORK_CREATE,
                    Optional.empty(), Optional.empty(), "  Rede do Tablet  ", 0)), "criar");
            WaNetwork own = data.byName(me, "Rede do Tablet");
            helper.assertTrue(own != null && !own.isPublic(), "rede nova é privada");
            helper.assertFalse(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.NETWORK_CREATE,
                    Optional.empty(), Optional.empty(), "rede do tablet", 0)), "nome repetido");
            helper.assertFalse(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.NETWORK_CREATE,
                    Optional.empty(), Optional.empty(), "x".repeat(40), 0)), "nome comprido");

            helper.assertTrue(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.NETWORK_RENAME,
                    Optional.of(own.id()), Optional.empty(), "Renomeada", 0)), "renomear");
            helper.assertTrue(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.NETWORK_COLOR,
                    Optional.of(own.id()), Optional.empty(), "", 0x123456)), "cor");
            helper.assertTrue(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.NETWORK_PUBLIC,
                    Optional.of(own.id()), Optional.empty(), "", 1)), "pública");
            WaNetwork changed = data.network(own.id());
            eq(helper, changed.name(), "Renomeada", "nome");
            eq(helper, changed.color(), 0x123456, "cor");
            helper.assertTrue(changed.isPublic(), "privacidade");

            // rede alheia privada: não gerencia nem usa; pública: usa, mas não gerencia nem agrupa
            WaNetwork foreign = data.create(UUID.randomUUID(), "teste-acoes-alheia");
            helper.assertFalse(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.NETWORK_USE,
                    foreign.id())), "usou rede alheia privada");
            helper.assertFalse(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.NETWORK_PUBLIC,
                    Optional.of(foreign.id()), Optional.empty(), "", 1)), "mudou privacidade alheia");
            helper.assertFalse(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.NETWORK_REMOVE,
                    foreign.id())), "removeu rede alheia");
            data.update(foreign.withPublic(true));
            helper.assertTrue(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.NETWORK_USE,
                    foreign.id())), "não usou rede pública");
            eq(helper, data.activeNetwork(me), foreign.id(), "rede ativa");

            helper.assertTrue(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.GROUP_CREATE,
                    Optional.empty(), Optional.empty(), "Grupo do Tablet", 0)), "criar grupo");
            WaGroup group = data.groups().stream().filter(g -> g.owner().equals(me)).findFirst().orElseThrow();
            helper.assertFalse(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.GROUP_ADD_NETWORK,
                    Optional.of(group.id()), Optional.of(foreign.id()), "", 0)), "agrupou rede alheia");
            helper.assertTrue(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.GROUP_ADD_NETWORK,
                    Optional.of(group.id()), Optional.of(own.id()), "", 0)), "agrupar");
            eq(helper, data.group(group.id()).networks(), List.of(own.id()), "redes do grupo");
            TabletSnapshot snapshot = fresh(menu);
            helper.assertTrue(snapshot.groups().stream().anyMatch(g -> g.id().equals(group.id())
                    && g.networks().contains(own.id())), "grupo no snapshot");

            // remover a rede a tira do grupo
            helper.assertTrue(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.NETWORK_REMOVE,
                    own.id())), "remover");
            helper.assertTrue(data.group(group.id()).networks().isEmpty(), "rede removida ficou no grupo");
            helper.assertTrue(TabletPayloads.handleAction(player, new TabletActionPayload(74, Action.GROUP_REMOVE,
                    group.id())), "desfazer grupo");
            helper.assertTrue(data.group(group.id()) == null, "grupo ficou");
            helper.succeed();
        } finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pausedGroupStopsTransport(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        NetworkSavedData data = data(helper);
        WaNetwork network = data.create(player.getUUID(), "teste-pausa");
        RouterBlockEntity source = chestWithRouter(helper, A, network.id());
        source.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity target = chestWithRouter(helper, B, network.id());
        target.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        TabletMenu menu = openTablet(player, 76);
        WaGroup group = data.createGroup(player.getUUID(), "teste-pausa-grupo");
        data.updateGroup(group.withNetwork(network.id()));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source)
                        && NetworkManager.get().contains(target), "sem onLoad"))
                .thenExecute(() -> {
                    helper.assertTrue(TabletPayloads.handleAction(player, new TabletActionPayload(76, Action.GROUP_PAUSE,
                            Optional.of(group.id()), Optional.empty(), "", 1)), "pausar");
                    helper.assertTrue(data.isPaused(network.id()), "rede não pausada");
                    ((ChestBlockEntity) GameTestCompat.getBlockEntity(helper, A)).setItem(0, new ItemStack(Items.DIAMOND, 10));
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    eq(helper, count(helper, B, Items.DIAMOND), 0, "itens passaram com a rede pausada");
                    NodeView view = node(fresh(menu), key(source));
                    helper.assertTrue(view != null && view.status() == NodeStatus.PAUSED,
                            "status " + (view == null ? "sem nó" : view.status()) + " em vez de pausado");
                    helper.assertTrue(menu.snapshot().network(network.id()).map(n -> n.paused()).orElse(false),
                            "rede pausada no snapshot");
                    helper.assertTrue(TabletPayloads.handleAction(player, new TabletActionPayload(76, Action.GROUP_PAUSE,
                            Optional.of(group.id()), Optional.empty(), "", 0)), "retomar");
                })
                .thenWaitUntil(() -> eq(helper, count(helper, B, Items.DIAMOND), 10, "itens depois de retomar"))
                .thenExecute(() -> {
                    close(player);
                    helper.succeed();
                });
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        ChestBlockEntity chest = GameTestCompat.getBlockEntity(helper, pos);
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) {
                total += chest.getItem(i).getCount();
            }
        }
        return total;
    }

    // ------------------------------------------------------------------ abrir à distância

    @GameTest(template = "empty")
    public static void openRouterFromAfar(GameTestHelper helper) {
        // Porte 1.20.1: no main, um MockPlayer sobrescrevia o openMenu do NeoForge; no Forge a abertura é estática
        // (NetworkHooks.openScreen) e as telas abrem pelo caminho de verdade, com o pacote na conexão embutida.
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        WaNetwork network = data(helper).create(player.getUUID(), "teste-abrir");
        RouterBlockEntity router = chestWithRouter(helper, A, network.id());
        Vec3 routerCenter = Vec3.atCenterOf(router.getBlockPos());
        try {
            // 20 blocos: longe para a tela comum (8), dentro do alcance do Básico (128)
            player.moveTo(routerCenter.add(20, 0, 0));
            TabletPayloads.handleOpenTablet(player);
            helper.assertTrue(player.containerMenu instanceof TabletMenu, "tecla de atalho não abriu o Tablet");
            int tablet = player.containerMenu.containerId;
            helper.assertTrue(TabletPayloads.handleOpenNode(player, new TabletOpenNodePayload(tablet, key(router))),
                    "não abriu de longe");
            helper.assertTrue(player.containerMenu instanceof RemoteRouterMenu remote && remote.router() == router,
                    "tela do roteador à distância");
            helper.assertTrue(player.containerMenu.stillValid(player), "tela remota fechou a 20 blocos");

            // fora do alcance do tier
            player.moveTo(routerCenter.add(300, 0, 0));
            helper.assertFalse(player.containerMenu.stillValid(player), "tela remota valeu fora do alcance");
            TabletMenu.open(player);
            int again = player.containerMenu.containerId;
            helper.assertFalse(TabletPayloads.handleOpenNode(player, new TabletOpenNodePayload(again, key(router))),
                    "abriu fora do alcance");
            helper.assertTrue(player.containerMenu instanceof TabletMenu, "trocou de tela fora do alcance");

            // sem Tablet a tecla não abre
            player.containerMenu = player.inventoryMenu;
            player.getInventory().setItem(0, ItemStack.EMPTY);
            helper.assertFalse(TabletPayloads.handleOpenTablet(player), "abriu sem Tablet");
            helper.succeed();
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
    }
}
