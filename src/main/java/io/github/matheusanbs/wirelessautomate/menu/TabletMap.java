package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.MapNearest;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.packet.TabletMapPayload;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Mapa do Tablet no servidor: com ela aberta, a cada {@link TabletMenu#SAMPLE_TICKS} ticks monta o
 * {@link TabletMapPayload} com todos os roteadores que o jogador vê na dimensão dele (os {@link
 * TabletMapPayload#MAX_POINTS} mais perto, se forem mais), sem a busca nem os filtros da Lista, e só o devolve se
 * ficou diferente do último enviado. Sem a aba aberta não roda nada. Uma passada pelo índice por montagem, sem
 * objeto por nó (listas primitivas); a ordem do índice é a da Lista, compartilhada por todos os jogadores.
 */
final class TabletMap {
    private final ServerPlayer viewer;
    private final int containerId;
    private boolean watching;
    private long lastBuild;
    private @Nullable TabletMapPayload sent;

    TabletMap(ServerPlayer viewer, int containerId) {
        this.viewer = viewer;
        this.containerId = containerId;
    }

    /** A tela mostra a aba {@code tab}: só a aba Mapa recebe o pacote. */
    void tab(int tab) {
        if (tab == TabletMenu.MAP_TAB) {
            watching = true;
        } else {
            stop();
        }
    }

    /** Saiu da aba ou fechou o Tablet: a próxima vez manda tudo de novo. */
    void stop() {
        watching = false;
        sent = null;
    }

    /** Os pontos de uma montagem nova, se mudaram desde o último enviado; senão {@code null}. */
    @Nullable TabletMapPayload poll() {
        if (!watching) {
            return null;
        }
        long now = viewer.server.getTickCount();
        if (sent != null && now >= lastBuild && now - lastBuild < TabletMenu.SAMPLE_TICKS) {
            return null;
        }
        lastBuild = now;
        TabletMapPayload built = build();
        if (built.equals(sent)) {
            return null;
        }
        sent = built;
        return built;
    }

    private TabletMapPayload build() {
        MinecraftServer server = viewer.server;
        NodeIndex index = NodeIndex.get(server);
        TabletSample shared = TabletSample.get(server);
        NetworkSavedData data = NetworkSavedData.get(server);
        boolean operator = viewer.hasPermissions(2);
        UUID me = viewer.getUUID();
        var dimension = viewer.level().dimension();
        BlockPos at = viewer.blockPosition();
        LongArrayList positions = new LongArrayList();
        ByteArrayList statuses = new ByteArrayList();
        int other = 0;
        List<NodeIndex.Entry> entries = shared.sorted(index);
        for (int i = 0, n = entries.size(); i < n; i++) {
            NodeIndex.Entry entry = entries.get(i);
            if (!TabletNodes.visible(entry, data, operator, me)) {
                continue;
            }
            if (!entry.key().dimension().equals(dimension)) {
                other++;
                continue;
            }
            boolean loaded = entry.router() != null;
            NodeStatus status = TabletNodes.status(entry, loaded, loaded && shared.full(entry.key()) ? 1 : 0,
                    loaded && shared.active(entry.key()), data);
            positions.add(entry.key().pos().asLong());
            statuses.add((byte) status.ordinal());
        }
        int total = positions.size();
        if (total <= TabletMapPayload.MAX_POINTS) {
            return new TabletMapPayload(containerId, other, total, positions.toLongArray(), statuses.toByteArray());
        }
        // mais que o teto: os mais perto do jogador, na ordem do índice (estável enquanto ele não anda)
        long[] distances = new long[total];
        for (int i = 0; i < total; i++) {
            long dx = BlockPos.getX(positions.getLong(i)) - at.getX();
            long dz = BlockPos.getZ(positions.getLong(i)) - at.getZ();
            distances[i] = dx * dx + dz * dz;
        }
        int[] kept = MapNearest.pick(distances, TabletMapPayload.MAX_POINTS);
        long[] keptPositions = new long[kept.length];
        byte[] keptStatuses = new byte[kept.length];
        for (int i = 0; i < kept.length; i++) {
            keptPositions[i] = positions.getLong(kept[i]);
            keptStatuses[i] = statuses.getByte(kept[i]);
        }
        return new TabletMapPayload(containerId, other, total, keptPositions, keptStatuses);
    }
}
