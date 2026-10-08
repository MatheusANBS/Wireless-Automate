package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.command.WaCommand;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.RoleFilter;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NetworkStats;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.NodeProbe;
import io.github.matheusanbs.wirelessautomate.network.RateLimiter;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.TickBudget;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TabletHeaderPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPagePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Menu do Tablet de rede (sem slots). No cliente guarda o último {@link TabletSnapshot}; no
 * servidor monta o snapshot e aplica as ações da tela, validando tudo.
 *
 * <p>Sincronização (servidor): como no {@link RouterMenu}, só o menu aberto de cada jogador recebe
 * {@link #broadcastChanges()}, então nada roda com a tela fechada. O snapshot é remontado a cada
 * {@link #SAMPLE_TICKS} ticks (amostra de vazão e de estado dos nós), depois de uma ação ou
 * consulta da tela (no máximo {@link #FORCED_PER_SECOND} por segundo por jogador, o resto espera) e,
 * no máximo a cada {@link #REBUILD_TICKS} ticks, quando um dado salvo do índice de nós ou as redes
 * mudaram (chunks que carregam ou descarregam só aparecem na amostra seguinte). Uma montagem
 * percorre o índice uma vez (O(nós visíveis)), mas só sonda o motor para os nós da página. O
 * cabeçalho (estatísticas, redes, grupos) e a página de nós vão em pacotes separados, cada um só se
 * ficou diferente do último enviado.
 *
 * <p>Quem vê o quê: as redes do jogador e as públicas (todas para operador nível 2); os grupos do
 * jogador (todos para operador); os nós que o jogador colocou ou que têm alguma aba numa rede dele
 * (todos para operador).
 */
public class TabletMenu extends AbstractContainerMenu {
    /** Janela da amostra de vazão e estado, em ticks. */
    public static final int SAMPLE_TICKS = 20;
    /** Intervalo mínimo entre remontagens provocadas por mudanças no mundo. */
    public static final int REBUILD_TICKS = 5;
    /**
     * Remontagens pedidas pela tela (consulta, ação) por segundo e por jogador; guarda até um
     * segundo delas. Além disso, o pedido espera e é atendido assim que houver ficha ou na amostra.
     */
    public static final int FORCED_PER_SECOND = 10;
    private static final Pattern SEARCH_SPLIT = Pattern.compile("[\\s,]+");
    /** Fichas de remontagem forçada por jogador; sobrevivem a fechar e abrir o Tablet. Só no servidor. */
    private static final Map<UUID, RateLimiter> FORCED_LIMITS = new HashMap<>();
    /** Teto de nós movidos por pacote. */
    public static final int MAX_MOVE = 1024;
    private static final ResourceType[] TRANSFER_TYPES = ResourceType.values();

    /** Ações da tela sobre redes e grupos ({@code TabletActionPayload}). */
    public enum Action {
        /** Texto: nome. */
        NETWORK_CREATE,
        /** Alvo: rede; texto: nome novo. */
        NETWORK_RENAME,
        /** Alvo: rede; valor: cor RGB. */
        NETWORK_COLOR,
        /** Alvo: rede; valor: 1 pública, 0 privada. */
        NETWORK_PUBLIC,
        NETWORK_REMOVE,
        /** Alvo: rede, que vira a rede ativa do jogador (Vinculador e roteadores colocados). */
        NETWORK_USE,
        /** Texto: nome. */
        GROUP_CREATE,
        GROUP_RENAME,
        GROUP_REMOVE,
        /** Alvo: grupo; outro: rede. */
        GROUP_ADD_NETWORK,
        GROUP_REMOVE_NETWORK,
        /** Alvo: grupo; valor: 1 pausa, 0 retoma. */
        GROUP_PAUSE
    }

    private TabletSnapshot snapshot;
    private int version;

    // Só no servidor.
    private final @Nullable ServerPlayer viewer;
    private Query query = Query.DEFAULT;
    private String[] searchWords = new String[0];
    private boolean forced;
    private @Nullable TabletSnapshot sent;
    /** Partes do último snapshot novo de {@link #pollSnapshot()} que mudaram, para o envio. */
    private boolean headerChanged;
    private boolean pageChanged;
    private long sampleTick;
    private long buildTick;
    /** {@link NodeIndex#dataVersion()} e versão das redes lidas na última montagem. */
    private int builtIndexVersion;
    private int builtDataVersion;
    /** Nós cheios na última sondagem de todos (só com o filtro de problemas) e quando ela foi. */
    private final Set<NodeKey> fullNodes = new HashSet<>();
    private long fullTick = Long.MIN_VALUE;
    private @Nullable Map<UUID, String> searchNames;
    private int searchNamesVersion;
    /** Totais movidos por nó na última amostra, por {@link ResourceType#ordinal()}. */
    private final Map<NodeKey, long[]> lastMoved = new HashMap<>();
    /** Nós que moveram algo na última amostra. */
    private final Set<NodeKey> activeNodes = new HashSet<>();
    /** Vazão por rede na última amostra: itens/s, mB/s e FE/t. */
    private final Map<UUID, long[]> networkRates = new HashMap<>();
    private Component notice = Component.empty();
    private int noticeId;

    /** Servidor. */
    public TabletMenu(int containerId, Inventory inventory, ServerPlayer viewer) {
        super(ModMenus.NETWORK_TABLET.get(), containerId);
        this.viewer = viewer;
        this.sampleTick = viewer.server.getTickCount();
        this.snapshot = build(true);
        this.sent = snapshot;
    }

    /** Cliente: o snapshot inicial vem no buffer de abertura. */
    public TabletMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, TabletSnapshot.STREAM_CODEC.decode(buf));
    }

    /** Cliente (e a captura de tela de desenvolvimento): só o snapshot. */
    public TabletMenu(int containerId, Inventory inventory, TabletSnapshot snapshot) {
        super(ModMenus.NETWORK_TABLET.get(), containerId);
        this.viewer = null;
        this.snapshot = snapshot;
    }

    /** Abre o Tablet para o jogador; o snapshot inicial vai no buffer de abertura. */
    public static void open(ServerPlayer player) {
        TabletMenu[] created = new TabletMenu[1];
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            created[0] = new TabletMenu(containerId, inventory, player);
            return created[0];
        }, Component.translatable("item.wirelessautomate.network_tablet")),
                buf -> TabletSnapshot.STREAM_CODEC.encode(buf, created[0].snapshot));
    }

    /** O jogador tem um Tablet no inventário (mochila, barra, armadura ou mão secundária). */
    public static boolean hasTablet(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.NETWORK_TABLET.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * O roteador está ao alcance do Tablet: o alcance do próprio roteador pela config do tier
     * ({@code tiers.<tier>.range}; 0 = a dimensão inteira), medido do jogador; outra dimensão só se o
     * tier tem {@code crossDimension}. É a mesma regra que o motor usa para as rotas.
     */
    public static boolean inReach(ServerPlayer player, RouterBlockEntity router) {
        Config.TierValues tier = Config.TIERS.get(router.tier());
        if (router.getLevel() != player.level()) {
            return tier.crossDimension().get();
        }
        int range = tier.range().get();
        return range <= 0 || player.position().distanceToSqr(Vec3.atCenterOf(router.getBlockPos()))
                <= (double) range * range;
    }

    public TabletSnapshot snapshot() {
        return snapshot;
    }

    /** Muda a cada snapshot recebido. */
    public int version() {
        return version;
    }

    public void applySnapshot(TabletSnapshot snapshot) {
        this.snapshot = snapshot;
        version++;
    }

    /** Cliente: cabeçalho novo ({@code TabletHeaderPayload}); a página que já estava fica. */
    public void applyHeader(TabletSnapshot header) {
        applySnapshot(header.withPage(snapshot.page()));
    }

    /** Cliente: página nova ({@code TabletPagePayload}). */
    public void applyPage(TabletSnapshot.Page page) {
        applySnapshot(snapshot.withPage(page));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** No servidor: o jogador ainda tem um Tablet. */
    @Override
    public boolean stillValid(Player player) {
        return viewer == null || hasTablet(player);
    }

    // ------------------------------------------------------------------ sincronização

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        TabletSnapshot changed = pollSnapshot();
        if (changed == null || viewer == null || viewer.connection == null) {
            return;
        }
        // O cabeçalho antes da página: os nós apontam para as redes dele.
        if (headerChanged) {
            send(new TabletHeaderPayload(containerId, changed));
        }
        if (pageChanged) {
            send(new TabletPagePayload(containerId, changed.page()));
        }
    }

    private void send(CustomPacketPayload payload) {
        // Jogadores falsos (GameTests) não negociam os canais do mod.
        if (viewer != null && viewer.connection != null && viewer.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    /**
     * Snapshot novo, se é hora de remontar e ele mudou desde o último enviado, ou {@code null}. Marca
     * o novo como enviado e guarda quais partes mudaram (cabeçalho, página). Só no servidor; público
     * para os GameTests.
     */
    public @Nullable TabletSnapshot pollSnapshot() {
        if (viewer == null) {
            return null;
        }
        MinecraftServer server = viewer.server;
        long now = server.getTickCount();
        boolean sample = now - sampleTick >= SAMPLE_TICKS;
        // Só dados salvos do índice: um chunk que carrega ou descarrega espera a próxima amostra.
        boolean worldChanged = NodeIndex.get(server).dataVersion() != builtIndexVersion
                || NetworkSavedData.get(server).version() != builtDataVersion;
        boolean rebuild = sample || (worldChanged && now - buildTick >= REBUILD_TICKS);
        if (!rebuild && forced) {
            // Pedido da tela: no máximo FORCED_PER_SECOND por segundo por jogador; o resto espera.
            rebuild = takeForced(viewer.getUUID(), now);
        }
        if (!rebuild) {
            return null;
        }
        forced = false;
        TabletSnapshot built = build(sample);
        snapshot = built;
        headerChanged = sent == null || !built.sameHeader(sent);
        pageChanged = sent == null || !built.page().equals(sent.page());
        if (!headerChanged && !pageChanged) {
            return null;
        }
        sent = built;
        return built;
    }

    /** Gasta uma remontagem forçada do jogador, se houver. */
    private static boolean takeForced(UUID player, long now) {
        RateLimiter limiter = FORCED_LIMITS.computeIfAbsent(player, id -> new RateLimiter(FORCED_PER_SECOND));
        if (limiter.available(now) < 1) {
            return false;
        }
        limiter.consume(1);
        return true;
    }

    /**
     * O que a tela mostra agora; com {@code sample}, mede vazão e atividade desde a amostra anterior.
     *
     * <p>Percorre o índice uma vez para contar, filtrar e medir a vazão (esta só na amostra), mas só
     * sonda o motor ({@link NodeProbe}) para os nós da página. Os cheios de cada rede vêm das
     * estatísticas do gerenciador; com o filtro de problemas, os nós cheios vêm de uma sondagem de
     * todos que vale por uma amostra.
     */
    private TabletSnapshot build(boolean sample) {
        MinecraftServer server = viewer.server;
        long now = server.getTickCount();
        buildTick = now;
        NetworkSavedData data = NetworkSavedData.get(server);
        NodeIndex index = NodeIndex.get(server);
        builtIndexVersion = index.dataVersion();
        builtDataVersion = data.version();
        boolean operator = viewer.hasPermissions(2);
        UUID me = viewer.getUUID();
        long elapsed = Math.max(1, now - sampleTick);
        if (sample) {
            sampleTick = now;
            activeNodes.clear();
        }
        boolean problems = query.role() == RoleFilter.PROBLEM;
        boolean probeAll = problems && (sample || fullTick == Long.MIN_VALUE || now - fullTick >= SAMPLE_TICKS);
        if (probeAll) {
            fullNodes.clear();
            fullTick = now;
        }

        Map<UUID, Totals> totals = new LinkedHashMap<>();
        for (WaNetwork network : data.networks()) {
            if (operator || network.owner().equals(me) || network.isPublic()) {
                totals.put(network.id(), new Totals());
            }
        }

        BlockPos center = viewer.blockPosition();
        ResourceKey<Level> here = viewer.level().dimension();
        Map<UUID, String> networkNames = searchWords.length == 0 ? Map.of() : searchNames(data);
        Map<ResourceKey<Level>, String> dimensionNames = new HashMap<>();
        List<Match> matches = new ArrayList<>();
        Set<NodeKey> seen = sample ? new HashSet<>() : null;
        int total = 0;
        for (NodeIndex.Entry entry : index.entries()) {
            if (!visible(entry, data, operator, me)) {
                continue;
            }
            total++;
            NodeKey key = entry.key();
            RouterBlockEntity router = entry.router();
            boolean loaded = router != null;
            boolean moved = false;
            if (loaded) {
                if (sample) {
                    seen.add(key);
                    moved = sampleMoved(entry, router, totals, elapsed);
                    if (moved) {
                        activeNodes.add(key);
                    }
                } else {
                    moved = activeNodes.contains(key);
                }
                if (probeAll && fullDestinations(router, now) > 0) {
                    fullNodes.add(key);
                }
            }
            countMembership(entry, totals, loaded);
            if (!matchesRole(entry, loaded, moved, problems, data) || !matchesType(entry, query.type())
                    || !matchesSearch(entry, networkNames)) {
                continue;
            }
            matches.add(new Match(entry, loaded, moved, distance(key, here, center),
                    dimensionNames.computeIfAbsent(key.dimension(), k -> k.location().toString())));
        }
        if (sample) {
            lastMoved.keySet().retainAll(seen);
            networkRates.clear();
            totals.forEach((id, t) -> networkRates.put(id, t.rates(elapsed)));
        }

        matches.sort(Comparator.comparingLong(Match::distance)
                .thenComparing(Match::dimension)
                .thenComparing(m -> m.entry.name()));
        int pages = Math.max(1, (matches.size() + TabletSnapshot.PAGE_SIZE - 1) / TabletSnapshot.PAGE_SIZE);
        if (query.page() >= pages) {
            query = new Query(query.search(), query.role(), query.type(), pages - 1);
        }
        int from = query.page() * TabletSnapshot.PAGE_SIZE;
        List<NodeView> nodes = new ArrayList<>();
        for (int i = from; i < Math.min(matches.size(), from + TabletSnapshot.PAGE_SIZE); i++) {
            Match match = matches.get(i);
            NodeIndex.Entry entry = match.entry;
            // Só os nós da página são sondados agora.
            RouterBlockEntity router = entry.router();
            int full = router == null ? 0 : fullDestinations(router, now);
            List<Optional<UUID>> networks = new ArrayList<>();
            for (ResourceType type : ResourceType.values()) {
                UUID id = entry.network(type);
                networks.add(id != null && data.network(id) != null ? Optional.of(id) : Optional.empty());
            }
            nodes.add(new NodeView(entry.key(), entry.name(), entry.machine(), entry.tier(), List.copyOf(networks),
                    entry.roles(), status(entry, match.loaded, full, match.moved, data)));
        }

        Map<UUID, NetworkStats> stats = new HashMap<>();
        for (NetworkStats s : NetworkManager.get().stats(server)) {
            stats.put(s.id(), s);
        }
        Map<UUID, String> names = new HashMap<>();
        List<NetworkView> networks = new ArrayList<>();
        totals.forEach((id, t) -> {
            WaNetwork network = data.network(id);
            NetworkStats s = stats.get(id);
            long[] rates = networkRates.getOrDefault(id, new long[ResourceType.values().length]);
            List<TabletSnapshot.TypeStats> types = new ArrayList<>(rates.length);
            for (ResourceType type : ResourceType.values()) {
                NetworkStats.TypeCount count = s == null ? null : s.byType().get(type.ordinal());
                types.add(count == null ? new TabletSnapshot.TypeStats(rates[type.ordinal()], 0, 0, 0)
                        : new TabletSnapshot.TypeStats(rates[type.ordinal()], count.sources(), count.destinations(),
                                count.sleeping()));
            }
            networks.add(new NetworkView(id, network.name(), network.color(), ownerName(server, network.owner(), names),
                    network.owner().equals(me), network.canManage(viewer), network.isPublic(), data.isPaused(id),
                    t.nodes, t.unloaded, s == null ? 0 : s.destinationsFull(), s == null ? 0 : s.destinationsSleeping(),
                    s == null ? 0 : Math.round(s.averageNanos()), s == null ? 0 : s.opsLastSecond(),
                    List.copyOf(types)));
        });
        List<GroupView> groups = new ArrayList<>();
        for (WaGroup group : data.groups()) {
            if (operator || group.owner().equals(me)) {
                groups.add(new GroupView(group.id(), group.name(), ownerName(server, group.owner(), names),
                        group.canManage(viewer), group.paused(), group.networks()));
            }
        }

        TickBudget budget = NetworkManager.get().budget();
        return new TabletSnapshot(operator, here.location(), center,
                Optional.ofNullable(data.activeNetwork(me)).filter(id -> data.network(id) != null),
                Math.round(budget.averageUsedNanos()), budget.limitNanos(), query, total, matches.size(),
                List.copyOf(networks), List.copyOf(groups), List.copyOf(nodes), notice, noticeId);
    }

    /** Destinos cheios do nó, nos tipos que o Tablet mostra. */
    private static int fullDestinations(RouterBlockEntity router, long now) {
        int full = 0;
        for (ResourceType type : TRANSFER_TYPES) {
            full += NodeProbe.fullDestinations(router, type, now);
        }
        return full;
    }

    /** O jogador vê o nó: operador, quem o colocou, ou alguma aba numa rede dele. */
    private static boolean visible(NodeIndex.Entry entry, NetworkSavedData data, boolean operator, UUID me) {
        if (operator || me.equals(entry.placedBy())) {
            return true;
        }
        for (ResourceType type : ResourceType.values()) {
            UUID id = entry.network(type);
            WaNetwork network = id == null ? null : data.network(id);
            if (network != null && network.owner().equals(me)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Totals totalsOf(Map<UUID, Totals> totals, @Nullable UUID network) {
        return network == null ? null : totals.get(network);
    }

    /** Soma a vazão do nó às redes de cada aba; devolve se ele moveu algo desde a amostra anterior. */
    private boolean sampleMoved(NodeIndex.Entry entry, RouterBlockEntity router, Map<UUID, Totals> totals, long elapsed) {
        long[] last = lastMoved.get(entry.key());
        if (last == null) {
            last = new long[ResourceType.values().length];
            for (ResourceType type : ResourceType.values()) {
                last[type.ordinal()] = router.moved(type);
            }
            lastMoved.put(entry.key(), last);
            return false;
        }
        boolean moved = false;
        for (int t = 0; t < TRANSFER_TYPES.length; t++) {
            ResourceType type = TRANSFER_TYPES[t];
            long now = router.moved(type);
            long delta = Math.max(0, now - last[type.ordinal()]);
            if (delta > 0) {
                moved = true;
                Totals network = totalsOf(totals, entry.network(type));
                if (network != null) {
                    network.moved[t] += delta;
                }
            }
        }
        for (ResourceType type : ResourceType.values()) {
            last[type.ordinal()] = router.moved(type);
        }
        return moved;
    }

    /** Conta o nó uma vez em cada rede em que alguma aba dele está. */
    private static void countMembership(NodeIndex.Entry entry, Map<UUID, Totals> totals, boolean loaded) {
        ResourceType[] types = ResourceType.values();
        for (int i = 0; i < types.length; i++) {
            UUID id = entry.network(types[i]);
            if (id == null) {
                continue;
            }
            boolean repeated = false;
            for (int j = 0; j < i; j++) {
                if (id.equals(entry.network(types[j]))) {
                    repeated = true;
                }
            }
            Totals network = totals.get(id);
            if (!repeated && network != null) {
                network.nodes++;
                if (!loaded) {
                    network.unloaded++;
                }
            }
        }
    }

    static NodeStatus status(NodeIndex.Entry entry, boolean loaded, int full, boolean moved, NetworkSavedData data) {
        if (!loaded) {
            return NodeStatus.UNLOADED;
        }
        boolean anyNetwork = false;
        boolean anyRunning = false;
        for (ResourceType type : ResourceType.values()) {
            UUID id = entry.network(type);
            if (id != null && data.network(id) != null) {
                anyNetwork = true;
                anyRunning |= !data.isPaused(id);
            }
        }
        if (!anyNetwork) {
            return NodeStatus.NO_NETWORK;
        }
        if (!anyRunning) {
            return NodeStatus.PAUSED;
        }
        if (full > 0) {
            return NodeStatus.FULL;
        }
        return moved ? NodeStatus.ACTIVE : NodeStatus.IDLE;
    }

    /** Papel da consulta. Problemas usa o estado do nó, com os cheios da última sondagem de todos. */
    private boolean matchesRole(NodeIndex.Entry entry, boolean loaded, boolean moved, boolean problems,
            NetworkSavedData data) {
        return switch (query.role()) {
            case ALL -> true;
            case EXTRACT -> hasRole(entry, NodeIndex.EXTRACT);
            case INSERT -> hasRole(entry, NodeIndex.INSERT);
            case STORAGE -> hasRole(entry, NodeIndex.STORAGE);
            case PROBLEM -> {
                NodeStatus status = status(entry, loaded, fullNodes.contains(entry.key()) ? 1 : 0, moved, data);
                yield status == NodeStatus.UNLOADED || status == NodeStatus.NO_NETWORK || status == NodeStatus.FULL;
            }
        };
    }

    /** Sem tipo escolhido passa tudo; com tipo, só nós com algum papel (extrai, insere, armazém) nele. */
    private static boolean matchesType(NodeIndex.Entry entry, Optional<ResourceType> type) {
        if (type.isEmpty()) {
            return true;
        }
        int mask = NodeIndex.role(type.get(), NodeIndex.EXTRACT) | NodeIndex.role(type.get(), NodeIndex.INSERT)
                | NodeIndex.role(type.get(), NodeIndex.STORAGE);
        return (entry.roles() & mask) != 0;
    }

    /**
     * Cada palavra da busca precisa aparecer no nome, na máquina, nas coordenadas
     * ({@link NodeIndex.Entry#searchText()}) ou no nome de uma rede do nó.
     */
    private boolean matchesSearch(NodeIndex.Entry entry, Map<UUID, String> networkNames) {
        if (searchWords.length == 0) {
            return true;
        }
        String text = entry.searchText();
        for (String word : searchWords) {
            if (!text.contains(word) && !inNetworkName(entry, word, networkNames)) {
                return false;
            }
        }
        return true;
    }

    private static boolean inNetworkName(NodeIndex.Entry entry, String word, Map<UUID, String> networkNames) {
        for (ResourceType type : ResourceType.values()) {
            UUID id = entry.network(type);
            String name = id == null ? null : networkNames.get(id);
            if (name != null && name.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Nomes das redes em minúsculas, refeitos só quando as redes mudam. */
    private Map<UUID, String> searchNames(NetworkSavedData data) {
        if (searchNames == null || searchNamesVersion != data.version()) {
            Map<UUID, String> names = new HashMap<>();
            for (WaNetwork network : data.networks()) {
                names.put(network.id(), network.name().toLowerCase(Locale.ROOT));
            }
            searchNames = names;
            searchNamesVersion = data.version();
        }
        return searchNames;
    }

    /** Palavras da busca: em minúsculas, separadas por espaços ou vírgulas, sem vazias. */
    static String[] searchWords(String search) {
        return Arrays.stream(SEARCH_SPLIT.split(search.strip().toLowerCase(Locale.ROOT)))
                .filter(word -> !word.isEmpty())
                .toArray(String[]::new);
    }

    private static boolean hasRole(NodeIndex.Entry entry, int role) {
        for (ResourceType type : ResourceType.values()) {
            if ((entry.roles() & NodeIndex.role(type, role)) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Distância ao quadrado no mesmo mundo; outra dimensão vai para o fim. */
    private static long distance(NodeKey key, ResourceKey<Level> here, BlockPos center) {
        if (!key.dimension().equals(here)) {
            return Long.MAX_VALUE;
        }
        return (long) key.pos().distSqr(center);
    }

    private static String ownerName(MinecraftServer server, UUID owner, Map<UUID, String> cache) {
        return cache.computeIfAbsent(owner, id -> {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                return online.getGameProfile().getName();
            }
            if (server.getProfileCache() != null) {
                var profile = server.getProfileCache().get(id);
                if (profile.isPresent()) {
                    return profile.get().getName();
                }
            }
            return id.toString().substring(0, 8);
        });
    }

    /** Um nó que passou na consulta; {@code dimension} é o nome da dimensão, para desempatar. */
    private record Match(NodeIndex.Entry entry, boolean loaded, boolean moved, long distance, String dimension) {
    }

    /** Somas de uma rede numa montagem. */
    private static final class Totals {
        int nodes;
        int unloaded;
        /** Movido desde a amostra anterior, na ordem de {@link #TRANSFER_TYPES}. */
        final long[] moved = new long[TRANSFER_TYPES.length];

        /** Por tipo, na unidade do registro: por segundo, ou por tick para a energia. */
        long[] rates(long elapsed) {
            ResourceType[] types = ResourceType.values();
            long[] rates = new long[types.length];
            for (int t = 0; t < types.length; t++) {
                long perTick = types[t].ratePerTick() ? moved[t] : moved[t] * 20;
                rates[t] = (perTick + elapsed / 2) / elapsed;
            }
            return rates;
        }
    }

    // ------------------------------------------------------------------ ações (servidor)

    private void notice(String key, Object... args) {
        notice = Component.translatable("gui.wirelessautomate.tablet.notice." + key, args);
        noticeId++;
        forced = true;
    }

    /** A tela mudou busca, papel ou página. */
    public void setQuery(Query query) {
        if (!query.search().equals(this.query.search())) {
            searchWords = searchWords(query.search());
        }
        this.query = query;
        forced = true;
    }

    public Query query() {
        return query;
    }

    /**
     * Põe os nós numa rede: só a aba {@code type} ou, vazio, todas; {@code network} vazio tira da
     * rede. Só nós visíveis ao jogador e carregados (a rede é guardada no roteador); os descarregados
     * ficam de fora e são contados no aviso. Devolve quantos mudaram, ou -1 se o pedido foi recusado.
     */
    public int moveNodes(List<NodeKey> keys, Optional<ResourceType> type, Optional<UUID> network) {
        if (viewer == null || keys.isEmpty() || keys.size() > MAX_MOVE
                || type.filter(t -> !LoadedTypes.contains(t)).isPresent()) {
            return -1;
        }
        MinecraftServer server = viewer.server;
        NetworkSavedData data = NetworkSavedData.get(server);
        WaNetwork target = network.map(data::network).orElse(null);
        if (network.isPresent() && (target == null || !target.canUse(viewer))) {
            notice("move.refused");
            return -1;
        }
        NodeIndex index = NodeIndex.get(server);
        boolean operator = viewer.hasPermissions(2);
        int moved = 0;
        int unloaded = 0;
        for (NodeKey key : new HashSet<>(keys)) {
            NodeIndex.Entry entry = index.entry(key);
            if (entry == null || !visible(entry, data, operator, viewer.getUUID())) {
                continue;
            }
            RouterBlockEntity router = loadedRouter(server, index, entry);
            if (router == null) {
                unloaded++;
                continue;
            }
            UUID id = target == null ? null : target.id();
            if (type.isPresent()) {
                router.setNetworkId(type.get(), id);
            } else {
                router.setNetworkId(id);
            }
            moved++;
        }
        Component name = target == null ? Component.translatable("gui.wirelessautomate.tablet.network.none")
                : target.displayName();
        if (unloaded > 0) {
            notice("moved.unloaded", moved, name, unloaded);
        } else {
            notice("moved", moved, name);
        }
        return moved;
    }

    /**
     * Abre a tela do roteador à distância: nó visível, carregado e ao alcance ({@link #inReach}).
     * Devolve se abriu.
     */
    public boolean openNode(NodeKey key) {
        if (viewer == null) {
            return false;
        }
        MinecraftServer server = viewer.server;
        NodeIndex index = NodeIndex.get(server);
        NodeIndex.Entry entry = index.entry(key);
        if (entry == null || !visible(entry, NetworkSavedData.get(server), viewer.hasPermissions(2), viewer.getUUID())) {
            return false;
        }
        RouterBlockEntity router = loadedRouter(server, index, entry);
        if (router == null) {
            notice("open.unloaded");
            return false;
        }
        if (!inReach(viewer, router)) {
            notice("open.range");
            return false;
        }
        RemoteRouterMenu.open(viewer, router);
        return true;
    }

    /**
     * O roteador carregado da entrada. Se o chunk está carregado e não há roteador ali (região
     * apagada, bloco trocado sem {@code onRemove}), a entrada velha sai do índice.
     */
    private static @Nullable RouterBlockEntity loadedRouter(MinecraftServer server, NodeIndex index, NodeIndex.Entry entry) {
        RouterBlockEntity router = entry.router();
        if (router != null) {
            return router;
        }
        ServerLevel level = server.getLevel(entry.key().dimension());
        if (level != null && level.isLoaded(entry.key().pos())) {
            if (level.getBlockEntity(entry.key().pos()) instanceof RouterBlockEntity found) {
                NodeIndex.track(found);
                return found;
            }
            index.remove(entry.key());
        }
        return null;
    }

    /** Uma ação sobre redes ou grupos, validada. Devolve se foi aplicada. */
    public boolean apply(Action action, Optional<UUID> target, Optional<UUID> other, String text, int value) {
        if (viewer == null) {
            return false;
        }
        NetworkSavedData data = NetworkSavedData.get(viewer.server);
        UUID me = viewer.getUUID();
        boolean done = switch (action) {
            case NETWORK_CREATE -> {
                String name = cleanName(text);
                if (name == null || data.byName(me, name) != null) {
                    yield refuse("name");
                }
                data.create(me, name);
                notice("network.created", name);
                yield true;
            }
            case NETWORK_RENAME -> {
                WaNetwork network = managedNetwork(data, target);
                String name = cleanName(text);
                if (network == null || name == null) {
                    yield refuse(network == null ? "permission" : "name");
                }
                WaNetwork same = data.byName(network.owner(), name);
                if (same != null && !same.id().equals(network.id())) {
                    yield refuse("name");
                }
                yield data.update(network.withName(name));
            }
            case NETWORK_COLOR -> {
                WaNetwork network = managedNetwork(data, target);
                yield network == null ? refuse("permission") : data.update(network.withColor(value));
            }
            case NETWORK_PUBLIC -> {
                WaNetwork network = managedNetwork(data, target);
                if (network == null || (value != 0 && value != 1)) {
                    yield refuse("permission");
                }
                yield data.update(network.withPublic(value == 1));
            }
            case NETWORK_REMOVE -> {
                WaNetwork network = managedNetwork(data, target);
                if (network == null) {
                    yield refuse("permission");
                }
                data.remove(network.id());
                notice("network.removed", network.name());
                yield true;
            }
            case NETWORK_USE -> {
                WaNetwork network = target.map(data::network).orElse(null);
                if (network == null || !network.canUse(viewer)) {
                    yield refuse("permission");
                }
                data.setActiveNetwork(me, network.id());
                notice("network.active", network.displayName());
                yield true;
            }
            case GROUP_CREATE -> {
                String name = cleanName(text);
                if (name == null) {
                    yield refuse("name");
                }
                data.createGroup(me, name);
                notice("group.created", name);
                yield true;
            }
            case GROUP_RENAME -> {
                WaGroup group = managedGroup(data, target);
                String name = cleanName(text);
                if (group == null || name == null) {
                    yield refuse(group == null ? "permission" : "name");
                }
                yield data.updateGroup(group.withName(name));
            }
            case GROUP_REMOVE -> {
                WaGroup group = managedGroup(data, target);
                if (group == null) {
                    yield refuse("permission");
                }
                data.removeGroup(group.id());
                notice("group.removed", group.name());
                yield true;
            }
            case GROUP_ADD_NETWORK, GROUP_REMOVE_NETWORK -> {
                WaGroup group = managedGroup(data, target);
                // Só redes que o jogador gerencia: pausar o grupo não pode parar a rede de outro.
                WaNetwork network = managedNetwork(data, other);
                if (group == null || network == null) {
                    yield refuse("permission");
                }
                yield data.updateGroup(action == Action.GROUP_ADD_NETWORK
                        ? group.withNetwork(network.id()) : group.withoutNetwork(network.id()));
            }
            case GROUP_PAUSE -> {
                WaGroup group = managedGroup(data, target);
                if (group == null || (value != 0 && value != 1)) {
                    yield refuse("permission");
                }
                boolean changed = data.updateGroup(group.withPaused(value == 1));
                notice(value == 1 ? "group.paused" : "group.resumed", group.name());
                yield changed;
            }
        };
        forced = true;
        return done;
    }

    private boolean refuse(String reason) {
        notice("refused." + reason);
        return false;
    }

    private @Nullable WaNetwork managedNetwork(NetworkSavedData data, Optional<UUID> id) {
        WaNetwork network = id.map(data::network).orElse(null);
        return network != null && network.canManage(viewer) ? network : null;
    }

    private @Nullable WaGroup managedGroup(NetworkSavedData data, Optional<UUID> id) {
        WaGroup group = id.map(data::group).orElse(null);
        return group != null && group.canManage(viewer) ? group : null;
    }

    /** Nome sem caracteres inválidos e sem espaços nas pontas, de 1 a {@link WaCommand#MAX_NAME_LENGTH}; senão {@code null}. */
    public static @Nullable String cleanName(String text) {
        String name = StringUtil.filterText(text).strip();
        return name.isEmpty() || name.length() > WaCommand.MAX_NAME_LENGTH ? null : name;
    }
}
