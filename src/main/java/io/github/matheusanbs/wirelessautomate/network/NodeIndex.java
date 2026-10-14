package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.Level;
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
 * ver {@link #faceModes}; sem a chave, tudo Nenhum), o bloco da máquina e quem colocou.
 * Uma entrada pode ficar velha se o bloco sumir sem passar pelo {@code onRemove} (região apagada):
 * o Tablet a apaga quando acha o chunk carregado sem roteador ({@link #forget}).
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
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final RemoteSide[] SIDES = RemoteSide.values();
    private static final PortMode[] MODES = PortMode.values();
    private static final UUID[] NO_NETWORKS = new UUID[0];

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
        private RouterTier tier = RouterTier.BASIC;
        /** Por tipo, as redes distintas das faces ({@link RouterBlockEntity#networks}); nunca nulo. */
        private final UUID[][] networks = new UUID[TYPES.length][];
        private int roles;
        private long faceModes;
        private ResourceLocation machine = BuiltInRegistries.BLOCK.getKey(net.minecraft.world.level.block.Blocks.AIR);
        private @Nullable UUID placedBy;
        /** O roteador carregado; não é salvo. */
        private @Nullable RouterBlockEntity router;
        /** Texto da busca do Tablet, feito na primeira busca e refeito só quando o nó muda; não é salvo. */
        private @Nullable String searchText;

        Entry(NodeKey key) {
            this.key = key;
            Arrays.fill(networks, NO_NETWORKS);
        }

        public NodeKey key() {
            return key;
        }

        public String name() {
            return name;
        }

        public RouterTier tier() {
            return tier;
        }

        /** As redes distintas das faces do tipo {@code type} (presa, perfis e exceções). Só leitura. */
        public List<UUID> networks(ResourceType type) {
            return List.of(networks[type.ordinal()]);
        }

        /** Alguma face de algum tipo do nó está em {@code network}. */
        public boolean inNetwork(UUID network) {
            for (UUID[] ids : networks) {
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
            for (UUID[] ids : networks) {
                if (ids.length > 0) {
                    return true;
                }
            }
            return false;
        }

        /** Redes distintas somando todos os tipos (uma rede em dois tipos conta uma vez). */
        public int distinctNetworks() {
            Set<UUID> distinct = new HashSet<>();
            for (UUID[] ids : networks) {
                Collections.addAll(distinct, ids);
            }
            return distinct.size();
        }

        /** Papéis em bits, ver {@link NodeIndex#role}. */
        public int roles() {
            return roles;
        }

        /** O modo de cada face da máquina presa, em bits ({@link NodeIndex#faceModes}). */
        public long faceModes() {
            return faceModes;
        }

        /** O modo da face do lado {@code side} da máquina, do tipo {@code type}. */
        public PortMode faceMode(ResourceType type, RemoteSide side) {
            return NodeIndex.faceMode(faceModes, type, side);
        }

        /** Bloco da máquina onde o roteador está preso (ar se não se sabe). */
        public ResourceLocation machine() {
            return machine;
        }

        public @Nullable UUID placedBy() {
            return placedBy;
        }

        /** O roteador, se o chunk está carregado. */
        public @Nullable RouterBlockEntity router() {
            return router != null && !router.isRemoved() ? router : null;
        }

        public boolean loaded() {
            return router() != null;
        }

        /**
         * Nome, máquina e coordenadas em minúsculas, separados por espaço, para a busca do Tablet.
         * Montado uma vez e guardado até um desses dados mudar.
         */
        public String searchText() {
            if (searchText == null) {
                searchText = (name + ' ' + machine + ' ' + key.pos.getX() + ' ' + key.pos.getY() + ' '
                        + key.pos.getZ()).toLowerCase(Locale.ROOT);
            }
            return searchText;
        }

        /** Copia o que o índice guarda do roteador. Devolve se algum dado salvo mudou. */
        private boolean read(RouterBlockEntity from) {
            boolean changed = false;
            if (!name.equals(from.name())) {
                name = from.name();
                changed = true;
            }
            if (tier != from.tier()) {
                tier = from.tier();
                changed = true;
            }
            for (ResourceType type : TYPES) {
                UUID[] now = from.networks(type);
                if (!Arrays.equals(networks[type.ordinal()], now)) {
                    networks[type.ordinal()] = now.length == 0 ? NO_NETWORKS : now;
                    changed = true;
                }
            }
            int newRoles = NodeIndex.roles(from);
            if (newRoles != roles) {
                roles = newRoles;
                changed = true;
            }
            long newModes = NodeIndex.faceModes(from);
            if (newModes != faceModes) {
                faceModes = newModes;
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
            }
            return changed;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", key.dimension.location().toString());
            tag.putLong("pos", key.pos.asLong());
            if (!name.isEmpty()) {
                tag.putString("name", name);
            }
            tag.putString("tier", tier.getSerializedName());
            CompoundTag networksTag = new CompoundTag();
            for (ResourceType type : TYPES) {
                UUID[] ids = networks[type.ordinal()];
                if (ids.length > 0) {
                    ListTag list = new ListTag();
                    for (UUID id : ids) {
                        list.add(NbtUtils.createUUID(id));
                    }
                    networksTag.put(type.key(), list);
                }
            }
            tag.put("face_networks", networksTag);
            tag.putInt("roles", roles);
            tag.putLong("face_modes", faceModes);
            tag.putString("machine", machine.toString());
            if (placedBy != null) {
                tag.putUUID("placedBy", placedBy);
            }
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
                    entry.networks[type.ordinal()] = ids.isEmpty() ? NO_NETWORKS : ids.toArray(UUID[]::new);
                }
            } else {
                // migração: a chave antiga tinha uma rede por tipo
                CompoundTag networksTag = tag.getCompound("networks");
                for (ResourceType type : TYPES) {
                    String typeKey = type.key();
                    entry.networks[type.ordinal()] = networksTag.hasUUID(typeKey)
                            ? new UUID[] {networksTag.getUUID(typeKey)} : NO_NETWORKS;
                }
            }
            entry.roles = tag.getInt("roles");
            entry.faceModes = tag.getLong("face_modes");
            ResourceLocation machine = ResourceLocation.tryParse(tag.getString("machine"));
            if (machine != null) {
                entry.machine = machine;
            }
            entry.placedBy = tag.hasUUID("placedBy") ? tag.getUUID("placedBy") : null;
            return entry;
        }
    }

    private final Map<NodeKey, Entry> entries = new LinkedHashMap<>();
    /** Muda a cada alteração, salva ou não (inclusive carregar e descarregar). Não é salvo. */
    private int version;
    /** Muda só quando um dado salvo muda (não ao carregar ou descarregar). Não é salvo. */
    private int dataVersion;

    public NodeIndex() {
    }

    public static NodeIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Bit do papel {@code role} ({@link #EXTRACT}, {@link #INSERT} ou {@link #STORAGE}) do tipo. */
    public static int role(ResourceType type, int role) {
        return 1 << (type.ordinal() * ROLES_PER_TYPE + role);
    }

    /**
     * Os modos das faces da máquina presa em bits, pelos lados dela ({@link RemoteSide}, relativos à frente da
     * máquina, como o painel do nó mostra): para cada tipo na ordem do {@link ResourceType} e cada lado na ordem
     * do enum, 2 bits com o ordinal do {@link PortMode}, a partir do menos significativo (o lado {@code s} do tipo
     * {@code t} começa no bit {@code (t * 6 + s) * 2}). Sem a frente da máquina (chunk dela descarregado), vale a
     * de Norte, como no painel.
     */
    public static long faceModes(RouterBlockEntity router) {
        int front = router.attachedFront();
        long bits = 0;
        for (ResourceType type : TYPES) {
            for (RemoteSide side : SIDES) {
                Direction face = DIRECTIONS[side.toAbsolute(front)];
                bits |= (long) router.face(type, face).mode().ordinal() << faceShift(type, side);
            }
        }
        return bits;
    }

    /** O modo da face do lado {@code side} da máquina, do tipo {@code type}, em {@code faceModes}. */
    public static PortMode faceMode(long faceModes, ResourceType type, RemoteSide side) {
        return MODES[(int) (faceModes >>> faceShift(type, side)) & 3];
    }

    private static int faceShift(ResourceType type, RemoteSide side) {
        return (type.ordinal() * SIDES.length + side.ordinal()) * 2;
    }

    /** Papéis do roteador pelas faces configuradas (modo, sem olhar redstone): Armazém conta só como armazém. */
    public static int roles(RouterBlockEntity router) {
        int roles = 0;
        for (ResourceType type : TYPES) {
            for (Direction face : DIRECTIONS) {
                PortMode mode = router.face(type, face).mode();
                switch (mode) {
                    case EXTRACT -> roles |= role(type, EXTRACT);
                    case INSERT -> roles |= role(type, INSERT);
                    case BOTH -> roles |= role(type, STORAGE);
                    case NONE -> {
                    }
                }
            }
        }
        return roles;
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

    /** Quem colocou o roteador, para o Tablet mostrar o nó ao dono mesmo fora das redes dele. */
    public static void placedBy(RouterBlockEntity router, UUID player) {
        if (router.getLevel() instanceof ServerLevel level) {
            NodeIndex index = get(level.getServer());
            Entry entry = index.update(router);
            if (entry != null && !player.equals(entry.placedBy)) {
                entry.placedBy = player;
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
            entries.put(key, entry);
        }
        boolean changed = entry.read(router);
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
        if (entries.remove(key) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    // ------------------------------------------------------------------ consulta

    /** Muda a cada alteração do índice, inclusive carregar e descarregar; para comparar com uma versão lida antes. */
    public int version() {
        return version;
    }

    /**
     * Muda só quando um dado salvo do índice muda (nó novo ou removido, nome, tier, redes, papéis, modos das faces,
     * máquina, quem colocou); carregar e descarregar chunks não mexe nela.
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

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty);
        if (dirty) {
            version++;
            dataVersion++;
        }
    }

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
            if (entry != null) {
                index.entries.put(entry.key, entry);
            }
        }
        return index;
    }
}
