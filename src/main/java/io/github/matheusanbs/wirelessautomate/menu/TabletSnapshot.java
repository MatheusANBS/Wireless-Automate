package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Tudo que a tela do Tablet mostra, montado no servidor só com ela aberta e só com o que o jogador
 * pode ver (as redes dele e as públicas; todas para operador). Os nós vêm em páginas de
 * {@link #PAGE_SIZE}, já filtrados pela busca e pelo papel ({@link Query}) e ordenados pela
 * distância ao jogador (os de outras dimensões no fim), para nenhum pacote levar milhares de nós.
 *
 * @param dimension     dimensão do jogador, para o mapa
 * @param center        posição do jogador, centro do mapa
 * @param modNanos      tempo médio do mod por tick (todas as redes)
 * @param budgetNanos   teto atual do orçamento por tick
 * @param query         a consulta que esta página responde
 * @param totalNodes    nós visíveis ao jogador, sem filtro
 * @param matchingNodes nós que passam na consulta (a página é um pedaço deles)
 * @param notice        aviso da última ação (vazio se não houver)
 * @param noticeId      muda a cada aviso novo, para a tela mostrá-lo de novo
 */
public record TabletSnapshot(
        boolean operator,
        ResourceLocation dimension,
        BlockPos center,
        Optional<UUID> activeNetwork,
        long modNanos,
        long budgetNanos,
        Query query,
        int totalNodes,
        int matchingNodes,
        List<NetworkView> networks,
        List<GroupView> groups,
        List<NodeView> nodes,
        Component notice,
        int noticeId) {

    public static final int PAGE_SIZE = 100;
    /** Tamanho máximo do texto da busca. */
    public static final int MAX_SEARCH = 64;

    /** Filtro por papel da lista. */
    public enum RoleFilter {
        ALL, EXTRACT, INSERT, STORAGE, PROBLEM
    }

    /** Estado de um nó, na ordem de prioridade (o primeiro que valer). */
    public enum NodeStatus {
        /** Chunk descarregado: o nó não está no motor. */
        UNLOADED,
        /** Nenhuma aba numa rede que exista. */
        NO_NETWORK,
        /** Todas as redes do nó estão em grupos pausados. */
        PAUSED,
        /** Algum destino do nó recusou várias vezes seguidas e dorme. */
        FULL,
        /** Moveu algo na última amostra. */
        ACTIVE,
        IDLE
    }

    /** Busca (nome, máquina, rede ou coordenadas), papel e página. */
    public record Query(String search, RoleFilter role, int page) {
        public static final Query DEFAULT = new Query("", RoleFilter.ALL, 0);
        public static final StreamCodec<RegistryFriendlyByteBuf, Query> STREAM_CODEC = StreamCodec.of(
                (buf, q) -> {
                    buf.writeUtf(q.search, MAX_SEARCH);
                    buf.writeEnum(q.role);
                    buf.writeVarInt(q.page);
                },
                buf -> new Query(buf.readUtf(MAX_SEARCH), buf.readEnum(RoleFilter.class), buf.readVarInt()));

        public Query {
            search = search.length() > MAX_SEARCH ? search.substring(0, MAX_SEARCH) : search;
            page = Math.max(0, page);
        }
    }

    /**
     * Uma rede visível. Vazões da última amostra: itens/s, mB/s e FE/t; o tempo e as operações vêm
     * do profiler do motor ({@code /wa profile}).
     *
     * @param manageable o jogador pode renomear, mudar cor e privacidade e remover (dono ou op)
     * @param unloaded   nós da rede em chunks descarregados
     * @param full       destinos cheios (dormindo depois de recusas seguidas)
     * @param sleeping   destinos dormindo, cheios ou não
     */
    public record NetworkView(UUID id, String name, int color, String owner, boolean owned, boolean manageable,
            boolean isPublic, boolean paused, int nodes, int unloaded, int full, int sleeping, long averageNanos,
            int opsPerSecond, long itemRate, long fluidRate, long energyRate) {
    }

    /** Um grupo de redes do jogador (ou de qualquer um, para operador). */
    public record GroupView(UUID id, String name, String owner, boolean manageable, boolean paused,
            List<UUID> networks) {
    }

    /**
     * Um nó da página.
     *
     * @param networks rede de cada aba, por {@link ResourceType#ordinal()}
     * @param roles    papéis em bits ({@code NodeIndex.role})
     * @param machine  bloco da máquina, para o ícone e o nome
     */
    public record NodeView(NodeKey key, String name, ResourceLocation machine, RouterTier tier,
            List<Optional<UUID>> networks, int roles, NodeStatus status) {
        public Optional<UUID> network(ResourceType type) {
            return networks.get(type.ordinal());
        }
    }

    public Optional<NetworkView> network(UUID id) {
        for (NetworkView view : networks) {
            if (view.id.equals(id)) {
                return Optional.of(view);
            }
        }
        return Optional.empty();
    }

    /** Páginas da consulta (pelo menos 1). */
    public int pages() {
        return Math.max(1, (matchingNodes + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TabletSnapshot> STREAM_CODEC =
            StreamCodec.of(TabletSnapshot::encode, TabletSnapshot::decode);

    private static void encode(RegistryFriendlyByteBuf buf, TabletSnapshot s) {
        buf.writeBoolean(s.operator);
        buf.writeResourceLocation(s.dimension);
        buf.writeBlockPos(s.center);
        buf.writeOptional(s.activeNetwork, (b, id) -> b.writeUUID(id));
        buf.writeVarLong(s.modNanos);
        buf.writeVarLong(s.budgetNanos);
        Query.STREAM_CODEC.encode(buf, s.query);
        buf.writeVarInt(s.totalNodes);
        buf.writeVarInt(s.matchingNodes);
        buf.writeVarInt(s.networks.size());
        for (NetworkView n : s.networks) {
            buf.writeUUID(n.id);
            buf.writeUtf(n.name, 64);
            buf.writeInt(n.color);
            buf.writeUtf(n.owner, 64);
            buf.writeBoolean(n.owned);
            buf.writeBoolean(n.manageable);
            buf.writeBoolean(n.isPublic);
            buf.writeBoolean(n.paused);
            buf.writeVarInt(n.nodes);
            buf.writeVarInt(n.unloaded);
            buf.writeVarInt(n.full);
            buf.writeVarInt(n.sleeping);
            buf.writeVarLong(n.averageNanos);
            buf.writeVarInt(n.opsPerSecond);
            buf.writeVarLong(n.itemRate);
            buf.writeVarLong(n.fluidRate);
            buf.writeVarLong(n.energyRate);
        }
        buf.writeVarInt(s.groups.size());
        for (GroupView g : s.groups) {
            buf.writeUUID(g.id);
            buf.writeUtf(g.name, 64);
            buf.writeUtf(g.owner, 64);
            buf.writeBoolean(g.manageable);
            buf.writeBoolean(g.paused);
            buf.writeVarInt(g.networks.size());
            for (UUID id : g.networks) {
                buf.writeUUID(id);
            }
        }
        buf.writeVarInt(s.nodes.size());
        for (NodeView n : s.nodes) {
            NodeKey.STREAM_CODEC.encode(buf, n.key);
            buf.writeUtf(n.name, 64);
            buf.writeResourceLocation(n.machine);
            buf.writeEnum(n.tier);
            for (Optional<UUID> network : n.networks) {
                buf.writeOptional(network, (b, id) -> b.writeUUID(id));
            }
            buf.writeVarInt(n.roles);
            buf.writeEnum(n.status);
        }
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, s.notice);
        buf.writeVarInt(s.noticeId);
    }

    private static TabletSnapshot decode(RegistryFriendlyByteBuf buf) {
        boolean operator = buf.readBoolean();
        ResourceLocation dimension = buf.readResourceLocation();
        BlockPos center = buf.readBlockPos();
        Optional<UUID> active = buf.readOptional(b -> b.readUUID());
        long modNanos = buf.readVarLong();
        long budgetNanos = buf.readVarLong();
        Query query = Query.STREAM_CODEC.decode(buf);
        int total = buf.readVarInt();
        int matching = buf.readVarInt();
        int networkCount = buf.readVarInt();
        List<NetworkView> networks = new ArrayList<>(networkCount);
        for (int i = 0; i < networkCount; i++) {
            networks.add(new NetworkView(buf.readUUID(), buf.readUtf(64), buf.readInt(), buf.readUtf(64),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(),
                    buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
        }
        int groupCount = buf.readVarInt();
        List<GroupView> groups = new ArrayList<>(groupCount);
        for (int i = 0; i < groupCount; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(64);
            String owner = buf.readUtf(64);
            boolean manageable = buf.readBoolean();
            boolean paused = buf.readBoolean();
            int count = buf.readVarInt();
            List<UUID> members = new ArrayList<>(count);
            for (int j = 0; j < count; j++) {
                members.add(buf.readUUID());
            }
            groups.add(new GroupView(id, name, owner, manageable, paused, List.copyOf(members)));
        }
        int nodeCount = buf.readVarInt();
        List<NodeView> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            NodeKey key = NodeKey.STREAM_CODEC.decode(buf);
            String name = buf.readUtf(64);
            ResourceLocation machine = buf.readResourceLocation();
            RouterTier tier = buf.readEnum(RouterTier.class);
            List<Optional<UUID>> typeNetworks = new ArrayList<>(ResourceType.values().length);
            for (int t = 0; t < ResourceType.values().length; t++) {
                typeNetworks.add(buf.readOptional(b -> b.readUUID()));
            }
            nodes.add(new NodeView(key, name, machine, tier, List.copyOf(typeNetworks), buf.readVarInt(),
                    buf.readEnum(NodeStatus.class)));
        }
        Component notice = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        int noticeId = buf.readVarInt();
        return new TabletSnapshot(operator, dimension, center, active, modNanos, budgetNanos, query, total, matching,
                List.copyOf(networks), List.copyOf(groups), List.copyOf(nodes), notice, noticeId);
    }
}
