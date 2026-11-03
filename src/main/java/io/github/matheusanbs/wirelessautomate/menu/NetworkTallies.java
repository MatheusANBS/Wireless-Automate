package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.network.IndexRemotes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * As contas de "Nesta rede" e a primeira máquina de cada rede (o ícone de reserva de um grupo sem ícone), refeitas só
 * quando o índice ({@link NodeIndex#dataVersion()}) ou as redes ({@link NetworkSavedData#version()}) mudam. Uma por
 * montador do Tablet; parada, custa duas comparações. A conta percorre as máquinas guardadas no índice (a presa e as
 * remotas de cada nó), sem consultar o mundo.
 */
final class NetworkTallies {
    private static final ResourceLocation AIR = ResourceLocation.withDefaultNamespace("air");
    private final Map<UUID, NetworkTally.Counts> counts = new Object2ObjectOpenHashMap<>();
    private final Map<UUID, ResourceLocation> firstMachine = new Object2ObjectOpenHashMap<>();
    private int indexVersion = -1;
    private int dataVersion = -1;

    void refresh(NodeIndex index, NetworkSavedData data) {
        if (index.dataVersion() == indexVersion && data.version() == dataVersion) {
            return;
        }
        indexVersion = index.dataVersion();
        dataVersion = data.version();
        counts.clear();
        firstMachine.clear();
        for (WaNetwork network : data.networks()) {
            counts.put(network.id(), new NetworkTally.Counts());
        }
        for (NodeIndex.Entry entry : index.entries()) {
            List<UUID> order = entry.networkOrder();
            if (order.isEmpty()) {
                continue;
            }
            ResourceLocation machine = machineOf(entry);
            if (machine != null) {
                for (int i = 0, n = order.size(); i < n; i++) {
                    firstMachine.putIfAbsent(order.get(i), machine);
                }
            }
            if (!entry.machineRolesRead()) {
                // entrada lida do arquivo, até o próximo aviso do roteador: o nó inteiro como uma máquina com os papéis dele
                NetworkTally.machine(entry.roles(), -1L >>> 1, order, entry::networks, counts);
                continue;
            }
            NetworkTally.machine(entry.attachedRoles(), entry.attachedNets(), order, entry::networks, counts);
            IndexRemotes remotes = entry.remotes();
            for (int i = 0, n = remotes.size(); i < n; i++) {
                NetworkTally.machine(remotes.machineRoles(i), remotes.machineNets(i), order, entry::networks, counts);
            }
        }
    }

    /** O bloco que representa o nó: o da presa; sem presa, o do grupo da primeira remota; {@code null} sem nenhum. */
    private static @Nullable ResourceLocation machineOf(NodeIndex.Entry entry) {
        if (entry.showsAttached()) {
            ResourceLocation attached = entry.attachedBlock();
            if (!attached.equals(AIR)) {
                return attached;
            }
        }
        IndexRemotes remotes = entry.remotes();
        if (remotes.size() == 0) {
            return null;
        }
        int g = remotes.groupIndex(remotes.group(0));
        return g < 0 || remotes.groupBlock(g).equals(AIR) ? null : remotes.groupBlock(g);
    }

    @Nullable NetworkTally.Counts counts(UUID network) {
        return counts.get(network);
    }

    Optional<ResourceLocation> firstMachine(UUID network) {
        return Optional.ofNullable(firstMachine.get(network));
    }
}
