package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.network.FlowSampler;
import io.github.matheusanbs.wirelessautomate.network.FlowStats;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.TickBudget;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TabletFlowsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletFlowsPayload.NetworkFlows;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Fluxos do Tablet no servidor: com ela aberta, o jogador entra na lista de quem olha o amostrador
 * ({@link FlowSampler#watch}), que roda sempre; trocar de aba ou fechar o Tablet tira o jogador dela e o
 * pacote para. A cada amostra nova
 * monta o {@link TabletFlowsPayload} com as redes que o jogador vê (as dele e as públicas; todas para
 * operador) e as faces sem rede só dos nós que ele vê na Lista, e só o devolve se ficou diferente do último
 * enviado. O tempo de cada rede é uma leitura direta do gerenciador, sem percorrer as portas. Como a amostra sai de 20 em 20 ticks, o
 * pacote sai no máximo uma vez por segundo.
 */
final class TabletFlows {
    private final ServerPlayer viewer;
    private final int containerId;
    private boolean watching;
    /** O {@link FlowSampler.Report#sampledAt()} da última amostra lida. */
    private long readSample = Long.MIN_VALUE;
    private @Nullable TabletFlowsPayload sent;

    TabletFlows(ServerPlayer viewer, int containerId) {
        this.viewer = viewer;
        this.containerId = containerId;
    }

    /** A tela mostra a aba {@code tab}: só a aba Fluxos recebe o pacote. */
    void tab(int tab) {
        if (tab == TabletMenu.FLOWS_TAB) {
            if (!watching) {
                watching = true;
                NetworkManager.get().flows().watch(viewer.getUUID());
            }
        } else {
            stop();
        }
    }

    /** Saiu da aba ou fechou o Tablet: deixa de olhar, e a próxima vez manda tudo de novo. */
    void stop() {
        if (watching) {
            watching = false;
            NetworkManager.get().flows().unwatch(viewer.getUUID());
        }
        readSample = Long.MIN_VALUE;
        sent = null;
    }

    /** Os Fluxos de uma amostra nova, se mudaram desde o último enviado; senão {@code null}. */
    @Nullable TabletFlowsPayload poll() {
        if (!watching) {
            return null;
        }
        FlowSampler.Report report = NetworkManager.get().flows().report();
        if (report.sampledAt() < 0 || report.sampledAt() == readSample) {
            return null;
        }
        readSample = report.sampledAt();
        TabletFlowsPayload built = build(report);
        if (built.equals(sent)) {
            return null;
        }
        sent = built;
        return built;
    }

    private TabletFlowsPayload build(FlowSampler.Report report) {
        MinecraftServer server = viewer.server;
        NetworkSavedData data = NetworkSavedData.get(server);
        boolean operator = viewer.hasPermissions(2);
        UUID me = viewer.getUUID();
        Map<UUID, List<FlowStats>> byNetwork = new HashMap<>();
        for (FlowStats stats : report.flows()) {
            byNetwork.computeIfAbsent(stats.network(), id -> new ArrayList<>()).add(stats);
        }
        List<NetworkFlows> networks = new ArrayList<>();
        for (WaNetwork network : data.networks()) {
            if (!TabletNodes.visible(network, operator, me)) {
                continue;
            }
            boolean paused = data.isPaused(network.id());
            List<FlowStats> types = byNetwork.getOrDefault(network.id(), List.of());
            if (types.isEmpty() && !paused) {
                continue;
            }
            List<FlowStats> sorted = new ArrayList<>(types);
            sorted.sort(Comparator.comparingInt(stats -> stats.type().ordinal()));
            networks.add(new NetworkFlows(network.id(), paused,
                    Math.round(NetworkManager.get().averageNanos(network.id())), List.copyOf(sorted)));
        }
        networks.sort(Comparator.comparing(NetworkFlows::id));
        TickBudget budget = NetworkManager.get().budget();
        return new TabletFlowsPayload(containerId, Math.round(budget.averageUsedNanos()), budget.limitNanos(),
                List.copyOf(networks), operator ? report.withoutNetwork() : withoutNetwork(report, data, me));
    }

    /**
     * As faces sem rede por tipo só dos nós que o jogador vê na Lista ({@link TabletNodes#visible}): quem os
     * colocou, ou com alguma face numa rede dele ou pública. Um nó fora do índice não conta.
     */
    private List<Integer> withoutNetwork(FlowSampler.Report report, NetworkSavedData data, UUID me) {
        int[] counts = new int[ResourceType.values().length];
        if (!report.nodesWithout().isEmpty()) {
            NodeIndex index = NodeIndex.get(viewer.server);
            for (FlowSampler.NodeWithoutNetwork node : report.nodesWithout()) {
                NodeIndex.Entry entry = index.entry(node.node());
                if (entry == null || !TabletNodes.visible(entry, data, false, me)) {
                    continue;
                }
                for (int t = 0; t < counts.length && t < node.counts().size(); t++) {
                    counts[t] += node.counts().get(t);
                }
            }
        }
        List<Integer> list = new ArrayList<>(counts.length);
        for (int count : counts) {
            list.add(count);
        }
        return List.copyOf(list);
    }
}
