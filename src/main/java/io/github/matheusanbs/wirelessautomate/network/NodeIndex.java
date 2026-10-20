package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Índice persistente dos roteadores do mundo, para o Tablet listar também os que estão em chunks
 * descarregados. Fica no data storage do overworld.
 *
 * <p>Custo zero por tick: o roteador avisa só quando algo do índice pode ter mudado (carregou,
 * descarregou, mudou rede, faces, nome ou tier, foi colocado ou quebrado), e o índice só marca para
 * salvar se um dado salvo mudou de fato. Quem está carregado é guardado só em memória (a referência
 * ao block entity), então carregar e descarregar chunks não suja o arquivo.
 *
 * <p>O que fica salvo de cada nó: posição e dimensão, nome, tier, as redes distintas das faces de cada
 * tipo (presa, perfis e exceções, chave {@code face_networks}; a {@code networks} antiga, uma rede por
 * tipo, só é lida, para migrar), os papéis (um
 * resumo dos modos das faces por tipo, ver {@link #role}), os modos de cada face (chave {@code face_modes},
 * formato no {@link FaceSummary}; sem a chave, tudo Nenhum), os papéis e a máscara de redes da presa
 * ({@code attached_roles} e {@code attached_nets}, só diferentes de zero), o bloco da máquina, quem colocou e as
 * máquinas vinculadas ({@link IndexRemotes}, só com máquinas). Também guarda, só na memória, quantas máquinas a base
 * tem ({@link #machines()}).
 * Uma entrada pode ficar velha se o bloco sumir sem passar pelo {@code onRemove} (região apagada):
 * quando o chunk dela carrega, a entrada sem roteador ali sai ({@link #chunkLoaded}, pelo bloco do chunk que
 * acabou de carregar, sem carregar nenhum outro), e o Tablet também a apaga quando acha o chunk carregado sem
 * roteador.
 *
 * <p>Salvar: cada entrada guarda a própria {@link CompoundTag} até um dado dela mudar, então gravar o índice só
 * recodifica as entradas que mudaram; o arquivo continua um só (é um {@link SavedData}), no mesmo formato.
 */
public final class NodeIndex extends SavedData {
    public static final String DATA_NAME = "wirelessautomate_nodes";
    public static final SavedData.Factory<NodeIndex> FACTORY = new SavedData.Factory<>(NodeIndex::new, NodeIndex::load);

    /** Papéis de um tipo, em {@link #role}. */
    public static final int EXTRACT = 0;
    public static final int INSERT = 1;
    public static final int STORAGE = 2;
    private static final int ROLES_PER_TYPE = 3;
    private static final ResourceType[] TYPES = ResourceType.values();

    /** Dimensão e posição de um roteador. */
    public record NodeKey(ResourceKey<Level> dimension, BlockPos pos) {
        public static final StreamCodec<FriendlyByteBuf, NodeKey> STREAM_CODEC = StreamCodec.of(
                (buf, key) -> {
                    buf.writeResourceLocation(key.dimension.location());
                    buf.writeBlockPos(key.pos);
                },
                buf -> new NodeKey(ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation()),
                        buf.readBlockPos()));

        public static NodeKey of(Level level, BlockPos pos) {
            return new NodeKey(level.dimension(), pos.immutable());
        }
    }

    /** Um nó do índice. Só o índice muda os campos; quem lê roda na thread do servidor. */
    public static final class Entry {
        private final NodeKey key;
        private String name = "";
        /** Nome dado à máquina presa; vazio sem nome. */
        private String attachedLabel = "";
        private RouterTier tier = RouterTier.BASIC;
        /**
         * Por tipo, as redes distintas das faces ({@link RouterBlockEntity#networks}), em listas imutáveis que
         * {@link #networks(ResourceType)} devolve sem copiar; nunca nulo.
         */
        @SuppressWarnings("unchecked")
        private final List<UUID>[] networks = new List[TYPES.length];
        private int roles;
        private long faceModes;
        private int attachedRoles;
        private long attachedNets;
        /** Papéis e redes por máquina já lidos do roteador; falso na entrada lida do arquivo. Não é salvo. */
        private boolean machineRolesRead;
        /** As redes distintas na ordem dos bits ({@link NetworkOrder}), feita quando precisa. Não é salvo. */
        private @Nullable List<UUID> networkOrder;
        private ResourceLocation machine = BuiltInRegistries.BLOCK.getKey(net.minecraft.world.level.block.Blocks.AIR);
        private @Nullable UUID placedBy;
        private IndexRemotes remotes = IndexRemotes.EMPTY;
        /** O roteador carregado; não é salvo. */
        private @Nullable RouterBlockEntity router;
        /** Texto da busca do Tablet, feito na primeira busca e refeito só quando o nó muda; não é salvo. */
        private @Nullable String searchText;
        /** A entrada já codificada para salvar; {@code null} depois que um dado salvo muda. Não é alterada depois de pronta. */
        private @Nullable CompoundTag saved;

        Entry(NodeKey key) {
            this.key = key;
            Arrays.fill(networks, List.of());
        }

        public NodeKey key() {
            return key;
        }

        public String name() {
            return name;
        }

        /** Nome dado à máquina presa ({@link RouterBlockEntity#attachedLabel}); vazio sem nome. */
        public String attachedLabel() {
            return attachedLabel;
        }

        public RouterTier tier() {
            return tier;
        }

        /** As redes distintas das faces do tipo {@code type} (presa, perfis e exceções). Só leitura. */
        public List<UUID> networks(ResourceType type) {
            return networks[type.ordinal()];
        }

        /** Alguma face de algum tipo do nó está em {@code network}. */
        public boolean inNetwork(UUID network) {
            for (List<UUID> ids : networks) {
                for (UUID id : ids) {
                    if (network.equals(id)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** Alguma face de algum tipo está numa rede (que exista ou não). */
        public boolean hasNetwork() {
            for (List<UUID> ids : networks) {
                if (!ids.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        /** Redes distintas somando todos os tipos (uma rede em dois tipos conta uma vez). */
        public int distinctNetworks() {
            Set<UUID> distinct = new HashSet<>();
            for (List<UUID> ids : networks) {
                distinct.addAll(ids);
            }
            return distinct.size();
        }

        /** Papéis em bits, ver {@link NodeIndex#role}. */
        public int roles() {
            return roles;
        }

        /** O modo de cada lado, juntando presa, perfis e exceções, em bits (o formato está no {@link FaceSummary}). */
        public long faceModes() {
            return faceModes;
        }

        /** O modo da face do lado {@code side} da máquina, do tipo {@code type}. */
        public PortMode faceMode(ResourceType type, RemoteSide side) {
            return NodeIndex.faceMode(faceModes, type, side);
        }

        /** Os papéis só das faces da presa ({@link NodeIndex#role}). */
        public int attachedRoles() {
            return attachedRoles;
        }

        /** A máscara das redes das faces da presa ({@link NetworkOrder}). */
        public long attachedNets() {
            return attachedNets;
        }

        /**
         * Os papéis e as redes por máquina (presa, grupos e exceções) já vieram do roteador. Falso numa entrada lida do
         * arquivo até o próximo aviso dele: no formato da etapa 3a, sem as chaves, eles leem zero.
         */
        public boolean machineRolesRead() {
            return machineRolesRead;
        }

        /** As redes distintas do nó na ordem dos bits das máscaras ({@link NetworkOrder}). Só leitura. */
        public List<UUID> networkOrder() {
            if (networkOrder == null) {
                networkOrder = NetworkOrder.of(this);
            }
            return networkOrder;
        }

        /** Bloco da máquina onde o roteador está preso (ar se não se sabe). */
        public ResourceLocation machine() {
            return machine;
        }

        public @Nullable UUID placedBy() {
            return placedBy;
        }

        /** As máquinas vinculadas e os grupos, na ordem do roteador. Só leitura, sem cópia. */
        public IndexRemotes remotes() {
            return remotes;
        }

        /** O roteador, se o chunk está carregado. */
        public @Nullable RouterBlockEntity router() {
            return router != null && !router.isRemoved() ? router : null;
        }

        public boolean loaded() {
            return router() != null;
        }

        /**
         * Nome, nome da presa, máquina e coordenadas em minúsculas, separados por espaço, para a busca do Tablet.
         * Montado uma vez e guardado até um desses dados mudar.
         */
        public String searchText() {
            if (searchText == null) {
                searchText = (name + ' ' + attachedLabel + ' ' + machine + ' ' + key.pos.getX() + ' '
                        + key.pos.getY() + ' ' + key.pos.getZ()).toLowerCase(Locale.ROOT);
            }
            return searchText;
        }

        /** Copia o que o índice guarda do roteador. Devolve se algum dado salvo mudou. */
        private boolean read(RouterBlockEntity from, FaceSummary summary) {
            boolean changed = false;
            if (!name.equals(from.name())) {
                name = from.name();
                changed = true;
            }
            if (!attachedLabel.equals(from.attachedLabel())) {
                attachedLabel = from.attachedLabel();
                changed = true;
            }
            if (tier != from.tier()) {
                tier = from.tier();
                changed = true;
            }
            for (ResourceType type : TYPES) {
                UUID[] now = from.networks(type);
                if (!networks[type.ordinal()].equals(Arrays.asList(now))) {
                    networks[type.ordinal()] = List.of(now);
                    networkOrder = null;
                    changed = true;
                }
            }
            summary.scan(from, networkOrder());
            machineRolesRead = true;
            if (summary.roles != roles) {
                roles = summary.roles;
                changed = true;
            }
            if (summary.modes != faceModes) {
                faceModes = summary.modes;
                changed = true;
            }
            if (summary.attachedRoles != attachedRoles || summary.attachedNets != attachedNets) {
                attachedRoles = summary.attachedRoles;
                attachedNets = summary.attachedNets;
                changed = true;
            }
            if (!remotes.matches(from.remoteMachines(), summary)) {
                remotes = IndexRemotes.of(from.remoteMachines(), summary);
                changed = true;
            }
            Level level = from.getLevel();
            BlockPos machinePos = from.machinePos();
            // Sem carregar o chunk vizinho: se ele não está carregado, fica a máquina que já se sabia.
            if (level != null && level.isLoaded(machinePos)) {
                ResourceLocation block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(machinePos).getBlock());
                if (!block.equals(machine)) {
                    machine = block;
                    changed = true;
                }
            }
            if (changed) {
                searchText = null;
                saved = null;
            }
            return changed;
        }

        /** A entrada codificada, guardada até um dado salvo mudar. */
        private CompoundTag save() {
            CompoundTag tag = saved;
            if (tag == null) {
                tag = encode();
                saved = tag;
            }
            return tag;
        }

        private CompoundTag encode() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", key.dimension.location().toString());
            tag.putLong("pos", key.pos.asLong());
            if (!name.isEmpty()) {
                tag.putString("name", name);
            }
            if (!attachedLabel.isEmpty()) {
                tag.putString("attached_label", attachedLabel);
            }
            tag.putString("tier", tier.getSerializedName());
            CompoundTag networksTag = new CompoundTag();
            for (ResourceType type : TYPES) {
                List<UUID> ids = networks[type.ordinal()];
                if (!ids.isEmpty()) {
                    ListTag list = new ListTag();
                    for (UUID id : ids) {
                        list.add(NbtUtils.createUUID(id));
                    }
                    networksTag.put(type.key(), list);
                }
            }
            tag.put("face_networks", networksTag);
            // Zero é o que a leitura dá sem a chave: um nó sem faces configuradas não grava nenhuma das duas.
            if (roles != 0) {
                tag.putInt("roles", roles);
            }
            if (faceModes != 0) {
                tag.putLong("face_modes", faceModes);
            }
            if (attachedRoles != 0) {
                tag.putInt("attached_roles", attachedRoles);
            }
            if (attachedNets != 0) {
                tag.putLong("attached_nets", attachedNets);
            }
            tag.putString("machine", machine.toString());
            if (placedBy != null) {
                tag.putUUID("placedBy", placedBy);
            }
            remotes.save(tag);
            return tag;
        }

        private static @Nullable Entry load(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dim"));
            if (dimension == null) {
                return null;
            }
            Entry entry = new Entry(new NodeKey(ResourceKey.create(Registries.DIMENSION, dimension),
                    BlockPos.of(tag.getLong("pos"))));
            entry.name = tag.getString("name");
            entry.attachedLabel = tag.getString("attached_label");
            String tierName = tag.getString("tier");
            entry.tier = Arrays.stream(RouterTier.values()).filter(t -> t.getSerializedName().equals(tierName))
                    .findFirst().orElse(RouterTier.BASIC);
            if (tag.contains("face_networks", Tag.TAG_COMPOUND)) {
                CompoundTag networksTag = tag.getCompound("face_networks");
                for (ResourceType type : TYPES) {
                    ListTag list = networksTag.getList(type.key(), Tag.TAG_INT_ARRAY);
                    Set<UUID> ids = new LinkedHashSet<>();
                    for (Tag id : list) {
                        try {
                            ids.add(NbtUtils.loadUUID(id));
                        } catch (IllegalArgumentException e) {
                            // UUID mal formado: fica de fora (o roteador relê ao carregar)
                        }
                    }
                    entry.networks[type.ordinal()] = List.copyOf(ids);
                }
            } else {
                // migração: a chave antiga tinha uma rede por tipo
                CompoundTag networksTag = tag.getCompound("networks");
                for (ResourceType type : TYPES) {
                    String typeKey = type.key();
                    entry.networks[type.ordinal()] = networksTag.hasUUID(typeKey)
                            ? List.of(networksTag.getUUID(typeKey)) : List.of();
                }
            }
            entry.roles = tag.getInt("roles");
            entry.faceModes = tag.getLong("face_modes");
            entry.attachedRoles = tag.getInt("attached_roles");
            entry.attachedNets = tag.getLong("attached_nets");
            ResourceLocation machine = ResourceLocation.tryParse(tag.getString("machine"));
            if (machine != null) {
                entry.machine = machine;
            }
            entry.placedBy = tag.hasUUID("placedBy") ? tag.getUUID("placedBy") : null;
            entry.remotes = IndexRemotes.load(tag);
            return entry;
        }
    }

    private final Map<NodeKey, Entry> entries = new LinkedHashMap<>();
    /** O resumo das faces, reaproveitado a cada leitura de roteador (só a thread do servidor lê). Não é salvo. */
    private final FaceSummary summary = new FaceSummary();
    /** As entradas de cada chunk ({@code ChunkPos.toLong()}), por dimensão, para o {@link #chunkLoaded}. Não é salvo. */
    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<List<Entry>>> byChunk = new HashMap<>();
    /** Muda a cada alteração, salva ou não (inclusive carregar e descarregar). Não é salvo. */
    private int version;
    /** Muda só quando um dado salvo muda (não ao carregar ou descarregar). Não é salvo. */
    private int dataVersion;
    /** As máquinas da base: as vinculadas de cada nó ({@link IndexRemotes#total()}) e uma presa por nó. Não é salvo. */
    private int machines;

    public NodeIndex() {
    }

    public static NodeIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Bit do papel {@code role} ({@link #EXTRACT}, {@link #INSERT} ou {@link #STORAGE}) do tipo. */
    public static int role(ResourceType type, int role) {
        return 1 << (type.ordinal() * ROLES_PER_TYPE + role);
    }

    /** O modo da face do lado {@code side} da máquina, do tipo {@code type}, em {@code faceModes} ({@link FaceSummary}). */
    public static PortMode faceMode(long faceModes, ResourceType type, RemoteSide side) {
        return FaceSummary.mode(faceModes, FaceSummary.shift(type, side));
    }

    /** O bit do papel de uma face do tipo {@code type} no modo {@code mode}; 0 em Nenhum. */
    static int roleOf(ResourceType type, PortMode mode) {
        return switch (mode) {
            case EXTRACT -> role(type, EXTRACT);
            case INSERT -> role(type, INSERT);
            case BOTH -> role(type, STORAGE);
            case NONE -> 0;
        };
    }

    // ------------------------------------------------------------------ avisos do roteador

    /** O roteador carregou ou mudou algo que o índice guarda. Só no servidor; barato se nada mudou. */
    public static void track(RouterBlockEntity router) {
        if (router.getLevel() instanceof ServerLevel level && !router.isRemoved()) {
            get(level.getServer()).update(router);
        }
    }

    /** O roteador descarregou (ou foi removido): fica no índice como descarregado. */
    public static void untrack(RouterBlockEntity router) {
        if (router.getLevel() instanceof ServerLevel level) {
            get(level.getServer()).unloaded(router);
        }
    }

    /** O roteador saiu do mundo (quebrado, trocado por outro bloco): sai do índice. */
    public static void forget(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            get(serverLevel.getServer()).remove(NodeKey.of(level, pos));
        }
    }

    /**
     * O chunk {@code chunk} de {@code level} carregou: as entradas dele sem roteador no lugar (o bloco sumiu sem
     * {@code onRemove}) saem do índice. Lê só o bloco do próprio chunk; num chunk sem entradas, é uma busca de hash.
     * Chamado pelo {@code ChunkEvent.Load}.
     */
    public static void chunkLoaded(ServerLevel level, ChunkAccess chunk) {
        get(level.getServer()).checkChunk(level.dimension(), chunk);
    }

    private void checkChunk(ResourceKey<Level> dimension, ChunkAccess chunk) {
        Long2ObjectOpenHashMap<List<Entry>> chunks = byChunk.get(dimension);
        List<Entry> inChunk = chunks == null ? null : chunks.get(chunk.getPos().toLong());
        if (inChunk == null) {
            return;
        }
        // De trás para a frente: o remove tira a entrada desta mesma lista.
        for (int i = inChunk.size() - 1; i >= 0; i--) {
            NodeKey key = inChunk.get(i).key;
            if (!chunk.getBlockState(key.pos).is(ModBlocks.ROUTER.get())) {
                remove(key);
            }
        }
    }

    /** Quem colocou o roteador, para o Tablet mostrar o nó ao dono mesmo fora das redes dele. */
    public static void placedBy(RouterBlockEntity router, UUID player) {
        if (router.getLevel() instanceof ServerLevel level) {
            NodeIndex index = get(level.getServer());
            Entry entry = index.update(router);
            if (entry != null && !player.equals(entry.placedBy)) {
                entry.placedBy = player;
                entry.saved = null;
                index.setDirty();
            }
        }
    }

    private @Nullable Entry update(RouterBlockEntity router) {
        Level level = router.getLevel();
        if (level == null || router.isRemoved()) {
            return null;
        }
        NodeKey key = NodeKey.of(level, router.getBlockPos());
        Entry entry = entries.get(key);
        boolean created = entry == null;
        if (created) {
            entry = new Entry(key);
            add(entry);
        }
        int before = entry.remotes.total();
        boolean changed = entry.read(router, summary);
        machines += entry.remotes.total() - before;
        boolean loadedChanged = entry.router != router;
        entry.router = router;
        if (created || changed) {
            setDirty();
        } else if (loadedChanged) {
            version++;
        }
        return entry;
    }

    private void unloaded(RouterBlockEntity router) {
        Level level = router.getLevel();
        if (level == null) {
            return;
        }
        Entry entry = entries.get(NodeKey.of(level, router.getBlockPos()));
        if (entry != null && entry.router == router) {
            entry.router = null;
            version++;
        }
    }

    /** Tira o nó do índice. Devolve se ele estava lá. */
    public boolean remove(NodeKey key) {
        Entry entry = entries.remove(key);
        if (entry == null) {
            return false;
        }
        machines -= 1 + entry.remotes.total();
        Long2ObjectOpenHashMap<List<Entry>> chunks = byChunk.get(key.dimension);
        long chunk = ChunkPos.asLong(key.pos);
        List<Entry> inChunk = chunks == null ? null : chunks.get(chunk);
        if (inChunk != null) {
            inChunk.remove(entry);
            if (inChunk.isEmpty()) {
                chunks.remove(chunk);
            }
        }
        setDirty();
        return true;
    }

    private void add(Entry entry) {
        entries.put(entry.key, entry);
        machines += 1 + entry.remotes.total();
        byChunk.computeIfAbsent(entry.key.dimension, dimension -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(ChunkPos.asLong(entry.key.pos), chunk -> new ArrayList<>(1)).add(entry);
    }

    // ------------------------------------------------------------------ consulta

    /** Muda a cada alteração do índice, inclusive carregar e descarregar; para comparar com uma versão lida antes. */
    public int version() {
        return version;
    }

    /**
     * Muda só quando um dado salvo do índice muda (nó novo ou removido, nome, tier, redes, papéis, modos das faces,
     * máquina, quem colocou, máquinas vinculadas); carregar e descarregar chunks não mexe nela.
     */
    public int dataVersion() {
        return dataVersion;
    }

    public @Nullable Entry entry(NodeKey key) {
        return entries.get(key);
    }

    /** Todas as entradas, em ordem de inclusão. Visão só de leitura. */
    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public int size() {
        return entries.size();
    }

    /** Quantas máquinas a base tem: as vinculadas, inclusive as acima do teto, e uma presa por roteador. */
    public int machines() {
        return machines;
    }

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty);
        if (dirty) {
            version++;
            dataVersion++;
        }
    }

    /**
     * As entradas vão pelas tags que cada uma guarda ({@link Entry#save}): quem chama não altera as tags dentro de
     * {@code nodes} (copie antes).
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            list.add(entry.save());
        }
        tag.put("nodes", list);
        return tag;
    }

    public static NodeIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        NodeIndex index = new NodeIndex();
        ListTag list = tag.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Entry entry = Entry.load(list.getCompound(i));
            if (entry != null && !index.entries.containsKey(entry.key)) {
                index.add(entry);
            }
        }
        return index;
    }
}
