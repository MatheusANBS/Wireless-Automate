package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Facet;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickCounts;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.RateMath;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSource;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Monta o {@link TabletSnapshot} de um jogador: percorre o índice, conta as facetas e os filtros rápidos,
 * mede a vazão por nó na amostra e monta a página, as redes e os grupos. É dono do estado da
 * amostra (quando foi, o movido por nó, os nós ativos e os cheios) e das versões lidas na última montagem.
 */
final class TabletSnapshotBuilder {
    private final ServerPlayer viewer;
    private final TabletQuery query;
    private long sampleTick;
    private long buildTick;
    /** {@link NodeIndex#dataVersion()} e versão das redes lidas na última montagem. */
    private int builtIndexVersion;
    private int builtDataVersion;
    /** Nós cheios na última sondagem de todos os nós carregados e quando ela foi. */
    private final Set<NodeKey> fullNodes = new HashSet<>();
    private long fullTick = Long.MIN_VALUE;
    /** Totais movidos por nó na última amostra, por {@link ResourceType#ordinal()}. */
    private final Map<NodeKey, long[]> lastMoved = new HashMap<>();
    /** Nós que moveram algo na última amostra. */
    private final Set<NodeKey> activeNodes = new HashSet<>();
    /** Vazão por nó na última amostra, por tipo, na unidade do registro (só os que moveram algo). */
    private final Map<NodeKey, long[]> nodeRates = new HashMap<>();

    TabletSnapshotBuilder(ServerPlayer viewer, TabletQuery query) {
        this.viewer = viewer;
        this.query = query;
        this.sampleTick = viewer.server.getTickCount();
    }

    /** Passou a janela da amostra. */
    boolean sampleDue(long now) {
        return now - sampleTick >= TabletMenu.SAMPLE_TICKS;
    }

    /**
     * Um dado salvo do índice ou as redes mudaram desde a última montagem; um chunk que carrega ou
     * descarrega espera a próxima amostra.
     */
    boolean worldChanged(MinecraftServer server) {
        return NodeIndex.get(server).dataVersion() != builtIndexVersion
                || NetworkSavedData.get(server).version() != builtDataVersion;
    }

    /** Passou o intervalo mínimo desde a última montagem. */
    boolean rebuildAllowed(long now) {
        return now - buildTick >= TabletMenu.REBUILD_TICKS;
    }

    /**
     * O que a tela mostra agora; com {@code sample}, mede vazão e atividade desde a amostra anterior.
     *
     * <p>Percorre o índice uma vez para contar, filtrar e medir a vazão (esta só na amostra), mas só
     * sonda o motor ({@link io.github.matheusanbs.wirelessautomate.network.NodeProbe}) para os nós da
     * página. Com o filtro de problemas, os nós cheios vêm de uma sondagem de todos que vale por uma amostra.
     */
    TabletSnapshot build(boolean sample, Component notice, int noticeId) {
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
            nodeRates.clear();
        }
        boolean probeAll = sample || fullTick == Long.MIN_VALUE || now - fullTick >= TabletMenu.SAMPLE_TICKS;
        if (probeAll) {
            fullNodes.clear();
            fullTick = now;
        }

        Map<UUID, Totals> totals = new LinkedHashMap<>();
        for (WaNetwork network : data.networks()) {
            if (TabletNodes.visible(network, operator, me)) {
                totals.put(network.id(), new Totals());
            }
        }

        BlockPos center = viewer.blockPosition();
        Map<UUID, String> networkNames = query.networkNames(data);
        Scan scan = scan(index, data, totals, sample, probeAll, operator, me, now, elapsed, networkNames);
        if (sample) {
            lastMoved.keySet().retainAll(scan.seen);
        }

