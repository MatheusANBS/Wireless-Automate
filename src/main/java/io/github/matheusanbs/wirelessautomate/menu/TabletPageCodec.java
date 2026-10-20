package io.github.matheusanbs.wirelessautomate.menu;

import static io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.MAX_NODES;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Facet;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Live;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Page;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff.ProfileMatch;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff.What;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * O codec binário da página de nós do Tablet ({@link Page}) e do estado e vazão por nó ({@link Live}): a paleta de
 * dimensões, máquinas e redes, os nós e as linhas da árvore, com os tetos de tamanho que a leitura confere. Fica
 * fora do {@link TabletSnapshot} só para ele não crescer; o formato dos bytes é o do {@code Page.STREAM_CODEC}.
 */
final class TabletPageCodec {
    private static final ResourceType[] TYPES = ResourceType.values();

    private TabletPageCodec() {
    }

    /**
     * A página com paletas: as dimensões, as máquinas e as redes vão uma vez cada, e os nós levam o índice delas (num
     * Tablet com centenas de nós, os mesmos ids se repetiam em cada nó).
     */
    static void encodePage(RegistryFriendlyByteBuf buf, Page page) {
        Query.STREAM_CODEC.encode(buf, page.query());
        buf.writeVarInt(page.totalNodes());
        buf.writeVarInt(page.matchingNodes());
        Object2IntLinkedOpenHashMap<ResourceLocation> dimensions = new Object2IntLinkedOpenHashMap<>();
        Object2IntLinkedOpenHashMap<ResourceLocation> machines = new Object2IntLinkedOpenHashMap<>();
        Object2IntLinkedOpenHashMap<UUID> networkIds = new Object2IntLinkedOpenHashMap<>();
        for (NodeView n : page.nodes()) {
            dimensions.putIfAbsent(n.key().dimension().location(), dimensions.size());
            machines.putIfAbsent(n.machine(), machines.size());
            for (List<UUID> networks : n.networks()) {
                for (UUID id : networks) {
                    networkIds.putIfAbsent(id, networkIds.size());
                }
            }
        }
        for (Facet<ResourceLocation> facet : page.groupCounts()) {
            machines.putIfAbsent(facet.id(), machines.size());
        }
        page.originGroup().ifPresent(id -> machines.putIfAbsent(id, machines.size()));
        TabletRows.addBlocks(page.rows(), machines);
        writeLocations(buf, dimensions);
        writeLocations(buf, machines);
        buf.writeVarInt(networkIds.size());
        for (UUID id : networkIds.keySet()) {
            buf.writeUUID(id);
        }
        buf.writeVarInt(page.nodes().size());
        for (NodeView n : page.nodes()) {
            buf.writeVarInt(dimensions.getInt(n.key().dimension().location()));
            buf.writeLong(n.key().pos().asLong());
            buf.writeUtf(n.name(), 64);
            buf.writeVarInt(machines.getInt(n.machine()));
            buf.writeEnum(n.tier());
            for (List<UUID> networks : n.networks()) {
                buf.writeVarInt(networks.size());
                for (UUID id : networks) {
                    buf.writeVarInt(networkIds.getInt(id));
                }
            }
            buf.writeVarInt(n.roles());
            buf.writeBoolean(n.removedNetwork());
            buf.writeLong(n.faceModes());
            writeLive(buf, n);
            buf.writeOptional(n.profile(), (b, match) -> {
                b.writeBoolean(match.equal());
                if (!match.equal()) {
                    b.writeEnum(match.type());
                    b.writeEnum(match.side());
                    b.writeEnum(match.what());
                }
            });
            buf.writeVarInt(n.linked());
        }
        TabletRows.encode(buf, page.rows(), machines);
        buf.writeVarInt(page.groupCounts().size());
        for (Facet<ResourceLocation> facet : page.groupCounts()) {
            buf.writeVarInt(machines.getInt(facet.id()));
            buf.writeVarInt(facet.count());
        }
        buf.writeOptional(page.originGroup(), (b, id) -> b.writeVarInt(machines.getInt(id)));
    }

