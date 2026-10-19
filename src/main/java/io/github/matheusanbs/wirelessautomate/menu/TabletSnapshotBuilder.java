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
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSource;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Monta o {@link TabletSnapshot} de um jogador: percorre o índice na ordem da Lista, conta as facetas e os filtros
 * rápidos e monta a página, as redes e os grupos. A vazão, os nós ativos, os cheios e a ordem vêm da amostra do
 * servidor ({@link TabletSample}), a mesma para todos os jogadores; aqui ficam só quando este jogador amostrou e
 * as versões lidas na última montagem.
 */
final class TabletSnapshotBuilder {
    private final ServerPlayer viewer;
    private final TabletQuery query;
    private final TabletTree tree;
    private long sampleTick;
    private long buildTick;
    /** {@link NodeIndex#dataVersion()} e versão das redes lidas na última montagem. */
    private int builtIndexVersion;
    private int builtDataVersion;
    /** Redes já contadas do nó em {@link #countMembership}, reaproveitado. */
    private final List<Totals> counted = new ArrayList<>();

    TabletSnapshotBuilder(ServerPlayer viewer, TabletQuery query, TabletTree tree) {
        this.viewer = viewer;
        this.query = query;
        this.tree = tree;
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
     * O que a tela mostra agora; com {@code sample}, pede ao {@link TabletSample} uma amostra nova (se a do servidor
     * já tem a idade da janela). Percorre o índice uma vez, já na ordem da Lista, para contar e filtrar.
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
        TabletSample shared = TabletSample.get(server);
        if (sample) {
            sampleTick = now;
        }
        shared.sampleIfDue(index, now);

        Map<UUID, Totals> totals = new LinkedHashMap<>();
        for (WaNetwork network : data.networks()) {
            if (TabletNodes.visible(network, operator, me)) {
                totals.put(network.id(), new Totals());
            }
        }

        Map<UUID, String> networkNames = query.networkNames(data);
        Scan scan = scan(shared.sorted(index), shared, data, totals, operator, me, networkNames);
        List<Match> matches = scan.matches;
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
        tree.prune(index, data, operator, me);
        List<TabletRows.Row> rows = new ArrayList<>();
        List<TabletRows.MachineLive> lives = new ArrayList<>();
        List<NodeView> nodes = pageNodes(matches, shared, data, profile, groupSizes, groupCounts, rows, lives, now);

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

        return new TabletSnapshot(operator, viewer.level().dimension().location(),
                Optional.ofNullable(data.activeNetwork(me)).filter(id -> data.network(id) != null),
                query.query(), scan.total, matches.size(),
                List.copyOf(networks), List.copyOf(groups), facets(scan.machineCounts), facets(scan.networkCounts),
                new QuickCounts(scan.problemCount, scan.noNetworkCount, scan.unloadedCount),
                TabletProfile.profileSummary(viewer, profile), List.copyOf(nodes),
                rows.isEmpty() ? TabletRows.EMPTY : new TabletRows(List.copyOf(rows), List.copyOf(lives)),
                List.copyOf(groupCounts),
                originGroup, notice, noticeId);
    }

    /** Uma passada pelo índice, na ordem da Lista: conta as facetas e os filtros rápidos e filtra pela consulta. */
    private Scan scan(List<NodeIndex.Entry> entries, TabletSample shared, NetworkSavedData data,
            Map<UUID, Totals> totals, boolean operator, UUID me, Map<UUID, String> networkNames) {
        Scan scan = new Scan();
        for (int i = 0, n = entries.size(); i < n; i++) {
            NodeIndex.Entry entry = entries.get(i);
            if (!TabletNodes.visible(entry, data, operator, me)) {
                continue;
            }
            scan.total++;
            NodeKey key = entry.key();
            RouterBlockEntity router = entry.router();
            boolean loaded = router != null;
            boolean moved = loaded && shared.active(key);
            countMembership(entry, totals, loaded);
            NodeStatus status = TabletNodes.status(entry, loaded, loaded && shared.full(key) ? 1 : 0, moved, data);
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
            scan.machineCounts.addTo(entry.machine(), 1);
            for (UUID id : TabletNodes.distinctNetworks(entry)) {
                if (totals.containsKey(id)) {
                    scan.networkCounts.addTo(id, 1);
                }
            }
            if (!query.matches(entry, problem, noNetwork, !loaded, networkNames)) {
                continue;
            }
            scan.matches.add(new Match(entry, status));
        }
        return scan;
    }

