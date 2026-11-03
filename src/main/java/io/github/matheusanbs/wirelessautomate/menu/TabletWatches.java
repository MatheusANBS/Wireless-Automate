package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.packet.TabletFlowsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMapPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletVerdictPayload;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Os vigias do servidor que só rodam com uma aba aberta: os Fluxos, o Mapa e o veredito da Máquina. */
final class TabletWatches {
    private final TabletFlows flows;
    private final TabletMap map;
    private final TabletVerdict verdict;

    TabletWatches(ServerPlayer viewer, int containerId, TabletChosenNode chosen) {
        this.flows = new TabletFlows(viewer, containerId);
        this.map = new TabletMap(viewer, containerId);
        this.verdict = new TabletVerdict(viewer, containerId, chosen);
    }

    /** A tela mostra a aba {@code tab}. */
    void tab(int tab) {
        flows.tab(tab);
        map.tab(tab);
        verdict.tab(tab);
    }

    /** Fechou o Tablet. */
    void stop() {
        flows.stop();
        map.stop();
        verdict.stop();
    }

    /** Os pacotes novos dos vigias, se mudaram. */
    void poll(List<CustomPacketPayload> out) {
        TabletFlowsPayload flowsNow = flows.poll();
        if (flowsNow != null) {
            out.add(flowsNow);
        }
        TabletMapPayload mapNow = map.poll();
        if (mapNow != null) {
            out.add(mapNow);
        }
        TabletVerdictPayload verdictNow = verdict.poll();
        if (verdictNow != null) {
            out.add(verdictNow);
        }
    }

    /** Ver {@link TabletMenu#pollFlows}. */
    @Nullable TabletFlowsPayload pollFlows() {
        return flows.poll();
    }

    /** Ver {@link TabletMenu#pollVerdict}. */
    @Nullable TabletVerdictPayload pollVerdict() {
        return verdict.poll();
    }
}
