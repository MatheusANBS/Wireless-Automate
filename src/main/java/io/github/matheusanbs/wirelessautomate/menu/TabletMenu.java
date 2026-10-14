package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.network.RateMath;
import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.command.WaCommand;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.ProfileSummary;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Facet;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickCounts;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickFilter;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NetworkStats;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.NodeProbe;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RateLimiter;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.TickBudget;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletHeaderPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPagePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.preset.ConfiguratorArea;
import io.github.matheusanbs.wirelessautomate.preset.PresetApplier;
import io.github.matheusanbs.wirelessautomate.preset.PresetPaste;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSource;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
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
 * percorre o índice uma vez (O(nós visíveis)) e sonda o motor uma vez por amostra para saber quais
 * nós têm destino cheio (as contagens dos filtros rápidos). O cabeçalho (estatísticas, redes, grupos,
 * facetas) e a página de nós vão em pacotes separados, cada um só se ficou diferente do último enviado.
 *
 * <p>Nó escolhido (servidor): o painel do Tablet mostra e edita um nó ({@link #chooseNode}). O menu é o
 * {@link RouterEditHost} desse nó, então os pacotes de face de sempre editam o roteador à distância
 * enquanto ele é visível, está carregado (sem carregar chunk) e ao alcance ({@link #inReach}). O
 * {@link TabletPanelPayload} (o {@link RouterSnapshot} do nó e o acesso) só sai com um nó escolhido: ao
 * escolher, quando a versão do roteador ou das redes muda, ao trocar o alvo, depois de uma edição e a cada
 * {@link #SAMPLE_TICKS} ticks (alcance e vazão), e só se ficou diferente do último enviado.
 *
 * <p>Quem vê o quê: as redes do jogador e as públicas (todas para operador nível 2); os grupos do
 * jogador (todos para operador); os nós que o jogador colocou ou que têm alguma face numa rede dele
 * (todos para operador).
 */
public class TabletMenu extends AbstractContainerMenu implements RouterEditHost {
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
    /**
     * Onde cada jogador deixou o Tablet (ao fechar o menu, inclusive para abrir o filtro de um nó): o Tablet
     * reabre igual. Só na memória do servidor, some quando ele para.
     */
    private static final Map<UUID, State> STATES = new HashMap<>();

    /** O que o Tablet lembra: a aba (posição na ordem das abas da tela), a consulta, o nó escolhido e o alvo de edição dele. */
    public record State(int tab, Query query, @Nullable NodeKey chosen, EditTargetRef target) {
    }
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
        /** Alvo: rede, que vira a rede ativa do jogador (a que o Vinculador usa). */
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
    /** Cliente: o último painel do nó escolhido recebido, ou {@code null}. */
    private @Nullable TabletPanelPayload panel;
    private int panelVersion;
    /** Cliente: a aba e o nó escolhido que o servidor restaurou ao abrir. */
    private int restoredTab;
    private @Nullable NodeKey restoredNode;

    // Só no servidor.
    private final @Nullable ServerPlayer viewer;
    private Query query = Query.DEFAULT;
    private int tab;
    private String[] searchWords = new String[0];
    private Set<ResourceLocation> textMachines = Set.of();
    private Set<ResourceLocation> machineLabels = Set.of();
    private Set<UUID> networkLabels = Set.of();
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
    /** Nós cheios na última sondagem de todos os nós carregados e quando ela foi. */
    private final Set<NodeKey> fullNodes = new HashSet<>();
    private long fullTick = Long.MIN_VALUE;
    private @Nullable Map<UUID, String> searchNames;
    private int searchNamesVersion;
    /** Totais movidos por nó na última amostra, por {@link ResourceType#ordinal()}. */
    private final Map<NodeKey, long[]> lastMoved = new HashMap<>();
    /** Nós que moveram algo na última amostra. */
    private final Set<NodeKey> activeNodes = new HashSet<>();
    /** Vazão por rede na última amostra, por tipo, na unidade do registro. */
    private final Map<UUID, long[]> networkRates = new HashMap<>();
    private Component notice = Component.empty();
    private int noticeId;
    /** O nó do painel e o alvo de edição dele (a presa, um grupo ou uma máquina); trocar de nó volta à presa. */
    private @Nullable NodeKey chosen;
    private EditTargetRef targetRef = EditTargetRef.ATTACHED;
    /** Um pedido, o alvo ou uma edição pede o painel de novo na próxima oportunidade. */
    private boolean panelForced;
    private @Nullable TabletPanelPayload sentPanel;
    private int panelChangeVersion;
    private int panelDataVersion;
    private long panelTick;
    /** O último snapshot lido do nó escolhido, mostrado (só para ler) enquanto ele está descarregado. */
    private @Nullable RouterSnapshot lastKnown;
    /** O último colar do Tablet: o que cada nó era antes; some ao fechar o menu. */
    private final Map<NodeKey, RouterPreset> undo = new LinkedHashMap<>();
    private @Nullable Component lastNotice;

    /** Servidor. */
    public TabletMenu(int containerId, Inventory inventory, ServerPlayer viewer) {
        super(ModMenus.NETWORK_TABLET.get(), containerId);
        this.viewer = viewer;
        this.sampleTick = viewer.server.getTickCount();
        restore(STATES.get(viewer.getUUID()));
        this.snapshot = build(true);
        this.sent = snapshot;
    }

    /** Cliente: o snapshot inicial vem no buffer de abertura. */
    public TabletMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, TabletSnapshot.STREAM_CODEC.decode(buf), buf.readVarInt(),
                NODE_CODEC.decode(buf).orElse(null));
    }

    /** Cliente (e a captura de tela de desenvolvimento): só o snapshot. */
    public TabletMenu(int containerId, Inventory inventory, TabletSnapshot snapshot) {
        this(containerId, inventory, snapshot, 0, null);
    }

    private TabletMenu(int containerId, Inventory inventory, TabletSnapshot snapshot, int restoredTab,
            @Nullable NodeKey restoredNode) {
        super(ModMenus.NETWORK_TABLET.get(), containerId);
        this.viewer = null;
        this.snapshot = snapshot;
        this.restoredTab = restoredTab;
        this.restoredNode = restoredNode;
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<NodeKey>> NODE_CODEC =
            ByteBufCodecs.optional(NodeKey.STREAM_CODEC.<RegistryFriendlyByteBuf>cast());

    /**
     * Põe o estado de antes: a consulta, a aba e, se o nó ainda existe e o jogador o vê, o nó escolhido e o
     * alvo dele. Só no servidor, antes da primeira montagem.
     */
    private void restore(@Nullable State saved) {
        if (saved == null || viewer == null) {
            return;
        }
        setQuery(saved.query());
        tab = saved.tab();
        if (saved.chosen() != null && chooseNode(saved.chosen())) {
            targetRef = saved.target();
        }
    }

    /** Esquece o estado de todos os jogadores (o servidor parou). */
    public static void resetStates() {
        STATES.clear();
        FORCED_LIMITS.clear();
    }

    /** O estado guardado do jogador, ou {@code null}. */
    public static @Nullable State savedState(UUID player) {
        return STATES.get(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (viewer != null) {
            STATES.put(viewer.getUUID(), new State(tab, query, chosen, targetRef));
        }
    }

    /** Quantas abas a tela tem ({@code TabletScreen.Tab}); o servidor só guarda uma posição dentro delas. */
    public static final int TAB_COUNT = 5;

    /** Servidor: a aba que a tela mostra. Devolve se o valor era de uma aba que existe. */
    public boolean setTab(int tab) {
        if (tab < 0 || tab >= TAB_COUNT) {
            return false;
        }
        this.tab = tab;
        return true;
    }

    /** Esquece o estado de um jogador (saiu do servidor). */
    public static void forget(UUID player) {
        STATES.remove(player);
        FORCED_LIMITS.remove(player);
    }

    @Override
    public FilterOrigin filterOrigin() {
        return FilterOrigin.TABLET;
    }

    /** Abre o Tablet para o jogador; o snapshot inicial vai no buffer de abertura. */
    public static void open(ServerPlayer player) {
        TabletMenu[] created = new TabletMenu[1];
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            created[0] = new TabletMenu(containerId, inventory, player);
            return created[0];
        }, Component.translatable("item.wirelessautomate.network_tablet")),
                buf -> created[0].writeOpenData(buf));
    }

    /** O que vai no buffer de abertura: o snapshot, a aba restaurada e o nó escolhido restaurado. */
    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        TabletSnapshot.STREAM_CODEC.encode(buf, snapshot);
        buf.writeVarInt(tab);
        NODE_CODEC.encode(buf, Optional.ofNullable(chosen));
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

    /** Servidor: a aba que a tela mostra. */
    public int tab() {
        return tab;
    }

    /** Cliente: a aba que o servidor restaurou ao abrir. */
    public int restoredTab() {
        return restoredTab;
    }

    /** Cliente: o nó escolhido que o servidor restaurou ao abrir, ou {@code null}. */
    public @Nullable NodeKey restoredNode() {
        return restoredNode;
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

    /** Cliente: o painel novo do nó escolhido ({@code TabletPanelPayload}). */
    public void applyPanel(TabletPanelPayload panel) {
        this.panel = panel;
        panelVersion++;
    }

    /** Cliente: o último painel recebido, ou {@code null} sem nó escolhido. */
    public @Nullable TabletPanelPayload panel() {
        return panel;
    }

    /** Muda a cada painel recebido. */
    public int panelVersion() {
        return panelVersion;
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
        if (changed != null && viewer != null && viewer.connection != null) {
            // O cabeçalho antes da página: os nós apontam para as redes dele.
            if (headerChanged) {
                send(new TabletHeaderPayload(containerId, changed));
            }
            if (pageChanged) {
                send(new TabletPagePayload(containerId, changed.page()));
            }
        }
        TabletPanelPayload panelNow = pollPanel();
        if (panelNow != null) {
            send(panelNow);
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
        boolean probeAll = sample || fullTick == Long.MIN_VALUE || now - fullTick >= SAMPLE_TICKS;
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
        Map<UUID, String> networkNames = searchWords.length == 0 ? Map.of() : searchNames(data);
        Map<ResourceLocation, Integer> machineCounts = new HashMap<>();
        Map<UUID, Integer> networkCounts = new HashMap<>();
        int problemCount = 0;
        int noNetworkCount = 0;
        int unloadedCount = 0;
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
            NodeStatus status = status(entry, loaded, fullNodes.contains(key) ? 1 : 0, moved, data);
            boolean problem = status == NodeStatus.UNLOADED || status == NodeStatus.NO_NETWORK
                    || status == NodeStatus.FULL;
            boolean noNetwork = !hasExistingNetwork(entry, data);
            if (problem) {
                problemCount++;
            }
            if (noNetwork) {
                noNetworkCount++;
            }
            if (!loaded) {
                unloadedCount++;
            }
            machineCounts.merge(entry.machine(), 1, Integer::sum);
            for (UUID id : distinctNetworks(entry)) {
                if (totals.containsKey(id)) {
                    networkCounts.merge(id, 1, Integer::sum);
                }
            }
            if (!matchesQuick(problem, noNetwork, !loaded) || !matchesLabels(entry) || !matchesType(entry, query.type())
                    || !matchesText(entry, networkNames)) {
                continue;
            }
            matches.add(new Match(entry, status));
        }
        if (sample) {
            lastMoved.keySet().retainAll(seen);
            networkRates.clear();
            totals.forEach((id, t) -> networkRates.put(id, t.rates(elapsed)));
        }

        matches.sort(Comparator.<Match, ResourceLocation>comparing(m -> m.entry.machine())
                .thenComparing(m -> m.entry.name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(m -> m.entry.key().dimension().location())
                .thenComparingLong(m -> m.entry.key().pos().asLong()));
        RouterPreset profile = storedProfile(viewer);
        List<NodeView> nodes = new ArrayList<>();
        for (int i = 0; i < Math.min(matches.size(), TabletSnapshot.MAX_NODES); i++) {
            Match match = matches.get(i);
            NodeIndex.Entry entry = match.entry;
            // só as redes que existem, só dos tipos carregados (os outros vão vazios); uma face numa
            // rede apagada vira o aviso (sem varrer nada)
            List<List<UUID>> networks = new ArrayList<>();
            boolean removed = false;
            for (ResourceType type : ResourceType.values()) {
                List<UUID> existing = new ArrayList<>();
                for (UUID id : LoadedTypes.contains(type) ? entry.networks(type) : List.<UUID>of()) {
                    if (data.network(id) != null) {
                        existing.add(id);
                    } else {
                        removed = true;
                    }
                }
                networks.add(List.copyOf(existing));
            }
            RouterBlockEntity loadedNode = entry.router();
            // só nós carregados e com perfil guardado comparam; o descarregado não tem o que ler
            Optional<ProfileDiff.ProfileMatch> comparison = profile == null || loadedNode == null ? Optional.empty()
                    : Optional.of(ProfileDiff.match(profile, RouterPreset.copyOf(loadedNode)));
            nodes.add(new NodeView(entry.key(), entry.name(), entry.machine(), entry.tier(), List.copyOf(networks),
                    entry.roles(), match.status, removed, entry.faceModes(), comparison));
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
        return new TabletSnapshot(operator, viewer.level().dimension().location(), center,
                Optional.ofNullable(data.activeNetwork(me)).filter(id -> data.network(id) != null),
                Math.round(budget.averageUsedNanos()), budget.limitNanos(), query, total, matches.size(),
                List.copyOf(networks), List.copyOf(groups), facets(machineCounts), facets(networkCounts),
                new QuickCounts(problemCount, noNetworkCount, unloadedCount), profileSummary(viewer, profile),
                List.copyOf(nodes), notice, noticeId);
    }

    /** As facetas pela contagem, das maiores para as menores (a chave desempata, para o cabeçalho ser estável). */
    private static <T extends Comparable<T>> List<Facet<T>> facets(Map<T, Integer> counts) {
        List<Facet<T>> facets = new ArrayList<>(counts.size());
        counts.forEach((id, count) -> facets.add(new Facet<>(id, count)));
        facets.sort(Comparator.<Facet<T>>comparingInt(Facet::count).reversed().thenComparing(Facet::id));
        return facets;
    }

    /** Destinos cheios do nó, nos tipos que o Tablet mostra. */
    private static int fullDestinations(RouterBlockEntity router, long now) {
        int full = 0;
        for (ResourceType type : TRANSFER_TYPES) {
            full += NodeProbe.fullDestinations(router, type, now);
        }
        return full;
    }

    /** O jogador vê o nó: operador, quem o colocou, ou alguma face numa rede dele. */
    private static boolean visible(NodeIndex.Entry entry, NetworkSavedData data, boolean operator, UUID me) {
        if (operator || me.equals(entry.placedBy())) {
            return true;
        }
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                WaNetwork network = data.network(id);
                if (network != null && network.owner().equals(me)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Soma a vazão do nó às redes das faces de cada tipo; devolve se ele moveu algo desde a amostra
     * anterior. O roteador só conta o movido por tipo ({@link RouterBlockEntity#moved}), não por rede:
     * com as faces de um tipo em duas redes, o total do tipo vai para cada uma delas (a vazão de uma
     * rede conta o nó inteiro). A vazão exata por rede pediria contar o movido por porta no motor.
     */
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
                for (UUID id : entry.networks(type)) {
                    Totals network = totals.get(id);
                    if (network != null) {
                        network.moved[t] = RateMath.add(network.moved[t], delta);
                    }
                }
            }
        }
        for (ResourceType type : ResourceType.values()) {
            last[type.ordinal()] = router.moved(type);
        }
        return moved;
    }

    /**
     * Conta o nó uma vez em cada rede em que alguma face dele está. Uma rede em vários tipos conta uma
     * vez: o {@code counted} guarda as já contadas deste nó (vazio e sem custo para quem não tem rede).
     */
    private static void countMembership(NodeIndex.Entry entry, Map<UUID, Totals> totals, boolean loaded) {
        Set<Totals> counted = null;
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                Totals network = totals.get(id);
                if (network == null) {
                    continue;
                }
                if (counted == null) {
                    counted = new HashSet<>();
                }
                if (counted.add(network)) {
                    network.nodes++;
                    if (!loaded) {
                        network.unloaded++;
                    }
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
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                if (data.network(id) != null) {
                    anyNetwork = true;
                    anyRunning |= !data.isPaused(id);
                }
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

    /** Os filtros rápidos ligados valem todos (E). */
    private boolean matchesQuick(boolean problem, boolean noNetwork, boolean unloaded) {
        for (QuickFilter filter : query.quick()) {
            boolean on = switch (filter) {
                case PROBLEM -> problem;
                case NO_NETWORK -> noNetwork;
                case UNLOADED -> unloaded;
            };
            if (!on) {
                return false;
            }
        }
        return true;
    }

    /** Etiquetas: a máquina do nó está entre as escolhidas e alguma face dele está numa das redes escolhidas. */
    private boolean matchesLabels(NodeIndex.Entry entry) {
        if (!machineLabels.isEmpty() && !machineLabels.contains(entry.machine())) {
            return false;
        }
        if (networkLabels.isEmpty()) {
            return true;
        }
        for (UUID id : networkLabels) {
            if (entry.inNetwork(id)) {
                return true;
            }
        }
        return false;
    }

    /** Alguma face do nó está numa rede que existe. */
    private static boolean hasExistingNetwork(NodeIndex.Entry entry, NetworkSavedData data) {
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                if (data.network(id) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** As redes das faces do nó nos tipos carregados, sem repetir. */
    private static Set<UUID> distinctNetworks(NodeIndex.Entry entry) {
        Set<UUID> ids = null;
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                if (ids == null) {
                    ids = new HashSet<>();
                }
                ids.add(id);
            }
        }
        return ids == null ? Set.of() : ids;
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
     * Cada palavra do texto precisa aparecer no nome, na máquina, nas coordenadas
     * ({@link NodeIndex.Entry#searchText()}) ou no nome de uma rede do nó; ou o nó é de uma máquina cujo
     * nome o cliente achou no texto ({@code textMachines}).
     */
    private boolean matchesText(NodeIndex.Entry entry, Map<UUID, String> networkNames) {
        if (searchWords.length == 0 || textMachines.contains(entry.machine())) {
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
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                String name = networkNames.get(id);
                if (name != null && name.contains(word)) {
                    return true;
                }
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

    /** Um nó que passou na consulta, com o estado que a lista mostra. */
    private record Match(NodeIndex.Entry entry, NodeStatus status) {
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
                rates[t] = RateMath.rate(moved[t], elapsed, types[t].ratePerTick());
            }
            return rates;
        }
    }

    // ------------------------------------------------------------------ nó escolhido (servidor)

    @Override
    public int containerId() {
        return containerId;
    }

    /** O nó escolhido, se há, é visível e está carregado (sem carregar chunk); senão {@code null}. */
    @Override
    public @Nullable RouterBlockEntity editedRouter() {
        NodeIndex.Entry entry = chosenEntry();
        return entry == null ? null : loadedRouter(viewer.server, NodeIndex.get(viewer.server), entry);
    }

    /** A entrada do índice do nó escolhido, se ainda existe e o jogador a vê. */
    private NodeIndex.@Nullable Entry chosenEntry() {
        if (viewer == null || chosen == null) {
            return null;
        }
        MinecraftServer server = viewer.server;
        NodeIndex.Entry entry = NodeIndex.get(server).entry(chosen);
        return entry != null && visible(entry, NetworkSavedData.get(server), viewer.hasPermissions(2), viewer.getUUID())
                ? entry : null;
    }

    /** Nó escolhido, visível, carregado e ao alcance, e o jogador ainda com o Tablet. */
    @Override
    public boolean canEdit(ServerPlayer player) {
        RouterBlockEntity router = editedRouter();
        return router != null && stillValid(player) && inReach(player, router);
    }

    /**
     * Escolhe o nó do painel ({@code null}: nenhum). Recusa um nó que não está no índice ou que o jogador
     * não vê. O alvo de edição volta para a presa e o painel é enviado de novo. Devolve se escolheu.
     */
    public boolean chooseNode(@Nullable NodeKey key) {
        if (viewer == null) {
            return false;
        }
        if (key != null) {
            MinecraftServer server = viewer.server;
            NodeIndex.Entry entry = NodeIndex.get(server).entry(key);
            if (entry == null || !visible(entry, NetworkSavedData.get(server), viewer.hasPermissions(2), viewer.getUUID())) {
                return false;
            }
        }
        if (!java.util.Objects.equals(key, chosen)) {
            chosen = key;
            targetRef = EditTargetRef.ATTACHED;
            lastKnown = null;
            sentPanel = null;
        }
        panelForced = true;
        return true;
    }

    /** O nó escolhido, ou {@code null}. Só no servidor. */
    public @Nullable NodeKey chosenNode() {
        return chosen;
    }

    @Override
    public EditTargetRef targetRef() {
        return targetRef;
    }

    /** Confere o alvo no roteador: uma máquina que mudou de índice segue pela posição; um alvo que sumiu volta à presa. */
    @Override
    public EditTarget validTarget() {
        RouterBlockEntity router = editedRouter();
        if (router == null) {
            return targetRef.target();
        }
        EditTargetRef fresh = targetRef.refresh(router);
        if (!fresh.equals(targetRef)) {
            targetRef = fresh;
            panelForced = true;
        }
        return targetRef.target();
    }

    @Override
    public @Nullable EditTarget actionTarget() {
        boolean wasRemote = !targetRef.target().attached();
        EditTarget target = validTarget();
        return wasRemote && target.attached() ? null : target;
    }

    @Override
    public boolean selectTarget(EditTarget target, @Nullable BlockPos pos, @Nullable ResourceLocation block) {
        RouterBlockEntity router = editedRouter();
        if (router == null) {
            return false;
        }
        EditTargetRef resolved = EditTargetRef.resolve(router, target, pos, block);
        if (resolved == null) {
            return false;
        }
        validTarget();
        if (!resolved.equals(targetRef)) {
            targetRef = resolved;
            panelForced = true;
        }
        return true;
    }

    /** Uma edição pelos pacotes de face: o painel mostra o resultado na próxima oportunidade. */
    @Override
    public void editApplied() {
        panelForced = true;
    }

    /**
     * O painel novo do nó escolhido, se é hora (pedido, versão do roteador ou das redes mudou, ou passaram
     * {@link #SAMPLE_TICKS} ticks) e ele ficou diferente do último enviado; senão {@code null}. Marca como
     * enviado. Nó carregado: o snapshot de agora, com acesso de edição ao alcance e de leitura fora dele;
     * descarregado: o último lido (vazio se nunca houve). Um nó que sumiu ou deixou de ser visível larga
     * a escolha e manda um painel sem nó. Só no servidor; público para os GameTests.
     */
    public @Nullable TabletPanelPayload pollPanel() {
        if (viewer == null || chosen == null) {
            return null;
        }
        MinecraftServer server = viewer.server;
        NodeIndex.Entry entry = chosenEntry();
        if (entry == null) {
            chosen = null;
            targetRef = EditTargetRef.ATTACHED;
            lastKnown = null;
            sentPanel = null;
            return new TabletPanelPayload(containerId, Optional.empty(), Optional.empty(), PanelAccess.READ_UNLOADED, -1, 0);
        }
        RouterBlockEntity router = loadedRouter(server, NodeIndex.get(server), entry);
        long now = server.getTickCount();
        int dataVersion = NetworkSavedData.get(server).version();
        int changeVersion = router == null ? 0 : router.changeVersion();
        boolean due = panelForced || sentPanel == null || now - panelTick >= SAMPLE_TICKS
                || changeVersion != panelChangeVersion || dataVersion != panelDataVersion;
        if (!due) {
            return null;
        }
        panelForced = false;
        panelTick = now;
        panelChangeVersion = changeVersion;
        panelDataVersion = dataVersion;
        PanelAccess access = PanelAccess.READ_UNLOADED;
        int distance = -1;
        int range = 0;
        if (router != null) {
            lastKnown = RouterSnapshot.capture(router, viewer, validTarget());
            access = inReach(viewer, router) ? PanelAccess.EDIT : PanelAccess.READ_OUT_OF_REACH;
            range = Config.TIERS.get(router.tier()).range().get();
            if (router.getLevel() == viewer.level()) {
                distance = (int) Math.round(Math.sqrt(viewer.position().distanceToSqr(Vec3.atCenterOf(router.getBlockPos()))));
            }
        }
        TabletPanelPayload built = new TabletPanelPayload(containerId, Optional.of(chosen),
                Optional.ofNullable(lastKnown), access, distance, range);
        if (built.equals(sentPanel)) {
            return null;
        }
        sentPanel = built;
        return built;
    }

    // ------------------------------------------------------------------ perfil (servidor)

    /**
     * O Tablet que guarda o perfil: o da mão principal, o da mão secundária ou o primeiro do inventário (o
     * mesmo critério de {@link #hasTablet}); {@link ItemStack#EMPTY} sem nenhum.
     */
    public static ItemStack tabletStack(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.NETWORK_TABLET.get())) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(ModItems.NETWORK_TABLET.get())) {
            return off;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.NETWORK_TABLET.get())) {
                return inventory.getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    /** O perfil guardado no Tablet do jogador, ou {@code null}: vale só com o preset e a origem juntos. */
    private static @Nullable RouterPreset storedProfile(Player player) {
        ItemStack tablet = tabletStack(player);
        return tablet.has(ModDataComponents.PROFILE_SOURCE.get()) ? tablet.get(ModDataComponents.PRESET.get()) : null;
    }

    /** O resumo do perfil para o cabeçalho: os tipos carregados e quantas faces têm modo. */
    private static Optional<ProfileSummary> profileSummary(Player player, @Nullable RouterPreset profile) {
        ProfileSource source = profile == null ? null : tabletStack(player).get(ModDataComponents.PROFILE_SOURCE.get());
        if (source == null) {
            return Optional.empty();
        }
        List<ResourceType> types = new ArrayList<>();
        int faces = 0;
        for (ResourceType type : LoadedTypes.LIST) {
            int withMode = 0;
            for (RelativeSide side : RelativeSide.values()) {
                if (profile.face(type, side).mode() != PortMode.NONE) {
                    withMode++;
                }
            }
            if (withMode > 0) {
                types.add(type);
                faces += withMode;
            }
        }
        return Optional.of(new ProfileSummary(source.nodeName(), source.machine(), List.copyOf(types), faces));
    }

    /** Servidor: o último colar ainda pode ser desfeito. */
    public boolean canUndo() {
        return !undo.isEmpty();
    }

    /** Servidor: o texto do último aviso de colar ou desfazer mandado ao jogador, ou {@code null}. */
    public @Nullable Component lastNotice() {
        return lastNotice;
    }

    /**
     * Uma ação do perfil, validada: o menu aberto e o Tablet no inventário, e para cada nó, na ordem, que o
     * jogador o vê, que está carregado (sem carregar chunk) e ao alcance; a regra das redes é a do
     * {@link PresetApplier}. Colar deixa a origem do perfil de fora. Devolve quantos nós a ação atingiu (1 para
     * copiar e esquecer), ou -1 se recusou.
     */
    public int profile(TabletProfilePayload.Action action, @Nullable NodeKey key, @Nullable ResourceLocation machine,
            List<NodeKey> keys) {
        if (viewer == null || keys.size() > TabletProfilePayload.MAX_KEYS || !stillValid(viewer)) {
            return -1;
        }
        ItemStack tablet = tabletStack(viewer);
        int result = switch (action) {
            case COPY -> copyProfile(tablet, key);
            case FORGET -> {
                tablet.remove(ModDataComponents.PRESET.get());
                tablet.remove(ModDataComponents.PROFILE_SOURCE.get());
                yield 1;
            }
            case PASTE_ONE -> key == null ? -1 : pasteProfile(tablet, List.of(key), true);
            case PASTE_GROUP -> machine == null ? -1 : pasteProfile(tablet, groupKeys(machine), false);
            case PASTE_SELECTED -> keys.isEmpty() ? -1 : pasteProfile(tablet, keys, false);
            case UNDO -> undoPaste();
        };
        if (result >= 0) {
            forced = true;
        }
        return result;
    }

    /** Guarda no Tablet o perfil do nó: o preset das faces e a origem. */
    private int copyProfile(ItemStack tablet, @Nullable NodeKey key) {
        RouterBlockEntity router = key == null ? null : reachableRouter(key);
        if (router == null) {
            return -1;
        }
        String name = router.name();
        tablet.set(ModDataComponents.PRESET.get(), PresetPaste.copy(viewer, router));
        tablet.set(ModDataComponents.PROFILE_SOURCE.get(), new ProfileSource(
                name.length() > ProfileSource.MAX_NAME ? name.substring(0, ProfileSource.MAX_NAME) : name,
                ConfiguratorArea.machine(viewer.serverLevel(), router), key));
        return 1;
    }

    /** O roteador do nó, se o jogador o vê, está carregado e ao alcance; senão {@code null}. */
    private @Nullable RouterBlockEntity reachableRouter(NodeKey key) {
        MinecraftServer server = viewer.server;
        NodeIndex index = NodeIndex.get(server);
        NodeIndex.Entry entry = index.entry(key);
        if (entry == null || !visible(entry, NetworkSavedData.get(server), viewer.hasPermissions(2), viewer.getUUID())) {
            return null;
        }
        RouterBlockEntity router = loadedRouter(server, index, entry);
        return router != null && inReach(viewer, router) ? router : null;
    }

    /**
     * Os nós da máquina que casam com a consulta atual do jogador (a mesma da Lista, reavaliada agora),
     * visíveis, no máximo {@link #MAX_MOVE}. A origem do perfil sai no colar.
     */
    private List<NodeKey> groupKeys(ResourceLocation machine) {
        MinecraftServer server = viewer.server;
        NetworkSavedData data = NetworkSavedData.get(server);
        boolean operator = viewer.hasPermissions(2);
        Map<UUID, String> networkNames = searchWords.length == 0 ? Map.of() : searchNames(data);
        List<NodeKey> result = new ArrayList<>();
        for (NodeIndex.Entry entry : NodeIndex.get(server).entries()) {
            if (result.size() >= MAX_MOVE) {
                break;
            }
            if (entry.machine().equals(machine) && visible(entry, data, operator, viewer.getUUID())
                    && matchesQuery(entry, data, networkNames)) {
                result.add(entry.key());
            }
        }
        return result;
    }

    /** A consulta atual vale para o nó: filtros rápidos, etiquetas, tipo e texto. */
    private boolean matchesQuery(NodeIndex.Entry entry, NetworkSavedData data, Map<UUID, String> networkNames) {
        boolean loaded = entry.router() != null;
        NodeStatus status = status(entry, loaded, fullNodes.contains(entry.key()) ? 1 : 0,
                activeNodes.contains(entry.key()), data);
        boolean problem = status == NodeStatus.UNLOADED || status == NodeStatus.NO_NETWORK || status == NodeStatus.FULL;
        return matchesQuick(problem, !hasExistingNetwork(entry, data), !loaded) && matchesLabels(entry)
                && matchesType(entry, query.type()) && matchesText(entry, networkNames);
    }

    /**
     * Cola o perfil guardado nos nós, menos a origem. O que o jogador não vê é ignorado (com um nó só, recusa);
     * o descarregado e o fora do alcance ficam de fora e são contados no aviso. Guarda o que cada nó era
     * ({@link RouterPreset#copyOf}) para o desfazer, só se algo foi colado. Devolve quantos nós receberam.
     */
    private int pasteProfile(ItemStack tablet, Collection<NodeKey> keys, boolean single) {
        RouterPreset profile = storedProfile(viewer);
        ProfileSource source = tablet.get(ModDataComponents.PROFILE_SOURCE.get());
        if (profile == null || source == null) {
            return -1;
        }
        MinecraftServer server = viewer.server;
        NodeIndex index = NodeIndex.get(server);
        NetworkSavedData data = NetworkSavedData.get(server);
        boolean operator = viewer.hasPermissions(2);
        Map<NodeKey, RouterPreset> before = new LinkedHashMap<>();
        PresetApplier.Checked rule = null;
        Component lastName = Component.empty();
        int skipped = 0;
        for (NodeKey key : new LinkedHashSet<>(keys)) {
            NodeIndex.Entry entry = index.entry(key);
            if (entry == null || !visible(entry, data, operator, viewer.getUUID())) {
                if (single) {
                    return -1;
                }
                continue;
            }
            if (key.equals(source.origin())) {
                continue;
            }
            RouterBlockEntity router = loadedRouter(server, index, entry);
            if (router == null || !inReach(viewer, router)) {
                skipped++;
                continue;
            }
            before.put(key, RouterPreset.copyOf(router));
            rule = PresetPaste.paste(viewer, router, profile, null);
            lastName = nodeName(entry);
        }
        if (!before.isEmpty()) {
            undo.clear();
            undo.putAll(before);
        }
        int pasted = before.size();
        if (pasted > 0 || skipped > 0) {
            sendPasteNotice(pasted, skipped, lastName, rule == null ? 0 : keptFaces(profile, rule));
        }
        return pasted;
    }

    /** Quantas faces do perfil ficaram com a rede de antes, porque o jogador não pode usar a rede delas. */
    private static int keptFaces(RouterPreset profile, PresetApplier.Checked rule) {
        int faces = 0;
        for (ResourceType type : LoadedTypes.LIST) {
            for (RelativeSide side : RelativeSide.values()) {
                UUID network = profile.network(type, side);
                if (network != null && rule.kept().contains(network)) {
                    faces++;
                }
            }
        }
        return faces;
    }

    /** O nome que o aviso dá ao nó: o nome do roteador ou, sem ele, o da máquina. */
    private static Component nodeName(NodeIndex.Entry entry) {
        return entry.name().isEmpty() ? RouterBlock.machineName(BuiltInRegistries.BLOCK.get(entry.machine()))
                : Component.literal(entry.name());
    }

    private void sendPasteNotice(int pasted, int skipped, Component name, int keptFaces) {
        String base = "gui.wirelessautomate.panel.notice.";
        MutableComponent message;
        if (skipped > 0) {
            message = Component.translatable(base + "pasted_skipped", pasted, skipped);
        } else if (pasted == 1) {
            message = Component.translatable(base + "pasted", name);
        } else {
            message = Component.translatable(base + "pasted_many", pasted);
        }
        if (keptFaces > 0) {
            message.append(" · ").append(Component.translatable(base + "kept_networks", keptFaces));
        }
        announce(new PanelNoticePayload(message, pasted > 0 ? PanelNoticePayload.Undo.UNDO_PASTE
                : PanelNoticePayload.Undo.NONE, containerId, ResourceType.ITEM, Direction.UP));
    }

    /**
     * Devolve cada nó do último colar ao que era ({@link RouterPreset#restoreTo}), se ainda é visível, está
     * carregado e ao alcance, e esquece o colar. Devolve quantos voltaram, ou -1 sem nada para desfazer.
     */
    private int undoPaste() {
        if (undo.isEmpty()) {
            return -1;
        }
        Map<NodeKey, RouterPreset> pending = new LinkedHashMap<>(undo);
        undo.clear();
        int restored = 0;
        int skipped = 0;
        for (Map.Entry<NodeKey, RouterPreset> entry : pending.entrySet()) {
            RouterBlockEntity router = reachableRouter(entry.getKey());
            if (router == null) {
                skipped++;
                continue;
            }
            entry.getValue().restoreTo(router);
            restored++;
        }
        String base = "gui.wirelessautomate.panel.notice.";
        announce(PanelNoticePayload.plain(skipped > 0 ? Component.translatable(base + "undone_skipped", restored, skipped)
                : Component.translatable(base + "undone", restored)));
        return restored;
    }

    private void announce(PanelNoticePayload notice) {
        lastNotice = notice.message();
        ModPayloads.sendNotice(viewer, notice);
    }

    // ------------------------------------------------------------------ ações (servidor)

    private void notice(String key, Object... args) {
        notice = Component.translatable("gui.wirelessautomate.tablet.notice." + key, args);
        noticeId++;
        forced = true;
    }

    /** A tela mudou a busca, as etiquetas, os filtros ou o tipo. */
    public void setQuery(Query query) {
        if (!query.text().equals(this.query.text())) {
            searchWords = searchWords(query.text());
        }
        textMachines = Set.copyOf(query.textMachines());
        machineLabels = Set.copyOf(query.machines());
        networkLabels = Set.copyOf(query.networks());
        this.query = query;
        forced = true;
    }

    public Query query() {
        return query;
    }

    /**
     * Põe os nós numa rede: todas as faces (presa, perfis e exceções) do tipo {@code type} ou, vazio,
     * de todos os tipos carregados (as faces de um tipo cujo mod saiu ficam como estão); {@code network}
     * vazio tira da rede. Só nós visíveis ao jogador e carregados
     * (a rede é guardada no roteador); os descarregados ficam de fora e são contados no aviso. Uma face
     * numa rede que o jogador não gerencia ({@link WaNetwork#canManage}: dono ou operador; uma rede
     * pública de outro dono também não) fica como está e é contada no aviso; faces sem rede ou em rede
     * apagada vão. Devolve quantos mudaram, ou -1 se o pedido foi recusado.
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
        int kept = 0;
        java.util.function.Predicate<UUID> canLeave = from -> {
            WaNetwork current = data.network(from);
            return current == null || current.canManage(viewer);
        };
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
            kept += router.setNetworkAllFaces(type.map(List::of).orElseGet(() -> LoadedTypes.LIST), id, canLeave);
            moved++;
        }
        Component name = target == null ? Component.translatable("gui.wirelessautomate.tablet.network.none")
                : target.displayName();
        String suffix = kept > 0 ? ".kept" : "";
        if (unloaded > 0) {
            notice("moved.unloaded" + suffix, moved, name, unloaded, kept);
        } else {
            notice("moved" + suffix, moved, name, kept);
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