    /**
     * Os nós da página e, abaixo dos abertos na árvore, as linhas deles em {@code rows} (com o estado e a vazão das
     * máquinas em {@code lives}), juntos até {@link TabletSnapshot#MAX_NODES}; e a contagem de cada grupo de máquina
     * que aparece nela.
     */
    private List<NodeView> pageNodes(List<Match> matches, TabletSample shared, NetworkSavedData data,
            RouterPreset profile, Map<ResourceLocation, Integer> groupSizes, List<Facet<ResourceLocation>> groupCounts,
            List<TabletRows.Row> rows, List<TabletRows.MachineLive> lives, long now) {
        List<NodeView> nodes = new ArrayList<>();
        for (int i = 0; i < matches.size() && nodes.size() + rows.size() < TabletSnapshot.MAX_NODES; i++) {
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
            long[] rates = shared.rates(entry.key());
            List<Long> rateList = new ArrayList<>(ResourceType.values().length);
            for (ResourceType type : ResourceType.values()) {
                rateList.add(rates == null ? 0L : rates[type.ordinal()]);
            }
            nodes.add(new NodeView(entry.key(), entry.name(), entry.machine(), entry.tier(), List.copyOf(networks),
                    entry.roles(), match.status, removed, entry.faceModes(), List.copyOf(rateList), comparison));
            if (groupCounts.isEmpty() || !groupCounts.getLast().id().equals(entry.machine())) {
                groupCounts.add(new Facet<>(entry.machine(), groupSizes.get(entry.machine())));
            }
            if (tree.isOpen(entry.key())) {
                tree.appendRows(entry, nodes.size() - 1, TabletSnapshot.MAX_NODES - nodes.size() - rows.size(), rows,
                        lives, shared.machines(), now);
            }
        }
        return nodes;
    }

    /** A consulta atual vale para o nó, com o estado da última amostra: filtros rápidos, etiquetas, tipo e texto. */
    boolean matchesQuery(NodeIndex.Entry entry, NetworkSavedData data, Map<UUID, String> networkNames) {
        boolean loaded = entry.router() != null;
        TabletSample shared = TabletSample.get(viewer.server);
        NodeStatus status = TabletNodes.status(entry, loaded, loaded && shared.full(entry.key()) ? 1 : 0,
                loaded && shared.active(entry.key()), data);
        boolean problem = status == NodeStatus.UNLOADED || status == NodeStatus.NO_NETWORK || status == NodeStatus.FULL;
        return query.matches(entry, problem, !TabletNodes.hasExistingNetwork(entry, data), !loaded, networkNames);
    }

    /** As facetas pela contagem, das maiores para as menores (a chave desempata, para o cabeçalho ser estável). */
    private static <T extends Comparable<T>> List<Facet<T>> facets(Object2IntOpenHashMap<T> counts) {
        List<Facet<T>> facets = new ArrayList<>(counts.size());
        for (Object2IntMap.Entry<T> count : Object2IntMaps.fastIterable(counts)) {
            facets.add(new Facet<>(count.getKey(), count.getIntValue()));
        }
        facets.sort(Comparator.<Facet<T>>comparingInt(Facet::count).reversed().thenComparing(Facet::id));
        return facets;
    }

    /**
     * Conta o nó uma vez em cada rede em que alguma face dele está. Uma rede em vários tipos conta uma
     * vez: o {@link #counted} guarda as já contadas deste nó (poucas: busca linear, sem alocar).
     */
    private void countMembership(NodeIndex.Entry entry, Map<UUID, Totals> totals, boolean loaded) {
        counted.clear();
        for (ResourceType type : LoadedTypes.LIST) {
            for (UUID id : entry.networks(type)) {
                Totals network = totals.get(id);
                if (network == null) {
                    continue;
                }
                if (!counted.contains(network)) {
                    counted.add(network);
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
        final Object2IntOpenHashMap<ResourceLocation> machineCounts = new Object2IntOpenHashMap<>();
        final Object2IntOpenHashMap<UUID> networkCounts = new Object2IntOpenHashMap<>();
        int problemCount;
        int noNetworkCount;
        int unloadedCount;
        final List<Match> matches = new ArrayList<>();
        int total;
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
