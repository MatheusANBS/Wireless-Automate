package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.packet.TabletMapPayload;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Os vigias do servidor que só rodam com uma aba aberta: por ora, o Mapa. */
final class TabletWatches {
    private final TabletMap map;

    TabletWatches(ServerPlayer viewer, int containerId) {
        this.map = new TabletMap(viewer, containerId);
    }

    /** A tela mostra a aba {@code tab}. */
    void tab(int tab) {
        map.tab(tab);
    }

    /** Fechou o Tablet. */
    void stop() {
        map.stop();
    }

    /** Os pacotes novos dos vigias, se mudaram. */
    void poll(List<CustomPacketPayload> out) {
        TabletMapPayload mapNow = map.poll();
        if (mapNow != null) {
            out.add(mapNow);
        }
    }
}
