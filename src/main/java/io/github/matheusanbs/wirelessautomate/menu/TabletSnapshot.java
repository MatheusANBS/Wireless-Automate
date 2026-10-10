package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.net.GameCodecs;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Tudo que a tela do Tablet mostra, montado no servidor só com ela aberta e só com o que o jogador
 * pode ver (as redes dele e as públicas; todas para operador). Os nós vêm em páginas de
 * {@link #PAGE_SIZE}, já filtrados pela busca e pelo papel ({@link Query}) e ordenados pela
 * distância ao jogador (os de outras dimensões no fim), para nenhum pacote levar milhares de nós.
 *
 * <p>Vai ao cliente em duas partes, cada uma só quando muda: o cabeçalho ({@link #HEADER_CODEC}:
 * estatísticas, redes, grupos e aviso, pequeno e mutável a cada amostra) e a página de nós
 * ({@link Page}, a parte grande). O buffer de abertura leva as duas ({@link #STREAM_CODEC}).
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
    public record Query(String search, RoleFilter role, Optional<ResourceType> type, int page) {
        public static final Query DEFAULT = new Query("", RoleFilter.ALL, Optional.empty(), 0);
        public static final StreamCodec<RegistryFriendlyByteBuf, Query> STREAM_CODEC = StreamCodec.of(
                (buf, q) -> {
                    buf.writeUtf(q.search, MAX_SEARCH);
                    buf.writeEnum(q.role);
                    buf.writeVarInt(q.type().map(t -> t.ordinal() + 1).orElse(0));
                    buf.writeVarInt(q.page);
                },
                buf -> {
                    String search = buf.readUtf(MAX_SEARCH);
                    RoleFilter role = buf.readEnum(RoleFilter.class);
                    int t = buf.readVarInt();
                    Optional<ResourceType> type = t <= 0 || t > ResourceType.values().length
                            ? Optional.empty() : Optional.of(ResourceType.values()[t - 1]);
                    return new Query(search, role, type, buf.readVarInt());
                });

        public Query {
            search = search.length() > MAX_SEARCH ? search.substring(0, MAX_SEARCH) : search;
            page = Math.max(0, page);
        }
    }

    /**
     * Uma rede visível. Vazões da última amostra: itens/s, mB/s, FE/t e mB/s de químico; o tempo e as operações vêm
     * do profiler do motor ({@code /wa profile}).
     *
     * @param manageable o jogador pode renomear, mudar cor e privacidade e remover (dono ou op)
     * @param unloaded   nós da rede em chunks descarregados
     * @param full       destinos cheios (dormindo depois de recusas seguidas)
     * @param sleeping   destinos dormindo, cheios ou não
     */
    public record NetworkView(UUID id, String name, int color, String owner, boolean owned, boolean manageable,
            boolean isPublic, boolean paused, int nodes, int unloaded, int full, int sleeping, long averageNanos,
            int opsPerSecond, List<TypeStats> types) {
        /** Por {@link ResourceType#ordinal()}. */
        public TypeStats type(ResourceType type) {
            return type.ordinal() < types.size() ? types.get(type.ordinal()) : TypeStats.EMPTY;
        }
    }

    /** Vazão e portas de um tipo numa rede; vazão na unidade da tela (itens/s, mB/s, FE/t). */
    public record TypeStats(long rate, int sources, int destinations, int sleeping) {
        public static final TypeStats EMPTY = new TypeStats(0, 0, 0, 0);
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

    /**
     * A página de nós: a consulta que ela responde, os totais e os nós.
     *
     * @param totalNodes    nós visíveis ao jogador, sem filtro
     * @param matchingNodes nós que passam na consulta
     */
    public record Page(Query query, int totalNodes, int matchingNodes, List<NodeView> nodes) {
        /** Lugar da página num cabeçalho recebido sozinho (o cliente põe a página que já tinha). */
        public static final Page EMPTY = new Page(Query.DEFAULT, 0, 0, List.of());
        public static final StreamCodec<RegistryFriendlyByteBuf, Page> STREAM_CODEC =
                StreamCodec.of(TabletSnapshot::encodePage, TabletSnapshot::decodePage);
    }

    public Page page() {
        return new Page(query, totalNodes, matchingNodes, nodes);
    }

    /** O mesmo cabeçalho com outra página. */
    public TabletSnapshot withPage(Page page) {
        return new TabletSnapshot(operator, dimension, center, activeNetwork, modNanos, budgetNanos, page.query(),
                page.totalNodes(), page.matchingNodes(), networks, groups, page.nodes(), notice, noticeId);
    }

    /**
     * Porte 1.20.1: a versão da abertura quando a inteira passa do teto do Forge ({@code ServerMenus}). Sem as
     * redes, os grupos (as listas não têm limite) e a página; o resto tem tamanho limitado. O cabeçalho inteiro e a
     * página chegam logo depois ({@code TabletHeaderPayload}, {@code TabletPagePayload}).
     */
    public TabletSnapshot reduced() {
        return new TabletSnapshot(operator, dimension, center, activeNetwork, modNanos, budgetNanos, Page.EMPTY.query(),
                0, 0, List.of(), List.of(), List.of(), notice, noticeId);
    }

    /** O cabeçalho igual ao de {@code other} (tudo menos a página). */
    public boolean sameHeader(TabletSnapshot other) {
        return withPage(Page.EMPTY).equals(other.withPage(Page.EMPTY));
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

    /** Snapshot inteiro (cabeçalho e página), para o buffer de abertura. */
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                encodeHeader(buf, s);
                encodePage(buf, s.page());
            },
            buf -> decodeHeader(buf).withPage(decodePage(buf)));
    /** Só o cabeçalho; decodificado com {@link Page#EMPTY} no lugar da página. */
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletSnapshot> HEADER_CODEC =
            StreamCodec.of(TabletSnapshot::encodeHeader, TabletSnapshot::decodeHeader);

    private static void encodeHeader(RegistryFriendlyByteBuf buf, TabletSnapshot s) {
        buf.writeBoolean(s.operator);
        buf.writeResourceLocation(s.dimension);
        buf.writeBlockPos(s.center);
        buf.writeOptional(s.activeNetwork, (b, id) -> b.writeUUID(id));
        buf.writeVarLong(s.modNanos);
        buf.writeVarLong(s.budgetNanos);
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
            buf.writeVarInt(n.types().size());
            for (TypeStats t : n.types()) {
                buf.writeVarLong(t.rate());
                buf.writeVarInt(t.sources());
                buf.writeVarInt(t.destinations());
                buf.writeVarInt(t.sleeping());
            }
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
        GameCodecs.COMPONENT.encode(buf, s.notice);
        buf.writeVarInt(s.noticeId);
    }

    private static void encodePage(RegistryFriendlyByteBuf buf, Page page) {
        Query.STREAM_CODEC.encode(buf, page.query);
        buf.writeVarInt(page.totalNodes);
        buf.writeVarInt(page.matchingNodes);
        buf.writeVarInt(page.nodes.size());
        for (NodeView n : page.nodes) {
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
    }

    private static TabletSnapshot decodeHeader(RegistryFriendlyByteBuf buf) {
        boolean operator = buf.readBoolean();
        ResourceLocation dimension = buf.readResourceLocation();
        BlockPos center = buf.readBlockPos();
        Optional<UUID> active = buf.readOptional(b -> b.readUUID());
        long modNanos = buf.readVarLong();
        long budgetNanos = buf.readVarLong();
        int networkCount = buf.readVarInt();
        List<NetworkView> networks = new ArrayList<>(networkCount);
        for (int i = 0; i < networkCount; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(64);
            int color = buf.readInt();
            String owner = buf.readUtf(64);
            boolean owned = buf.readBoolean();
            boolean manageable = buf.readBoolean();
            boolean isPublic = buf.readBoolean();
            boolean paused = buf.readBoolean();
            int nodes = buf.readVarInt();
            int unloaded = buf.readVarInt();
            int full = buf.readVarInt();
            int sleeping = buf.readVarInt();
            long averageNanos = buf.readVarLong();
            int opsPerSecond = buf.readVarInt();
            int typeCount = buf.readVarInt();
            List<TypeStats> types = new ArrayList<>(typeCount);
            for (int t = 0; t < typeCount; t++) {
                types.add(new TypeStats(buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            networks.add(new NetworkView(id, name, color, owner, owned, manageable, isPublic, paused, nodes,
                    unloaded, full, sleeping, averageNanos, opsPerSecond, List.copyOf(types)));
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
        Component notice = GameCodecs.COMPONENT.decode(buf);
        int noticeId = buf.readVarInt();
        return new TabletSnapshot(operator, dimension, center, active, modNanos, budgetNanos, Page.EMPTY.query(),
                0, 0, List.copyOf(networks), List.copyOf(groups), List.of(), notice, noticeId);
    }

    private static Page decodePage(RegistryFriendlyByteBuf buf) {
        Query query = Query.STREAM_CODEC.decode(buf);
        int total = buf.readVarInt();
        int matching = buf.readVarInt();
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
        return new Page(query, total, matching, List.copyOf(nodes));
    }
}
