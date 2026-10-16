package io.github.matheusanbs.wirelessautomate.gametest;

import com.mojang.authlib.GameProfile;
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
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickFilter;
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
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
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
    /**
     * Os testes com nós em rede pública alheia: o Tablet mostra esses nós a todo jogador, e eles entrariam nas
     * contagens dos testes da Lista que rodam em paralelo.
     */
    private static final String PUBLIC_NODES_BATCH = "wirelessautomate_public_nodes";

    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);

    /** Baú em {@code machine} com um roteador em cima (facing=UP), com todas as abas em {@code network}. */
    private static RouterBlockEntity chestWithRouter(GameTestHelper helper, BlockPos machine, @Nullable UUID network) {
        helper.setBlock(machine, Blocks.CHEST);
        BlockPos pos = machine.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
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
    private static ServerPlayer playerWithTablet(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = TestPlayers.inLevel(helper);
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
                    eq(helper, entry.networks(ResourceType.ITEM), List.of(network), "rede de itens");
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
                    helper.assertTrue(entry.networks(ResourceType.ENERGY).isEmpty(), "energia sem rede");

                    // ida e volta pelo arquivo
                    CompoundTag saved = index(helper).save(new CompoundTag(), helper.getLevel().registryAccess());
                    NodeIndex.Entry loaded = NodeIndex.load(saved, helper.getLevel().registryAccess()).entry(key);
                    helper.assertTrue(loaded != null, "nó perdido ao salvar");
                    eq(helper, loaded.name(), "Baú do índice", "nome salvo");
                    eq(helper, loaded.roles(), entry.roles(), "papéis salvos");
                    eq(helper, loaded.networks(ResourceType.ITEM), List.of(network), "rede salva");
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

    @GameTest(template = "empty", batch = PUBLIC_NODES_BATCH)
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
            eq(helper, view.networks(ResourceType.ITEM), List.of(own.id()), "rede do nó");
            helper.assertTrue(view.status() != NodeStatus.UNLOADED, "nó carregado aparece descarregado");

            // filtro rápido e busca
            helper.assertTrue(TabletPayloads.handleQuery(player, new TabletQueryPayload(70,
                    Query.DEFAULT.withQuick(Set.of(QuickFilter.NO_NETWORK)))), "consulta recusada");
            menu.pollSnapshot();
            helper.assertFalse(listed(menu.snapshot(), key(mine)), "filtro rápido deixou passar");
            TabletPayloads.handleQuery(player, new TabletQueryPayload(70, Query.of("propria")));
            menu.pollSnapshot();
            helper.assertTrue(listed(menu.snapshot(), key(mine)), "busca pelo nome da rede");
            TabletPayloads.handleQuery(player, new TabletQueryPayload(70, Query.of("nada-assim")));
            menu.pollSnapshot();
            helper.assertTrue(menu.snapshot().nodes().isEmpty(), "busca sem resultado trouxe nós");
            helper.assertFalse(TabletPayloads.handleQuery(player, new TabletQueryPayload(71, Query.DEFAULT)),
                    "consulta com outro containerId");

            // quem colocou vê o nó mesmo fora das redes dele
            NodeIndex.placedBy(theirs, player.getUUID());
            TabletPayloads.handleQuery(player, new TabletQueryPayload(70, Query.DEFAULT));
            menu.pollSnapshot();
            helper.assertTrue(listed(menu.snapshot(), key(theirs)), "nó colocado pelo jogador fora da lista");
            NodeIndex.placedBy(theirs, UUID.randomUUID());

            // rede alheia pública: aparece, sem gerenciar, e os nós dela entram na lista e na contagem
            data.update(foreign.withPublic(true));
            menu.setQuery(Query.DEFAULT);
            menu.pollSnapshot();
            helper.assertTrue(menu.snapshot().network(foreign.id()).map(n -> n.isPublic() && !n.manageable())
                    .orElse(false), "rede pública alheia");
            helper.assertTrue(listed(menu.snapshot(), key(theirs)), "nó de rede pública alheia fora da lista");
            helper.assertTrue(menu.snapshot().network(foreign.id()).map(n -> n.nodes() >= 1).orElse(false),
                    "nós da rede pública alheia fora da contagem");

            // sem o Tablet o menu não vale mais
            player.getInventory().setItem(0, ItemStack.EMPTY);
            helper.assertFalse(menu.stillValid(player), "menu válido sem o Tablet");
            helper.succeed();
        } finally {
            close(player);
            data.update(foreign);
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
                    menu.setQuery(Query.DEFAULT.withType(Optional.of(ResourceType.ENERGY)));
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
            menu.setQuery(Query.of("limite-" + i));
            menu.pollSnapshot();
            if (menu.snapshot().query().text().equals("limite-" + i)) {
                built++;
            }
        }
        helper.assertTrue(built > 0 && built <= TabletMenu.FORCED_PER_SECOND, "remontagens forçadas: " + built);
        helper.assertTrue(menu.pollSnapshot() == null, "remontou sem ficha");

        // Fechar e abrir de novo não devolve as fichas.
        TabletMenu again = openTablet(player, 79);
        again.setQuery(Query.of("reaberto"));
        again.pollSnapshot();
        helper.assertFalse(again.snapshot().query().text().equals("reaberto"), "reabrir zerou o limite");

        helper.startSequence()
                // o pedido não se perde: é atendido quando a ficha volta
                .thenWaitUntil(() -> {
                    again.pollSnapshot();
                    helper.assertTrue(again.snapshot().query().text().equals("reaberto"), "pedido perdido");
                })
                .thenExecute(() -> {
                    // cabeçalho e página vão à parte; o cliente junta os dois
                    TabletSnapshot server = again.snapshot();
                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                            helper.getLevel().registryAccess());
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

    // ------------------------------------------------------------------ consulta

    private static ResourceLocation id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    /** Máquina {@code block} em {@code machine} com um roteador em cima, nomeado e colocado pelo jogador. */
    private static RouterBlockEntity owned(GameTestHelper helper, ServerPlayer player, BlockPos machine, Block block,
            @Nullable UUID network, String name) {
        helper.setBlock(machine, block);
        BlockPos pos = machine.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        router.setNetworkId(network);
        router.setName(name);
        NodeIndex.placedBy(router, player.getUUID());
        return router;
    }

    private static void loaded(GameTestHelper helper, RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad");
        }
    }

    @GameTest(template = "empty")
    public static void tabletQueryMatchesText(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        WaNetwork alpha = data(helper).create(player.getUUID(), "teste-consulta-alfa");
        RouterBlockEntity chest = owned(helper, player, A, Blocks.CHEST, alpha.id(), "Cofre do fundo");
        RouterBlockEntity furnace = owned(helper, player, B, Blocks.FURNACE, null, "Forja");
        TabletMenu menu = openTablet(player, 100);

        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, chest, furnace))
                .thenExecute(() -> {
                    try {
                        TabletSnapshot s = search(menu, "fundo");
                        helper.assertTrue(listed(s, key(chest)) && !listed(s, key(furnace)), "pelo nome");
                        s = search(menu, "furnace");
                        helper.assertTrue(listed(s, key(furnace)) && !listed(s, key(chest)), "pelo id da máquina");
                        s = search(menu, "cofre fundo");
                        helper.assertTrue(listed(s, key(chest)), "palavras se somam");
                        s = search(menu, "cofre forja");
                        helper.assertTrue(!listed(s, key(chest)) && !listed(s, key(furnace)), "palavras são E");
                        s = search(menu, "alfa");
                        helper.assertTrue(listed(s, key(chest)) && !listed(s, key(furnace)), "pelo nome da rede");
                        BlockPos at = helper.absolutePos(B.above());
                        s = search(menu, at.getX() + " " + at.getY() + " " + at.getZ());
                        helper.assertTrue(listed(s, key(furnace)), "pelas coordenadas");
                        // o nome localizado da máquina é resolvido pelo cliente: o servidor recebe os ids
                        menu.setQuery(Query.of("forno").withTextMachines(List.of(id(Blocks.FURNACE))));
                        menu.pollSnapshot();
                        s = menu.snapshot();
                        helper.assertTrue(listed(s, key(furnace)) && !listed(s, key(chest)), "por textMachines");
                        helper.succeed();
                    } finally {
                        close(player);
                    }
                });
    }

    @GameTest(template = "empty")
    public static void tabletQueryAddsMachineAndNetworkLabels(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        WaNetwork alpha = data(helper).create(player.getUUID(), "teste-etiqueta-alfa");
        WaNetwork beta = data(helper).create(player.getUUID(), "teste-etiqueta-beta");
        RouterBlockEntity chest = owned(helper, player, A, Blocks.CHEST, alpha.id(), "Baú");
        RouterBlockEntity furnace = owned(helper, player, B, Blocks.FURNACE, beta.id(), "Forja");
        RouterBlockEntity bare = owned(helper, player, new BlockPos(2, 1, 0), Blocks.CHEST, null, "Solto");
        TabletMenu menu = openTablet(player, 101);

        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, chest, furnace, bare))
                .thenExecute(() -> {
                    try {
                        menu.setQuery(Query.DEFAULT.withMachines(List.of(id(Blocks.CHEST))));
                        menu.pollSnapshot();
                        TabletSnapshot s = menu.snapshot();
                        helper.assertTrue(listed(s, key(chest)) && listed(s, key(bare)) && !listed(s, key(furnace)),
                                "etiqueta de máquina");
                        menu.setQuery(Query.DEFAULT.withMachines(List.of(id(Blocks.CHEST)))
                                .withNetworks(List.of(alpha.id())));
                        menu.pollSnapshot();
                        s = menu.snapshot();
                        helper.assertTrue(listed(s, key(chest)) && !listed(s, key(bare)) && !listed(s, key(furnace)),
                                "máquina e rede somam");
                        menu.setQuery(Query.DEFAULT.withNetworks(List.of(alpha.id(), beta.id())));
                        menu.pollSnapshot();
                        s = menu.snapshot();
                        helper.assertTrue(listed(s, key(chest)) && listed(s, key(furnace)) && !listed(s, key(bare)),
                                "duas redes: alguma delas");
                        menu.setQuery(Query.DEFAULT.withMachines(List.of(id(Blocks.FURNACE)))
                                .withNetworks(List.of(alpha.id())));
                        menu.pollSnapshot();
                        helper.assertTrue(menu.snapshot().nodes().stream().noneMatch(n -> n.key().equals(key(chest))
                                || n.key().equals(key(furnace))), "máquina de uma e rede de outra");
                        helper.succeed();
                    } finally {
                        close(player);
                    }
                });
    }

    @GameTest(template = "empty")
    public static void tabletQuickFilters(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        WaNetwork alpha = data(helper).create(player.getUUID(), "teste-rapido-alfa");
        RouterBlockEntity ok = owned(helper, player, A, Blocks.CHEST, alpha.id(), "Normal");
        RouterBlockEntity bare = owned(helper, player, B, Blocks.CHEST, null, "Sem rede");
        RouterBlockEntity off = owned(helper, player, new BlockPos(2, 1, 0), Blocks.CHEST, alpha.id(), "Fora");
        TabletMenu menu = openTablet(player, 102);

        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, ok, bare, off))
                .thenExecute(() -> off.onChunkUnloaded())
                .thenExecute(() -> {
                    try {
                        menu.setQuery(Query.DEFAULT.withQuick(Set.of(QuickFilter.NO_NETWORK)));
                        menu.pollSnapshot();
                        TabletSnapshot s = menu.snapshot();
                        helper.assertTrue(!listed(s, key(ok)) && listed(s, key(bare)) && !listed(s, key(off)),
                                "sem rede");
                        menu.setQuery(Query.DEFAULT.withQuick(Set.of(QuickFilter.UNLOADED)));
                        menu.pollSnapshot();
                        s = menu.snapshot();
                        helper.assertTrue(!listed(s, key(ok)) && !listed(s, key(bare)) && listed(s, key(off)),
                                "descarregados");
                        menu.setQuery(Query.DEFAULT.withQuick(Set.of(QuickFilter.PROBLEM)));
                        menu.pollSnapshot();
                        s = menu.snapshot();
                        helper.assertTrue(!listed(s, key(ok)) && listed(s, key(bare)) && listed(s, key(off)),
                                "com problema");
                        menu.setQuery(Query.DEFAULT.withQuick(Set.of(QuickFilter.NO_NETWORK, QuickFilter.UNLOADED)));
                        menu.pollSnapshot();
                        s = menu.snapshot();
                        helper.assertTrue(!listed(s, key(ok)) && !listed(s, key(bare)) && !listed(s, key(off)),
                                "filtros ligados juntos são E");
                        // as contagens são dos nós visíveis, sem olhar a consulta
                        eq(helper, s.quickCounts().problem(), 2, "contagem com problema");
                        eq(helper, s.quickCounts().noNetwork(), 1, "contagem sem rede");
                        eq(helper, s.quickCounts().unloaded(), 1, "contagem descarregados");
                        off.onLoad();
                        helper.succeed();
                    } finally {
                        close(player);
                    }
                });
    }

    @GameTest(template = "empty")
    public static void tabletListIsOrderedByMachineThenName(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        WaNetwork net = data(helper).create(player.getUUID(), "teste-ordem");
        RouterBlockEntity beta = owned(helper, player, A, Blocks.CHEST, net.id(), "Beta");
        RouterBlockEntity forge = owned(helper, player, B, Blocks.FURNACE, net.id(), "Aaa");
        RouterBlockEntity alfa = owned(helper, player, new BlockPos(2, 1, 0), Blocks.CHEST, net.id(), "alfa");
        TabletMenu menu = openTablet(player, 103);

        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, beta, forge, alfa))
                .thenExecute(() -> {
                    try {
                        List<NodeKey> order = fresh(menu).nodes().stream().map(NodeView::key).toList();
                        eq(helper, order, List.of(key(alfa), key(beta), key(forge)),
                                "máquina, depois nome sem diferenciar maiúsculas");
                        helper.succeed();
                    } finally {
                        close(player);
                    }
                });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void tabletListStopsAt256(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        WaNetwork net = data(helper).create(player.getUUID(), "teste-limite");
        int count = TabletSnapshot.MAX_NODES + 4;
        List<RouterBlockEntity> routers = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlockPos machine = new BlockPos(i % 3, 5 + (i / 9) * 2, (i % 9) / 3);
            routers.add(owned(helper, player, machine, Blocks.CHEST, net.id(), String.format("n%03d", i)));
        }
        TabletMenu menu = openTablet(player, 104);

        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, routers.toArray(RouterBlockEntity[]::new)))
                .thenExecute(() -> {
                    try {
                        TabletSnapshot s = fresh(menu);
                        eq(helper, s.nodes().size(), TabletSnapshot.MAX_NODES, "nós enviados");
                        eq(helper, s.matchingNodes(), count, "total que casou");
                        eq(helper, s.totalNodes(), count, "total visível");
                        eq(helper, s.nodes().get(0).name(), "n000", "primeiro");
                        eq(helper, s.nodes().get(TabletSnapshot.MAX_NODES - 1).name(),
                                String.format("n%03d", TabletSnapshot.MAX_NODES - 1), "último");
                        helper.succeed();
                    } finally {
                        close(player);
                    }
                });
    }

    @GameTest(template = "empty")
    public static void tabletHeaderCarriesFacets(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        WaNetwork alpha = data(helper).create(player.getUUID(), "teste-facetas-alfa");
        WaNetwork beta = data(helper).create(player.getUUID(), "teste-facetas-beta");
        RouterBlockEntity one = owned(helper, player, A, Blocks.CHEST, alpha.id(), "Um");
        RouterBlockEntity two = owned(helper, player, B, Blocks.CHEST, alpha.id(), "Dois");
        RouterBlockEntity forge = owned(helper, player, new BlockPos(2, 1, 0), Blocks.FURNACE, alpha.id(), "Forja");
        forge.setFaceNetwork(ResourceType.ITEM, Direction.DOWN, beta.id());
        TabletMenu menu = openTablet(player, 105);

        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, one, two, forge))
                .thenExecute(() -> {
                    try {
                        // a busca não muda as facetas: elas são dos nós visíveis
                        menu.setQuery(Query.of("forja"));
                        menu.pollSnapshot();
                        TabletSnapshot s = menu.snapshot();
                        eq(helper, s.matchingNodes(), 1, "casou só a forja");
                        eq(helper, s.totalNodes(), 3, "nós visíveis");
                        eq(helper, facet(s.machineFacets(), id(Blocks.CHEST)), 2, "faceta do baú");
                        eq(helper, facet(s.machineFacets(), id(Blocks.FURNACE)), 1, "faceta da fornalha");
                        eq(helper, facet(s.networkFacets(), alpha.id()), 3, "faceta da rede alfa");
                        eq(helper, facet(s.networkFacets(), beta.id()), 1, "faceta da rede beta");

                        // ida e volta pelos codecs
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                                helper.getLevel().registryAccess());
                        TabletSnapshot.STREAM_CODEC.encode(buf, s);
                        helper.assertTrue(TabletSnapshot.STREAM_CODEC.decode(buf).equals(s), "codec do snapshot");
                        helper.succeed();
                    } finally {
                        close(player);
                    }
                });
    }

    private static int facet(List<? extends TabletSnapshot.Facet<?>> facets, Object id) {
        return facets.stream().filter(f -> f.id().equals(id)).mapToInt(TabletSnapshot.Facet::count).findFirst()
                .orElse(-1);
    }

    @GameTest(template = "empty")
    public static void queryCodecKeepsEveryFieldAndCapsLists(GameTestHelper helper) {
        List<ResourceLocation> many = java.util.stream.IntStream.range(0, 40)
                .mapToObj(i -> ResourceLocation.fromNamespaceAndPath("teste", "m" + i)).toList();
        List<UUID> networks = java.util.stream.IntStream.range(0, 12).mapToObj(i -> UUID.randomUUID()).toList();
        Query query = new Query("x".repeat(100), many, many, networks,
                Set.of(QuickFilter.PROBLEM, QuickFilter.UNLOADED), Optional.of(ResourceType.FLUID));
        eq(helper, query.text().length(), TabletSnapshot.MAX_TEXT, "texto cortado");
        eq(helper, query.textMachines().size(), TabletSnapshot.MAX_TEXT_MACHINES, "textMachines cortado");
        eq(helper, query.machines().size(), TabletSnapshot.MAX_LABELS, "machines cortado");
        eq(helper, query.networks().size(), TabletSnapshot.MAX_LABELS, "networks cortado");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        Query.STREAM_CODEC.encode(buf, query);
        eq(helper, Query.STREAM_CODEC.decode(buf), query, "ida e volta");
        helper.succeed();
    }

    // ------------------------------------------------------------------ modos por face no índice

    @GameTest(template = "empty")
    public static void indexKeepsFaceModes(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        UUID network = data(helper).create(player.getUUID(), "teste-modos").id();
        RouterBlockEntity router = owned(helper, player, A, Blocks.CHEST, network, "Modos");
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        router.setMode(ResourceType.FLUID, Direction.DOWN, PortMode.BOTH);
        NodeKey key = key(router);
        TabletMenu menu = openTablet(player, 106);

        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    try {
                        NodeIndex.Entry entry = index(helper).entry(key);
                        // pelos lados da máquina: Cima e Baixo do baú; 2 bits por lado, 6 lados por tipo
                        eq(helper, entry.faceMode(ResourceType.ITEM, RemoteSide.TOP), PortMode.EXTRACT, "itens, cima");
                        eq(helper, entry.faceMode(ResourceType.FLUID, RemoteSide.BOTTOM), PortMode.BOTH, "fluidos, baixo");
                        eq(helper, entry.faceMode(ResourceType.ENERGY, RemoteSide.LEFT), PortMode.NONE, "energia, esquerda");
                        eq(helper, (entry.faceModes() >> (RemoteSide.TOP.ordinal() * 2)) & 3L,
                                (long) PortMode.EXTRACT.ordinal(), "bits dos itens, cima");
                        eq(helper, (entry.faceModes() >> ((1 * 6 + RemoteSide.BOTTOM.ordinal()) * 2)) & 3L,
                                (long) PortMode.BOTH.ordinal(), "bits dos fluidos, baixo");

                        // o Tablet leva os modos no nó
                        NodeView view = node(fresh(menu), key);
                        eq(helper, view.faceModes(), entry.faceModes(), "faceModes no NodeView");
                        helper.assertTrue(view.profile().isEmpty(), "sem perfil copiado ainda");

                        // ida e volta pelo arquivo
                        CompoundTag saved = index(helper).save(new CompoundTag(), helper.getLevel().registryAccess());
                        NodeIndex.Entry loaded = NodeIndex.load(saved, helper.getLevel().registryAccess()).entry(key);
                        eq(helper, loaded.faceModes(), entry.faceModes(), "modos salvos");

                        // mundo antigo: sem a chave, tudo Nenhum
                        ListTag nodes = saved.getList("nodes", Tag.TAG_COMPOUND);
                        for (int i = 0; i < nodes.size(); i++) {
                            nodes.getCompound(i).remove("face_modes");
                        }
                        NodeIndex.Entry old = NodeIndex.load(saved, helper.getLevel().registryAccess()).entry(key);
                        eq(helper, old.faceModes(), 0L, "índice antigo");
                        eq(helper, old.faceMode(ResourceType.ITEM, RemoteSide.TOP), PortMode.NONE, "modo no índice antigo");

                        // mudar o modo de uma face muda o índice
                        int before = index(helper).dataVersion();
                        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                        eq(helper, entry.faceMode(ResourceType.ITEM, RemoteSide.TOP), PortMode.INSERT, "modo novo");
                        helper.assertTrue(index(helper).dataVersion() != before, "dataVersion não mudou");
                        helper.succeed();
                    } finally {
                        close(player);
                    }
                });
    }

    // ------------------------------------------------------------------ mover nós

    @GameTest(template = "empty", batch = PUBLIC_NODES_BATCH)
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
            eq(helper, index(helper).entry(key(a)).networks(ResourceType.ITEM), List.of(second.id()),
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
            // só os tipos carregados: a face de um tipo cujo mod não está aqui fica como está
            for (ResourceType type : io.github.matheusanbs.wirelessautomate.network.LoadedTypes.LIST) {
                helper.assertTrue(b.networks(type).length == 0, "b ainda em alguma rede de " + type);
            }

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
            data.update(foreign);
        }
    }

    /**
     * Mover pelo Tablet só tira faces de redes que o jogador gerencia (dono ou operador): uma face na
     * rede de outro dono fica, privada ou pública (pública deixa usar, não gerenciar); faces sem rede,
     * em rede apagada ou do próprio jogador vão. O operador move todas.
     */
    @GameTest(template = "empty", batch = PUBLIC_NODES_BATCH)
    public static void moveNodesKeepsOthersNetworks(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        NetworkSavedData data = data(helper);
        WaNetwork mine = data.create(player.getUUID(), "teste-dono-mina");
        WaNetwork target = data.create(player.getUUID(), "teste-dono-alvo");
        WaNetwork foreignPrivate = data.create(UUID.randomUUID(), "teste-dono-alheia");
        WaNetwork foreignPublic = data.create(UUID.randomUUID(), "teste-dono-publica").withPublic(true);
        data.update(foreignPublic);
        WaNetwork deleted = data.create(UUID.randomUUID(), "teste-dono-apagada");
        RouterBlockEntity router = chestWithRouter(helper, A, null);
        router.setFaceNetwork(ResourceType.ITEM, Direction.UP, foreignPrivate.id());
        router.setFaceNetwork(ResourceType.ITEM, Direction.DOWN, foreignPublic.id());
        router.setFaceNetwork(ResourceType.ITEM, Direction.NORTH, mine.id());
        router.setFaceNetwork(ResourceType.ITEM, Direction.SOUTH, deleted.id());
        data.remove(deleted.id());
        openTablet(player, 74);
        try {
            eq(helper, TabletPayloads.handleMoveNodes(player, new TabletMoveNodesPayload(74, List.of(key(router)),
                    Optional.of(ResourceType.ITEM), Optional.of(target.id()))), 1, "nós movidos");
            eq(helper, router.faceNetwork(ResourceType.ITEM, Direction.UP), foreignPrivate.id(), "privada alheia ficou");
            eq(helper, router.faceNetwork(ResourceType.ITEM, Direction.DOWN), foreignPublic.id(), "pública alheia ficou");
            for (Direction face : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
                eq(helper, router.faceNetwork(ResourceType.ITEM, face), target.id(), "face " + face + " no alvo");
            }
        } finally {
            close(player);
        }
        ServerPlayer operator = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-mock-operator"),
                net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            protected int getPermissionLevel() {
                return 2;
            }
        };
        operator.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        openTablet(operator, 75);
        try {
            eq(helper, TabletPayloads.handleMoveNodes(operator, new TabletMoveNodesPayload(75, List.of(key(router)),
                    Optional.of(ResourceType.ITEM), Optional.empty())), 1, "operador moveu");
            helper.assertTrue(router.networks(ResourceType.ITEM).length == 0, "operador deixou faces em rede");
            helper.succeed();
        } finally {
            close(operator);
            data.update(foreignPublic.withPublic(false));
        }
    }

    // ------------------------------------------------------------------ redes por face

    /** Liga a busca {@code search} e devolve o snapshot remontado. */
    private static TabletSnapshot search(TabletMenu menu, String search) {
        menu.setQuery(Query.of(search));
        menu.pollSnapshot();
        return menu.snapshot();
    }

    @GameTest(template = "empty")
    public static void nodeListedInBothFaceNetworks(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        NetworkSavedData data = data(helper);
        WaNetwork mine = data.create(player.getUUID(), "teste-faces-mina");
        WaNetwork depot = data.create(player.getUUID(), "teste-faces-armazem");
        RouterBlockEntity router = chestWithRouter(helper, A, null);
        router.setFaceNetwork(ResourceType.ITEM, Direction.UP, mine.id());
        router.setFaceNetwork(ResourceType.ITEM, Direction.DOWN, depot.id());
        NodeKey key = key(router);
        TabletMenu menu = openTablet(player, 90);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(listed(search(menu, "faces-mina"), key), "busca pela rede de Cima");
                        helper.assertTrue(listed(search(menu, "faces-armazem"), key), "busca pela rede de Baixo");
                        TabletSnapshot s = search(menu, "");
                        NodeView view = node(s, key);
                        helper.assertTrue(view != null, "nó fora da lista");
                        helper.assertTrue(view.networks(ResourceType.ITEM).contains(mine.id())
                                && view.networks(ResourceType.ITEM).contains(depot.id()),
                                "itens sem as duas redes: " + view.networks(ResourceType.ITEM));
                        helper.assertTrue(view.networks(ResourceType.FLUID).isEmpty(), "fluidos ganharam rede");
                        eq(helper, view.distinctNetworks(), 2, "redes distintas");
                        helper.assertFalse(view.removedNetwork(), "aviso de rede removida sem motivo");
                        for (WaNetwork network : List.of(mine, depot)) {
                            helper.assertTrue(s.network(network.id()).map(n -> n.nodes() >= 1).orElse(false),
                                    "o nó não conta em " + network.name());
                        }
                        helper.succeed();
                    } finally {
                        close(player);
                    }
                });
    }

    @GameTest(template = "empty")
    public static void nodeIndexFaceNetworksRoundTrip(GameTestHelper helper) {
        NetworkSavedData data = data(helper);
        UUID up = data.create(UUID.randomUUID(), "teste-indice-cima").id();
        UUID down = data.create(UUID.randomUUID(), "teste-indice-baixo").id();
        RouterBlockEntity router = chestWithRouter(helper, A, null);
        router.setFaceNetwork(ResourceType.ITEM, Direction.UP, up);
        router.setFaceNetwork(ResourceType.ITEM, Direction.DOWN, down);
        router.setFaceNetwork(ResourceType.ENERGY, Direction.UP, up);
        NodeKey key = key(router);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad"))
                .thenExecute(() -> {
                    NodeIndex.Entry entry = index(helper).entry(key);
                    helper.assertTrue(entry != null, "roteador fora do índice");
                    helper.assertTrue(entry.networks(ResourceType.ITEM).containsAll(List.of(up, down))
                            && entry.networks(ResourceType.ITEM).size() == 2,
                            "redes de itens: " + entry.networks(ResourceType.ITEM));
                    eq(helper, entry.distinctNetworks(), 2, "redes distintas");
                    helper.assertTrue(entry.inNetwork(down) && entry.hasNetwork(), "inNetwork/hasNetwork");

                    CompoundTag saved = index(helper).save(new CompoundTag(), helper.getLevel().registryAccess());
                    CompoundTag node = null;
                    ListTag nodes = saved.getList("nodes", Tag.TAG_COMPOUND);
                    for (int i = 0; i < nodes.size(); i++) {
                        CompoundTag tag = nodes.getCompound(i);
                        if (tag.getLong("pos") == key.pos().asLong()
                                && tag.getString("dim").equals(key.dimension().location().toString())) {
                            node = tag;
                        }
                    }
                    helper.assertTrue(node != null, "nó fora do arquivo");
                    helper.assertTrue(node.contains("face_networks"), "sem face_networks");
                    helper.assertFalse(node.contains("networks"), "a chave antiga ainda é escrita");
                    helper.assertFalse(node.getCompound("face_networks").contains(ResourceType.FLUID.key()),
                            "tipo sem rede gravado");
                    NodeIndex.Entry loaded = NodeIndex.load(saved, helper.getLevel().registryAccess()).entry(key);
                    helper.assertTrue(loaded != null, "nó perdido ao salvar");
                    eq(helper, loaded.networks(ResourceType.ITEM), entry.networks(ResourceType.ITEM), "itens salvos");
                    eq(helper, loaded.networks(ResourceType.ENERGY), List.of(up), "energia salva");
                    eq(helper, loaded.networks(ResourceType.FLUID), List.of(), "fluidos salvos");

                    // a entrada antiga, com uma rede por tipo em "networks", vira uma lista de um
                    CompoundTag legacy = node.copy();
                    legacy.remove("face_networks");
                    CompoundTag old = new CompoundTag();
                    old.putUUID(ResourceType.FLUID.key(), down);
                    legacy.put("networks", old);
                    ListTag legacyNodes = new ListTag();
                    legacyNodes.add(legacy);
                    CompoundTag legacyFile = new CompoundTag();
                    legacyFile.put("nodes", legacyNodes);
                    NodeIndex.Entry migrated = NodeIndex.load(legacyFile, helper.getLevel().registryAccess()).entry(key);
                    helper.assertTrue(migrated != null, "entrada antiga perdida");
                    eq(helper, migrated.networks(ResourceType.FLUID), List.of(down), "rede antiga de fluidos");
                    eq(helper, migrated.networks(ResourceType.ITEM), List.of(), "itens na entrada antiga");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void moveSetsAllFacesOfTheTab(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        NetworkSavedData data = data(helper);
        WaNetwork first = data.create(player.getUUID(), "teste-mover-faces-1");
        WaNetwork second = data.create(player.getUUID(), "teste-mover-faces-2");
        WaNetwork target = data.create(player.getUUID(), "teste-mover-faces-alvo");
        RouterBlockEntity router = chestWithRouter(helper, A, first.id());
        BlockPos furnace = new BlockPos(2, 1, 0);
        helper.setBlock(furnace, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
        router.setFaceNetwork(ResourceType.ITEM, Direction.DOWN, second.id());
        TabletMenu menu = openTablet(player, 91);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(router.linkMachine(helper.absolutePos(furnace)), "não vinculou a fornalha");
                        int index = router.remoteMachines().index(helper.absolutePos(furnace));
                        EditTarget group = EditTarget.group(router.remoteMachines().machine(index).group());
                        EditTarget machine = EditTarget.machine(index);
                        router.setFaceNetwork(group, ResourceType.ITEM, Direction.NORTH, second.id());
                        router.configureFace(machine, ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0,
                                RedstoneMode.IGNORE);
                        helper.assertTrue(router.remoteMachines().machine(index).hasException(), "sem exceção");

                        eq(helper, TabletPayloads.handleMoveNodes(player, new TabletMoveNodesPayload(91,
                                List.of(key(router)), Optional.of(ResourceType.ITEM), Optional.of(target.id()))), 1,
                                "nós movidos");
                        for (Direction face : Direction.values()) {
                            eq(helper, router.faceNetwork(ResourceType.ITEM, face), target.id(), "presa " + face);
                            eq(helper, router.faceNetwork(group, ResourceType.ITEM, face), target.id(), "perfil " + face);
                            eq(helper, router.faceNetwork(machine, ResourceType.ITEM, face), target.id(),
                                    "exceção " + face);
                            // as outras abas não mudam
                            eq(helper, router.faceNetwork(ResourceType.FLUID, face), first.id(), "fluidos da presa " + face);
                            eq(helper, router.faceNetwork(group, ResourceType.FLUID, face), null,
                                    "fluidos do perfil " + face);
                        }
                        NodeIndex.Entry entry = index(helper).entry(key(router));
                        eq(helper, entry.networks(ResourceType.ITEM), List.of(target.id()), "índice depois de mover");
                        eq(helper, entry.networks(ResourceType.ENERGY), List.of(first.id()), "energia no índice");
                        helper.succeed();
                    } finally {
                        close(player);
                    }
                });
    }

    @GameTest(template = "empty")
    public static void unloadedTypesStayOutOfTheTablet(GameTestHelper helper) {
        if (io.github.matheusanbs.wirelessautomate.network.LoadedTypes.contains(ResourceType.CHEMICAL)) {
            helper.succeed(); // com o Mekanism, Químicos é um tipo carregado como os outros
            return;
        }
        ServerPlayer player = playerWithTablet(helper, A);
        NetworkSavedData data = data(helper);
        WaNetwork items = data.create(player.getUUID(), "teste-descarregado-itens");
        WaNetwork chemicals = data.create(player.getUUID(), "teste-descarregado-quimicos");
        RouterBlockEntity router = chestWithRouter(helper, A, null);
        router.setFaceNetwork(ResourceType.ITEM, Direction.UP, items.id());
        router.setFaceNetwork(ResourceType.CHEMICAL, Direction.UP, chemicals.id());
        NodeKey key = key(router);
        TabletMenu menu = openTablet(player, 93);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        NodeView view = node(fresh(menu), key);
                        helper.assertTrue(view != null, "nó fora da lista");
                        eq(helper, view.distinctNetworks(), 1, "redes distintas contaram Químicos");
                        helper.assertTrue(view.networks(ResourceType.CHEMICAL).isEmpty(), "Químicos no NodeView");
                        helper.assertFalse(listed(search(menu, "descarregado-quimicos"), key),
                                "busca achou pela rede de Químicos");

                        // sem aba, para "Sem rede": a face de Químicos fica como está
                        eq(helper, TabletPayloads.handleMoveNodes(player, new TabletMoveNodesPayload(93, List.of(key),
                                Optional.empty(), Optional.empty())), 1, "nós movidos");
                        eq(helper, router.faceNetwork(ResourceType.ITEM, Direction.UP), null, "itens ainda na rede");
                        eq(helper, router.faceNetwork(ResourceType.CHEMICAL, Direction.UP), chemicals.id(),
                                "a face de Químicos perdeu a rede");
                        helper.succeed();
                    } finally {
                        close(player);
                    }
                });
    }

    @GameTest(template = "empty")
    public static void removedNetworkFlag(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        NetworkSavedData data = data(helper);
        WaNetwork kept = data.create(player.getUUID(), "teste-removida-fica");
        WaNetwork gone = data.create(player.getUUID(), "teste-removida-sai");
        RouterBlockEntity router = chestWithRouter(helper, A, null);
        router.setFaceNetwork(ResourceType.ITEM, Direction.UP, kept.id());
        router.setFaceNetwork(ResourceType.ITEM, Direction.DOWN, gone.id());
        NodeKey key = key(router);
        TabletMenu menu = openTablet(player, 92);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad"))
                .thenExecute(() -> {
                    try {
                        NodeView before = node(fresh(menu), key);
                        helper.assertTrue(before != null && !before.removedNetwork(), "aviso antes de remover");
                        helper.assertTrue(data.remove(gone.id()), "não removeu a rede");
                        NodeView after = node(fresh(menu), key);
                        helper.assertTrue(after != null, "nó sumiu da lista");
                        helper.assertTrue(after.removedNetwork(), "sem o aviso de rede removida");
                        eq(helper, after.networks(ResourceType.ITEM), List.of(kept.id()), "redes depois de remover");
                        // a face guarda o UUID: nada varre os roteadores
                        eq(helper, router.faceNetwork(ResourceType.ITEM, Direction.DOWN), gone.id(), "a face mudou");
                        helper.succeed();
                    } finally {
                        close(player);
                    }
                });
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
                    ((ChestBlockEntity) helper.getBlockEntity(A)).setItem(0, new ItemStack(Items.DIAMOND, 10));
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
        ChestBlockEntity chest = helper.getBlockEntity(pos);
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
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        WaNetwork network = data(helper).create(player.getUUID(), "teste-abrir");
        RouterBlockEntity router = chestWithRouter(helper, A, network.id());
        Vec3 routerCenter = Vec3.atCenterOf(router.getBlockPos());
        try {
            // 20 blocos: longe para a tela comum (8), dentro do alcance do Básico (64)
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