    private static void writeLocations(RegistryFriendlyByteBuf buf, Object2IntLinkedOpenHashMap<ResourceLocation> palette) {
        buf.writeVarInt(palette.size());
        for (ResourceLocation id : palette.keySet()) {
            buf.writeResourceLocation(id);
        }
    }

    private static List<ResourceLocation> readLocations(RegistryFriendlyByteBuf buf) {
        int count = readCount(buf, MAX_NODES * 2);
        List<ResourceLocation> palette = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            palette.add(buf.readResourceLocation());
        }
        return palette;
    }

    static int readCount(RegistryFriendlyByteBuf buf, int max) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) {
            throw new DecoderException("count " + count + " > " + max);
        }
        return count;
    }

    private static <T> T at(List<T> palette, int index) {
        if (index < 0 || index >= palette.size()) {
            throw new DecoderException("palette index " + index + " of " + palette.size());
        }
        return palette.get(index);
    }

    /** O que muda a cada amostra num nó: o estado e a vazão de cada tipo (ver {@link Live}). */
    private static void writeLive(RegistryFriendlyByteBuf buf, NodeView n) {
        buf.writeEnum(n.status());
        for (ResourceType type : TYPES) {
            buf.writeVarLong(n.rate(type));
        }
    }

    static Live readLive(RegistryFriendlyByteBuf buf) {
        NodeStatus status = buf.readEnum(NodeStatus.class);
        List<Long> rates = new ArrayList<>(TYPES.length);
        for (int t = 0; t < TYPES.length; t++) {
            rates.add(buf.readVarLong());
        }
        return new Live(status, List.copyOf(rates));
    }

    static Page decodePage(RegistryFriendlyByteBuf buf) {
        Query query = Query.STREAM_CODEC.decode(buf);
        int total = buf.readVarInt();
        int matching = buf.readVarInt();
        List<ResourceLocation> dimensions = readLocations(buf);
        List<ResourceLocation> machines = readLocations(buf);
        int networkCount = readCount(buf, MAX_NODES * TYPES.length * 8);
        List<UUID> networkIds = new ArrayList<>(networkCount);
        for (int i = 0; i < networkCount; i++) {
            networkIds.add(buf.readUUID());
        }
        int nodeCount = readCount(buf, MAX_NODES);
        List<NodeView> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            NodeKey key = new NodeKey(ResourceKey.create(Registries.DIMENSION, at(dimensions, buf.readVarInt())),
                    BlockPos.of(buf.readLong()));
            String name = buf.readUtf(64);
            ResourceLocation machine = at(machines, buf.readVarInt());
            RouterTier tier = buf.readEnum(RouterTier.class);
            List<List<UUID>> typeNetworks = new ArrayList<>(TYPES.length);
            for (int t = 0; t < TYPES.length; t++) {
                int count = readCount(buf, networkCount);
                List<UUID> ids = new ArrayList<>(count);
                for (int k = 0; k < count; k++) {
                    ids.add(at(networkIds, buf.readVarInt()));
                }
                typeNetworks.add(List.copyOf(ids));
            }
            int roles = buf.readVarInt();
            boolean removed = buf.readBoolean();
            long faceModes = buf.readLong();
            Live live = readLive(buf);
            Optional<ProfileMatch> profile = buf.readOptional(b -> b.readBoolean() ? ProfileMatch.EQUAL
                    : ProfileMatch.differs(b.readEnum(ResourceType.class), b.readEnum(RemoteSide.class),
                            b.readEnum(What.class)));
            int linked = buf.readVarInt();
            nodes.add(new NodeView(key, name, machine, tier, List.copyOf(typeNetworks), roles, live.status(), removed,
                    faceModes, live.rates(), profile, linked));
        }
        TabletRows rows = TabletRows.decode(buf, machines, nodeCount);
        int groupCount = readCount(buf, MAX_NODES);
        List<Facet<ResourceLocation>> groupCounts = new ArrayList<>(groupCount);
        for (int i = 0; i < groupCount; i++) {
            groupCounts.add(new Facet<>(at(machines, buf.readVarInt()), buf.readVarInt()));
        }
        return new Page(query, total, matching, List.copyOf(nodes), rows, List.copyOf(groupCounts),
                buf.readOptional(b -> at(machines, b.readVarInt())));
    }
}
