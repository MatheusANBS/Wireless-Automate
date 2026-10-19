package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff.ProfileMatch;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSource;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Tudo que a tela do Tablet mostra, montado no servidor só com ela aberta e só com o que o jogador
 * pode ver (as redes dele e as públicas; todas para operador). Os nós são o resultado da consulta
 * ({@link Query}): no máximo {@link #MAX_NODES}, ordenados pelo id da máquina e depois pelo nome, e
 * {@code matchingNodes} diz quantos casaram, para nenhum pacote levar milhares de nós.
 *
 * <p>Vai ao cliente em duas partes, cada uma só quando muda: o cabeçalho ({@link #HEADER_CODEC}:
 * redes, grupos, facetas, contagens dos filtros rápidos e aviso, pequeno) e a página de nós ({@link Page},
 * a parte grande). O buffer de abertura leva as duas
 * ({@link #STREAM_CODEC}).
 *
 * @param dimension     dimensão do jogador, para o mapa
 * @param query         a consulta que esta página responde
 * @param totalNodes    nós visíveis ao jogador, sem filtro
 * @param matchingNodes nós que passam na consulta (os nós enviados são os {@link #MAX_NODES} primeiros)
 * @param machineFacets as máquinas dos nós visíveis, com quantos nós cada uma tem (sem olhar a consulta)
 * @param networkFacets as redes visíveis das faces dos nós visíveis, com quantos nós cada uma tem
 * @param quickCounts   quantos nós visíveis cada filtro rápido pega
 * @param profile       o perfil guardado no Tablet do jogador, vazio sem perfil
 * @param rows          as linhas da árvore abaixo dos nós abertos ({@link TabletRows}); elas e os nós dividem o
 *                      teto de {@link #MAX_NODES}
 * @param groupCounts   quantos nós de cada máquina passam na consulta (não só os enviados), na ordem da lista
 * @param originGroup   a máquina atual do nó de origem do perfil, se ele passa na consulta
 * @param notice        aviso da última ação (vazio se não houver)
 * @param noticeId      muda a cada aviso novo, para a tela mostrá-lo de novo
 */
public record TabletSnapshot(
        boolean operator,
        ResourceLocation dimension,
        Optional<UUID> activeNetwork,
        Query query,
        int totalNodes,
        int matchingNodes,
        List<NetworkView> networks,
        List<GroupView> groups,
        List<Facet<ResourceLocation>> machineFacets,
        List<Facet<UUID>> networkFacets,
        QuickCounts quickCounts,
        Optional<ProfileSummary> profile,
        List<NodeView> nodes,
        TabletRows rows,
        List<Facet<ResourceLocation>> groupCounts,
        Optional<ResourceLocation> originGroup,
        Component notice,
        int noticeId) {

    /** Linhas enviadas por consulta: os nós e as linhas da árvore deles. */
    public static final int MAX_NODES = 256;
    /** Tamanho máximo do texto da busca. */
    public static final int MAX_TEXT = 64;
    /** Máquinas que o texto pode pegar pelo nome (resolvido no cliente). */
    public static final int MAX_TEXT_MACHINES = 32;
    /** Etiquetas de máquina e de rede de uma consulta, cada. */
    public static final int MAX_LABELS = 8;

    /** Filtro rápido da lista; os ligados valem todos ao mesmo tempo. */
    public enum QuickFilter {
        /** Descarregado, sem rede ou com destino cheio. */
        PROBLEM,
        /** Nenhuma face numa rede que exista. */
        NO_NETWORK,
        /** Chunk descarregado. */
        UNLOADED
    }

    /** Quantos nós visíveis cada {@link QuickFilter} pega. */
    public record QuickCounts(int problem, int noNetwork, int unloaded) {
        public static final QuickCounts EMPTY = new QuickCounts(0, 0, 0);

        public int count(QuickFilter filter) {
            return switch (filter) {
                case PROBLEM -> problem;
                case NO_NETWORK -> noNetwork;
                case UNLOADED -> unloaded;
            };
        }
    }

    /**
     * O resumo do perfil guardado no Tablet, para a faixa da Lista.
     *
     * @param sourceName nome do nó de onde o perfil veio
     * @param machine    bloco da máquina de origem
     * @param origin     o nó de origem, que o colar deixa de fora
     * @param types      os tipos carregados que têm alguma face com modo
     * @param faces      quantas faces (tipo × lado) dos tipos carregados têm modo
     */
    public record ProfileSummary(String sourceName, ResourceLocation machine, NodeKey origin, List<ResourceType> types,
            int faces) {
    }

    /** Um valor (uma máquina, uma rede) e quantos nós visíveis o têm. */
    public record Facet<T>(T id, int count) {
    }

    /** Estado de um nó, na ordem de prioridade (o primeiro que valer). */
    public enum NodeStatus {
        /** Chunk descarregado: o nó não está no motor. */
        UNLOADED,
        /** Nenhuma face numa rede que exista. */
        NO_NETWORK,
        /** Todas as redes do nó estão em grupos pausados. */
        PAUSED,
        /** Algum destino do nó recusou várias vezes seguidas e dorme. */
        FULL,
        /** Moveu algo na última amostra. */
        ACTIVE,
        IDLE
    }

    /**
     * A consulta da lista. Um nó casa se passa em todas as etiquetas, no tipo, nos filtros rápidos e no texto.
     *
     * @param text         palavras que casam, todas, com o nome, o id da máquina, as coordenadas ou o nome de uma
     *                     rede do nó; vazio passa tudo
     * @param textMachines ids de máquinas cujo nome (no idioma do cliente) casa com o texto: o nó delas casa o
     *                     texto mesmo sem as palavras aparecerem no resto
     * @param machines     etiquetas de máquina: o id da máquina do nó está na lista (vazia passa tudo)
     * @param networks     etiquetas de rede: alguma face do nó está numa delas (vazia passa tudo)
     * @param quick        filtros rápidos ligados
     * @param type         só nós com algum papel nesse tipo
     */
    public record Query(String text, List<ResourceLocation> textMachines, List<ResourceLocation> machines,
            List<UUID> networks, Set<QuickFilter> quick, Optional<ResourceType> type) {
        public static final Query DEFAULT = new Query("", List.of(), List.of(), List.of(), Set.of(), Optional.empty());
        public static final StreamCodec<RegistryFriendlyByteBuf, Query> STREAM_CODEC =
                StreamCodec.of(Query::encode, Query::decode);

        public Query {
            text = text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
            textMachines = capped(textMachines, MAX_TEXT_MACHINES);
            machines = capped(machines, MAX_LABELS);
            networks = capped(networks, MAX_LABELS);
            quick = Set.copyOf(quick);
        }

        private static <T> List<T> capped(List<T> list, int max) {
            return List.copyOf(list.size() > max ? list.subList(0, max) : list);
        }

        /** A consulta que só procura {@code text}. */
        public static Query of(String text) {
            return DEFAULT.withText(text);
        }

        public Query withText(String text) {
            return new Query(text, textMachines, machines, networks, quick, type);
        }

        public Query withTextMachines(List<ResourceLocation> textMachines) {
            return new Query(text, textMachines, machines, networks, quick, type);
        }

        public Query withMachines(List<ResourceLocation> machines) {
            return new Query(text, textMachines, machines, networks, quick, type);
        }

        public Query withNetworks(List<UUID> networks) {
            return new Query(text, textMachines, machines, networks, quick, type);
        }

        public Query withQuick(Set<QuickFilter> quick) {
            return new Query(text, textMachines, machines, networks, quick, type);
        }

        public Query withType(Optional<ResourceType> type) {
            return new Query(text, textMachines, machines, networks, quick, type);
        }

        private static void encode(RegistryFriendlyByteBuf buf, Query q) {
            buf.writeUtf(q.text, MAX_TEXT);
            writeIds(buf, q.textMachines);
            writeIds(buf, q.machines);
            buf.writeVarInt(q.networks.size());
            for (UUID id : q.networks) {
                buf.writeUUID(id);
            }
            int bits = 0;
            for (QuickFilter filter : q.quick) {
                bits |= 1 << filter.ordinal();
            }
            buf.writeVarInt(bits);
            buf.writeVarInt(q.type.map(t -> t.ordinal() + 1).orElse(0));
        }

        private static Query decode(RegistryFriendlyByteBuf buf) {
            String text = buf.readUtf(MAX_TEXT);
            List<ResourceLocation> textMachines = readIds(buf, MAX_TEXT_MACHINES);
            List<ResourceLocation> machines = readIds(buf, MAX_LABELS);
            int count = buf.readVarInt();
            if (count > MAX_LABELS) {
                throw new DecoderException("networks " + count + " > " + MAX_LABELS);
            }
            List<UUID> networks = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                networks.add(buf.readUUID());
            }
            int bits = buf.readVarInt();
            Set<QuickFilter> quick = EnumSet.noneOf(QuickFilter.class);
            for (QuickFilter filter : QuickFilter.values()) {
                if ((bits & (1 << filter.ordinal())) != 0) {
                    quick.add(filter);
                }
            }
            int t = buf.readVarInt();
            Optional<ResourceType> type = t <= 0 || t > ResourceType.values().length
                    ? Optional.empty() : Optional.of(ResourceType.values()[t - 1]);
            return new Query(text, textMachines, machines, networks, quick, type);
        }

        private static void writeIds(RegistryFriendlyByteBuf buf, List<ResourceLocation> ids) {
            buf.writeVarInt(ids.size());
            for (ResourceLocation id : ids) {
                buf.writeResourceLocation(id);
            }
        }

        private static List<ResourceLocation> readIds(RegistryFriendlyByteBuf buf, int max) {
            int count = buf.readVarInt();
            if (count > max) {
                throw new DecoderException("ids " + count + " > " + max);
            }
            List<ResourceLocation> ids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ids.add(buf.readResourceLocation());
            }
            return ids;
        }
    }

    /**
     * Uma rede visível.
     *
     * @param manageable o jogador pode renomear, mudar cor e privacidade e remover (dono ou op)
     * @param unloaded   nós da rede em chunks descarregados
     */
    public record NetworkView(UUID id, String name, int color, String owner, boolean owned, boolean manageable,
            boolean isPublic, boolean paused, int nodes, int unloaded) {
    }

    /** Um grupo de redes do jogador (ou de qualquer um, para operador). */
    public record GroupView(UUID id, String name, String owner, boolean manageable, boolean paused,
            List<UUID> networks) {
    }

    /**
     * Um nó da lista.
     *
     * @param networks       por {@link ResourceType#ordinal()}, as redes distintas das faces do tipo
     *                       (presa, perfis e exceções), só as que ainda existem
     * @param roles          papéis em bits ({@code NodeIndex.role})
     * @param machine        bloco da máquina, para o ícone e o nome
     * @param removedNetwork alguma face aponta para uma rede que não existe mais
     * @param faceModes      os modos das faces pelos lados, da presa, dos perfis e das exceções ({@link NodeIndex.Entry#faceModes})
     * @param rates          a vazão do nó por {@link ResourceType#ordinal()} na última amostra, na unidade da tela
     * @param profile        a comparação com o perfil copiado no Tablet; vazio sem perfil copiado
     */
    public record NodeView(NodeKey key, String name, ResourceLocation machine, RouterTier tier,
            List<List<UUID>> networks, int roles, NodeStatus status, boolean removedNetwork, long faceModes,
            List<Long> rates, Optional<ProfileMatch> profile) {
        /** As redes das faces do tipo {@code type}. */
        public List<UUID> networks(ResourceType type) {
            return networks.get(type.ordinal());
        }

        /** O modo da face do lado {@code side} da máquina, do tipo {@code type}. */
        public PortMode faceMode(ResourceType type, RemoteSide side) {
            return NodeIndex.faceMode(faceModes, type, side);
        }

        /** A vazão do tipo na última amostra (0 sem amostra). */
        public long rate(ResourceType type) {
            return type.ordinal() < rates.size() ? rates.get(type.ordinal()) : 0;
        }

        /** O mesmo nó com outro estado e outra vazão. */
        public NodeView withLive(Live live) {
            return new NodeView(key, name, machine, tier, networks, roles, live.status(), removedNetwork, faceModes,
                    live.rates(), profile);
        }

        /** Igual a {@code other} fora o estado e a vazão. */
        boolean sameExceptLive(NodeView other) {
            return key.equals(other.key) && name.equals(other.name) && machine.equals(other.machine)
                    && tier == other.tier && networks.equals(other.networks) && roles == other.roles
                    && removedNetwork == other.removedNetwork && faceModes == other.faceModes
                    && profile.equals(other.profile);
        }

        /** Redes distintas somando os tipos carregados (uma rede em dois tipos conta uma vez), como a dica. */
        public int distinctNetworks() {
            return (int) LoadedTypes.LIST.stream().flatMap(type -> networks(type).stream()).distinct().count();
        }
    }

    /**
     * A página de nós: a consulta que ela responde, os totais, os nós e as linhas da árvore (juntos, no máximo
     * {@link #MAX_NODES}).
     *
     * @param totalNodes    nós visíveis ao jogador, sem filtro
     * @param matchingNodes nós que passam na consulta
     * @param groupCounts   quantos nós de cada máquina passam na consulta
     * @param originGroup   a máquina atual do nó de origem do perfil, se ele passa na consulta
     */
    public record Page(Query query, int totalNodes, int matchingNodes, List<NodeView> nodes, TabletRows rows,
            List<Facet<ResourceLocation>> groupCounts, Optional<ResourceLocation> originGroup) {
        /** Lugar da página num cabeçalho recebido sozinho (o cliente põe a página que já tinha). */
        public static final Page EMPTY = new Page(Query.DEFAULT, 0, 0, List.of(), TabletRows.EMPTY, List.of(),
                Optional.empty());
        public static final StreamCodec<RegistryFriendlyByteBuf, Page> STREAM_CODEC =
                StreamCodec.of(TabletPageCodec::encodePage, TabletPageCodec::decodePage);

        /**
         * Igual a {@code other} fora o estado e a vazão dos nós e das linhas de máquina: a mudança cabe no pacote
         * pequeno ({@link Live}).
         */
        public boolean sameExceptLive(Page other) {
            if (totalNodes != other.totalNodes || matchingNodes != other.matchingNodes || nodes.size() != other.nodes.size()
                    || !query.equals(other.query) || !groupCounts.equals(other.groupCounts)
                    || !originGroup.equals(other.originGroup) || !rows.sameExceptLive(other.rows)) {
                return false;
            }
            for (int i = 0; i < nodes.size(); i++) {
                if (!nodes.get(i).sameExceptLive(other.nodes.get(i))) {
                    return false;
                }
            }
            return true;
        }

        /** O estado e a vazão de cada nó, na ordem. */
        public List<Live> lives() {
            List<Live> lives = new ArrayList<>(nodes.size());
            for (NodeView node : nodes) {
                lives.add(new Live(node.status(), node.rates()));
            }
            return lives;
        }

        /** A mesma página com o estado e a vazão de {@code lives}; ela mesma se o número de nós não bate. */
        public Page withLives(List<Live> lives) {
            return withLives(lives, rows.lives());
        }

        /**
         * A mesma página com o estado e a vazão dos nós ({@code lives}) e das linhas de máquina ({@code machines});
         * ela mesma se o número de nós não bate.
         */
        public Page withLives(List<Live> lives, List<TabletRows.MachineLive> machines) {
            if (lives.size() != nodes.size()) {
                return this;
            }
            List<NodeView> updated = new ArrayList<>(nodes.size());
            for (int i = 0; i < nodes.size(); i++) {
                updated.add(nodes.get(i).withLive(lives.get(i)));
            }
            return new Page(query, totalNodes, matchingNodes, List.copyOf(updated), rows.withLives(machines), groupCounts,
                    originGroup);
        }
    }

    public Page page() {
        return new Page(query, totalNodes, matchingNodes, nodes, rows, groupCounts, originGroup);
    }

    /** O mesmo cabeçalho com outra página. */
    public TabletSnapshot withPage(Page page) {
        return new TabletSnapshot(operator, dimension, activeNetwork, page.query(),
                page.totalNodes(), page.matchingNodes(), networks, groups, machineFacets, networkFacets, quickCounts, profile,
                page.nodes(), page.rows(), page.groupCounts(), page.originGroup(), notice, noticeId);
    }

    /** O cabeçalho igual ao de {@code other} (tudo menos a página). */
    public boolean sameHeader(TabletSnapshot other) {
        return withPage(Page.EMPTY).equals(other.withPage(Page.EMPTY));
    }

    /** Quantos nós da máquina passam na consulta (0 se nenhum). */
    public int groupCount(ResourceLocation machine) {
        for (Facet<ResourceLocation> facet : groupCounts) {
            if (facet.id().equals(machine)) {
                return facet.count();
            }
        }
        return 0;
    }

    public Optional<NetworkView> network(UUID id) {
        for (NetworkView view : networks) {
            if (view.id.equals(id)) {
                return Optional.of(view);
            }
        }
        return Optional.empty();
    }

    /** Snapshot inteiro (cabeçalho e página), para o buffer de abertura. */
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                encodeHeader(buf, s);
                TabletPageCodec.encodePage(buf, s.page());
            },
            buf -> decodeHeader(buf).withPage(TabletPageCodec.decodePage(buf)));
    /** Só o cabeçalho; decodificado com {@link Page#EMPTY} no lugar da página. */
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletSnapshot> HEADER_CODEC =
            StreamCodec.of(TabletSnapshot::encodeHeader, TabletSnapshot::decodeHeader);

    private static void encodeHeader(RegistryFriendlyByteBuf buf, TabletSnapshot s) {
        buf.writeBoolean(s.operator);
        buf.writeResourceLocation(s.dimension);
        buf.writeOptional(s.activeNetwork, (b, id) -> b.writeUUID(id));
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
        buf.writeVarInt(s.machineFacets.size());
        for (Facet<ResourceLocation> facet : s.machineFacets) {
            buf.writeResourceLocation(facet.id());
            buf.writeVarInt(facet.count());
        }
        buf.writeVarInt(s.networkFacets.size());
        for (Facet<UUID> facet : s.networkFacets) {
            buf.writeUUID(facet.id());
            buf.writeVarInt(facet.count());
        }
        buf.writeVarInt(s.quickCounts.problem());
        buf.writeVarInt(s.quickCounts.noNetwork());
        buf.writeVarInt(s.quickCounts.unloaded());
        buf.writeOptional(s.profile, (b, p) -> {
            b.writeUtf(ProfileSource.clip(p.sourceName), ProfileSource.MAX_NAME);
            b.writeResourceLocation(p.machine);
            NodeKey.STREAM_CODEC.encode(b, p.origin);
            b.writeVarInt(p.types.size());
            p.types.forEach(type -> b.writeEnum(type));
            b.writeVarInt(p.faces);
        });
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, s.notice);
        buf.writeVarInt(s.noticeId);
    }

    private static TabletSnapshot decodeHeader(RegistryFriendlyByteBuf buf) {
        boolean operator = buf.readBoolean();
        ResourceLocation dimension = buf.readResourceLocation();
        Optional<UUID> active = buf.readOptional(b -> b.readUUID());
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
            networks.add(new NetworkView(id, name, color, owner, owned, manageable, isPublic, paused, nodes,
                    unloaded));
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
        int machineCount = buf.readVarInt();
        List<Facet<ResourceLocation>> machineFacets = new ArrayList<>(machineCount);
        for (int i = 0; i < machineCount; i++) {
            machineFacets.add(new Facet<>(buf.readResourceLocation(), buf.readVarInt()));
        }
        int facetCount = buf.readVarInt();
        List<Facet<UUID>> networkFacets = new ArrayList<>(facetCount);
        for (int i = 0; i < facetCount; i++) {
            networkFacets.add(new Facet<>(buf.readUUID(), buf.readVarInt()));
        }
        QuickCounts quickCounts = new QuickCounts(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        Optional<ProfileSummary> profile = buf.readOptional(b -> {
            String sourceName = b.readUtf(ProfileSource.MAX_NAME);
            ResourceLocation machine = b.readResourceLocation();
            NodeKey origin = NodeKey.STREAM_CODEC.decode(b);
            int typeCount = b.readVarInt();
            List<ResourceType> types = new ArrayList<>(typeCount);
            for (int i = 0; i < typeCount; i++) {
                types.add(b.readEnum(ResourceType.class));
            }
            return new ProfileSummary(sourceName, machine, origin, List.copyOf(types), b.readVarInt());
        });
        Component notice = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        int noticeId = buf.readVarInt();
        return new TabletSnapshot(operator, dimension, active, Page.EMPTY.query(),
                0, 0, List.copyOf(networks), List.copyOf(groups), List.copyOf(machineFacets),
                List.copyOf(networkFacets), quickCounts, profile, List.of(), TabletRows.EMPTY, List.of(), Optional.empty(),
                notice, noticeId);
    }

    /**
     * O que muda a cada amostra num nó da página: o estado e a vazão por tipo. Quando só isso muda, o Tablet manda
     * só estes ({@link #LIVE_CODEC}), na ordem da página, em vez da página inteira.
     */
    public record Live(NodeStatus status, List<Long> rates) {
    }

    /** O estado e a vazão de cada nó da página, na ordem dela. */
    public static final StreamCodec<RegistryFriendlyByteBuf, List<Live>> LIVE_CODEC = StreamCodec.of(
            (buf, lives) -> {
                buf.writeVarInt(lives.size());
                for (Live live : lives) {
                    buf.writeEnum(live.status());
                    for (int t = 0; t < ResourceType.values().length; t++) {
                        buf.writeVarLong(t < live.rates().size() ? live.rates().get(t) : 0);
                    }
                }
            },
            buf -> {
                int count = TabletPageCodec.readCount(buf, MAX_NODES);
                List<Live> lives = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    lives.add(TabletPageCodec.readLive(buf));
                }
                return List.copyOf(lives);
            });
}