        List<Match> matches = scan.matches;
        matches.sort(Comparator.<Match, ResourceLocation>comparing(m -> m.entry.machine())
                .thenComparing(m -> m.entry.name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(m -> m.entry.key().dimension().location())
                .thenComparingLong(m -> m.entry.key().pos().asLong()));
        RouterPreset profile = TabletProfile.storedProfile(viewer);
        ProfileSource source = profile == null ? null
                : TabletMenu.tabletStack(viewer).get(ModDataComponents.PROFILE_SOURCE.get());
        Optional<ResourceLocation> originGroup = Optional.empty();
        Map<ResourceLocation, Integer> groupSizes = new HashMap<>();
        for (Match match : matches) {
            groupSizes.merge(match.entry.machine(), 1, Integer::sum);
            if (source != null && match.entry.key().equals(source.origin())) {
                originGroup = Optional.of(match.entry.machine());
            }
        }
        List<Facet<ResourceLocation>> groupCounts = new ArrayList<>();
        List<NodeView> nodes = pageNodes(matches, data, profile, groupSizes, groupCounts);

        Map<UUID, String> names = new HashMap<>();
        List<NetworkView> networks = new ArrayList<>();
        totals.forEach((id, t) -> {
            WaNetwork network = data.network(id);
            networks.add(new NetworkView(id, network.name(), network.color(), ownerName(server, network.owner(), names),
                    network.owner().equals(me), network.canManage(viewer), network.isPublic(), data.isPaused(id),
                    t.nodes, t.unloaded));
        });
        List<GroupView> groups = new ArrayList<>();
        for (WaGroup group : data.groups()) {
            if (operator || group.owner().equals(me)) {
                groups.add(new GroupView(group.id(), group.name(), ownerName(server, group.owner(), names),
                        group.canManage(viewer), group.paused(), group.networks()));
            }
        }

        return new TabletSnapshot(operator, viewer.level().dimension().location(), center,
                Optional.ofNullable(data.activeNetwork(me)).filter(id -> data.network(id) != null),
                query.query(), scan.total, matches.size(),
                List.copyOf(networks), List.copyOf(groups), facets(scan.machineCounts), facets(scan.networkCounts),
                new QuickCounts(scan.problemCount, scan.noNetworkCount, scan.unloadedCount),
                TabletProfile.profileSummary(viewer, profile), List.copyOf(nodes), List.copyOf(groupCounts),
                originGroup, notice, noticeId);
    }

    /** Uma passada pelo índice: conta as facetas e os filtros rápidos, mede a vazão na amostra e filtra pela consulta. */
    private Scan scan(NodeIndex index, NetworkSavedData data, Map<UUID, Totals> totals, boolean sample,
            boolean probeAll, boolean operator, UUID me, long now, long elapsed, Map<UUID, String> networkNames) {
        Scan scan = new Scan(sample);
        for (NodeIndex.Entry entry : index.entries()) {
            if (!TabletNodes.visible(entry, data, operator, me)) {
                continue;
            }
            scan.total++;
            NodeKey key = entry.key();
            RouterBlockEntity router = entry.router();
            boolean loaded = router != null;
            boolean moved = false;
            if (loaded) {
                if (sample) {
                    scan.seen.add(key);
                    moved = sampleMoved(entry, router, elapsed);
                    if (moved) {
                        activeNodes.add(key);
                    }
                } else {
                    moved = activeNodes.contains(key);
                }
                if (probeAll && TabletNodes.fullDestinations(router, now) > 0) {
                    fullNodes.add(key);
                }
            }
            countMembership(entry, totals, loaded);
            NodeStatus status = TabletNodes.status(entry, loaded, fullNodes.contains(key) ? 1 : 0, moved, data);
            boolean problem = status == NodeStatus.UNLOADED || status == NodeStatus.NO_NETWORK
                    || status == NodeStatus.FULL;
            boolean noNetwork = !TabletNodes.hasExistingNetwork(entry, data);
            if (problem) {
                scan.problemCount++;
            }
            if (noNetwork) {
                scan.noNetworkCount++;
            }
            if (!loaded) {
                scan.unloadedCount++;
            }
            scan.machineCounts.merge(entry.machine(), 1, Integer::sum);
            for (UUID id : TabletNodes.distinctNetworks(entry)) {
                if (totals.containsKey(id)) {
                    scan.networkCounts.merge(id, 1, Integer::sum);
                }
            }
            if (!query.matches(entry, problem, noNetwork, !loaded, networkNames)) {
                continue;
            }
            scan.matches.add(new Match(entry, status));
        }
        return scan;
    }

    /** Os nós da página (até {@link TabletSnapshot#MAX_NODES}), e a contagem de cada grupo de máquina que aparece nela. */
    private List<NodeView> pageNodes(List<Match> matches, NetworkSavedData data, RouterPreset profile,
            Map<ResourceLocation, Integer> groupSizes, List<Facet<ResourceLocation>> groupCounts) {
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
                    : Optional.of(ProfileDiff.match(profile, loadedNode));
            long[] rates = nodeRates.get(entry.key());
            List<Long> rateList = new ArrayList<>(ResourceType.values().length);
            for (ResourceType type : ResourceType.values()) {
                rateList.add(rates == null ? 0L : rates[type.ordinal()]);
            }
            nodes.add(new NodeView(entry.key(), entry.name(), entry.machine(), entry.tier(), List.copyOf(networks),
                    entry.roles(), match.status, removed, entry.faceModes(), List.copyOf(rateList), comparison));
            if (groupCounts.isEmpty() || !groupCounts.getLast().id().equals(entry.machine())) {
                groupCounts.add(new Facet<>(entry.machine(), groupSizes.get(entry.machine())));
            }
        }
        return nodes;
    }

    /** A consulta atual vale para o nó, com o estado da última amostra: filtros rápidos, etiquetas, tipo e texto. */
    boolean matchesQuery(NodeIndex.Entry entry, NetworkSavedData data, Map<UUID, String> networkNames) {
        boolean loaded = entry.router() != null;
        NodeStatus status = TabletNodes.status(entry, loaded, fullNodes.contains(entry.key()) ? 1 : 0,
                activeNodes.contains(entry.key()), data);
        boolean problem = status == NodeStatus.UNLOADED || status == NodeStatus.NO_NETWORK || status == NodeStatus.FULL;
        return query.matches(entry, problem, !TabletNodes.hasExistingNetwork(entry, data), !loaded, networkNames);
    }

    /** As facetas pela contagem, das maiores para as menores (a chave desempata, para o cabeçalho ser estável). */
    private static <T extends Comparable<T>> List<Facet<T>> facets(Map<T, Integer> counts) {
        List<Facet<T>> facets = new ArrayList<>(counts.size());
        counts.forEach((id, count) -> facets.add(new Facet<>(id, count)));
        facets.sort(Comparator.<Facet<T>>comparingInt(Facet::count).reversed().thenComparing(Facet::id));
        return facets;
    }

    /** Mede a vazão do nó por tipo; devolve se ele moveu algo desde a amostra anterior. */
    private boolean sampleMoved(NodeIndex.Entry entry, RouterBlockEntity router, long elapsed) {
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
        for (ResourceType type : TabletNodes.TRANSFER_TYPES) {
            long now = router.moved(type);
            long delta = Math.max(0, now - last[type.ordinal()]);
            if (delta > 0) {
                moved = true;
                nodeRates.computeIfAbsent(entry.key(), key -> new long[ResourceType.values().length])[type.ordinal()] =
                        RateMath.rate(delta, elapsed, type.ratePerTick());
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

    /** O que uma passada pelo índice contou e os nós que passaram na consulta. */
    private static final class Scan {
        final Map<ResourceLocation, Integer> machineCounts = new HashMap<>();
        final Map<UUID, Integer> networkCounts = new HashMap<>();
        int problemCount;
        int noNetworkCount;
        int unloadedCount;
        final List<Match> matches = new ArrayList<>();
        final Set<NodeKey> seen;
        int total;

        Scan(boolean sample) {
            this.seen = sample ? new HashSet<>() : null;
        }
    }

    /** Um nó que passou na consulta, com o estado que a lista mostra. */
    private record Match(NodeIndex.Entry entry, NodeStatus status) {
    }

    /** Somas de uma rede numa montagem. */
    private static final class Totals {
        int nodes;
        int unloaded;
    }
}
