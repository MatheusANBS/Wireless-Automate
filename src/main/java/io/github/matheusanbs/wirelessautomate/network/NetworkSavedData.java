package io.github.matheusanbs.wirelessautomate.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Redes do mundo e a rede ativa de cada jogador. Fica no data storage do overworld.
 *
 * <p>Remover uma rede não mexe nos roteadores: os que apontavam para ela ficam com um id que não
 * existe mais, e o motor trata id inexistente como "sem rede". Assim não é preciso varrer o mundo
 * nem carregar chunks.
 *
 * <p>Também guarda os grupos de redes ({@link WaGroup}), só de organização: uma rede está pausada se
 * algum grupo pausado a contém ({@link #isPaused}), e o motor trata rede pausada como sem rotas.
 */
public final class NetworkSavedData extends SavedData {
    public static final String DATA_NAME = "wirelessautomate_networks";
    public static final SavedData.Factory<NetworkSavedData> FACTORY =
            new SavedData.Factory<>(NetworkSavedData::new, NetworkSavedData::load);

    /** Cores padrão das redes novas, escolhidas pelo hash do id. */
    private static final int[] PALETTE = {
        0x4FC3F7, 0x81C784, 0xFFB74D, 0xE57373, 0xBA68C8, 0xFFF176,
        0x4DB6AC, 0xF06292, 0x9575CD, 0xAED581, 0x7986CB, 0xA1887F,
    };

    /** Em ordem de criação. */
    private final Map<UUID, WaNetwork> networks = new LinkedHashMap<>();
    private final Map<UUID, UUID> activeByPlayer = new HashMap<>();
    /** Em ordem de criação. */
    private final Map<UUID, WaGroup> groups = new LinkedHashMap<>();
    /** Redes em algum grupo pausado; refeito quando um grupo muda. Não é salvo. */
    private final Set<UUID> paused = new HashSet<>();
    /** Muda a cada alteração (rede criada, removida, rede ativa). Não é salvo. */
    private int version;

    public NetworkSavedData() {
    }

    public static NetworkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * Versão das redes: muda sempre que algo é marcado para salvar. As telas abertas comparam com a
     * versão que já viram para saber se o seletor de redes precisa ser reenviado, sem varrer nada.
     */
    public int version() {
        return version;
    }

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty);
        if (dirty) {
            version++;
        }
    }

    public static int colorFor(UUID id) {
        return PALETTE[Math.floorMod(id.hashCode(), PALETTE.length)];
    }

    /** As cores padrão, para o seletor de cor do Tablet. Cópia. */
    public static int[] palette() {
        return PALETTE.clone();
    }

    public WaNetwork create(UUID owner, String name) {
        UUID id = UUID.randomUUID();
        WaNetwork network = new WaNetwork(id, name, colorFor(id), owner);
        networks.put(id, network);
        setDirty();
        return network;
    }

    public @Nullable WaNetwork network(UUID id) {
        return networks.get(id);
    }

    /** Todas as redes, em ordem de criação. Visão só de leitura. */
    public Collection<WaNetwork> networks() {
        return Collections.unmodifiableCollection(networks.values());
    }

    /** Redes de um dono, em ordem de criação. */
    public List<WaNetwork> networksOf(UUID owner) {
        List<WaNetwork> result = new ArrayList<>();
        for (WaNetwork network : networks.values()) {
            if (network.owner().equals(owner)) {
                result.add(network);
            }
        }
        return result;
    }

    /** Rede do dono com esse nome, sem diferenciar maiúsculas. */
    public @Nullable WaNetwork byName(UUID owner, String name) {
        for (WaNetwork network : networks.values()) {
            if (network.owner().equals(owner) && network.name().equalsIgnoreCase(name)) {
                return network;
            }
        }
        return null;
    }

    /**
     * Rede com esse nome, sem diferenciar maiúsculas: primeiro entre as de {@code preferredOwner},
     * depois a mais antiga de qualquer dono.
     */
    public @Nullable WaNetwork find(@Nullable UUID preferredOwner, String name) {
        WaNetwork own = preferredOwner == null ? null : byName(preferredOwner, name);
        if (own != null) {
            return own;
        }
        for (WaNetwork network : networks.values()) {
            if (network.name().equalsIgnoreCase(name)) {
                return network;
            }
        }
        return null;
    }

    /** Remove a rede, limpa a rede ativa de quem a usava e a tira dos grupos. Os roteadores ficam órfãos (ver a classe). */
    public boolean remove(UUID id) {
        if (networks.remove(id) == null) {
            return false;
        }
        activeByPlayer.values().removeIf(id::equals);
        groups.replaceAll((groupId, group) -> group.withoutNetwork(id));
        refreshPaused();
        setDirty();
        return true;
    }

    /** Troca a rede de mesmo id (nome, cor, privacidade). Devolve se ela existia e mudou. */
    public boolean update(WaNetwork network) {
        WaNetwork old = networks.get(network.id());
        if (old == null || old.equals(network)) {
            return false;
        }
        networks.put(network.id(), network);
        setDirty();
        return true;
    }

    // ------------------------------------------------------------------ grupos

    public WaGroup createGroup(UUID owner, String name) {
        WaGroup group = new WaGroup(UUID.randomUUID(), name, owner, List.of(), false);
        groups.put(group.id(), group);
        setDirty();
        return group;
    }

    public @Nullable WaGroup group(UUID id) {
        return groups.get(id);
    }

    /** Todos os grupos, em ordem de criação. Visão só de leitura. */
    public Collection<WaGroup> groups() {
        return Collections.unmodifiableCollection(groups.values());
    }

    /** Troca o grupo de mesmo id; redes que não existem mais saem dele. Devolve se mudou. */
    public boolean updateGroup(WaGroup group) {
        WaGroup old = groups.get(group.id());
        if (old == null) {
            return false;
        }
        WaGroup clean = group;
        for (UUID network : group.networks()) {
            if (!networks.containsKey(network)) {
                clean = clean.withoutNetwork(network);
            }
        }
        if (old.equals(clean)) {
            return false;
        }
        groups.put(clean.id(), clean);
        refreshPaused();
        setDirty();
        return true;
    }

    public boolean removeGroup(UUID id) {
        if (groups.remove(id) == null) {
            return false;
        }
        refreshPaused();
        setDirty();
        return true;
    }

    /** A rede está num grupo pausado: o motor não monta as rotas dela. Consulta num conjunto, sem varrer. */
    public boolean isPaused(UUID network) {
        return !paused.isEmpty() && paused.contains(network);
    }

    private void refreshPaused() {
        paused.clear();
        for (WaGroup group : groups.values()) {
            if (group.paused()) {
                paused.addAll(group.networks());
            }
        }
    }

    public @Nullable UUID activeNetwork(UUID player) {
        return activeByPlayer.get(player);
    }

    /** {@code null} limpa a rede ativa. A rede precisa existir. */
    public void setActiveNetwork(UUID player, @Nullable UUID network) {
        if (network == null) {
            if (activeByPlayer.remove(player) != null) {
                setDirty();
            }
            return;
        }
        if (!networks.containsKey(network)) {
            throw new IllegalArgumentException("rede inexistente: " + network);
        }
        if (!network.equals(activeByPlayer.put(player, network))) {
            setDirty();
        }
    }

    /** A rede ativa do jogador; se ele não tiver nenhuma, cria uma com nome padrão e a ativa. */
    public WaNetwork activeOrCreate(ServerPlayer player) {
        UUID playerId = player.getUUID();
        UUID active = activeByPlayer.get(playerId);
        WaNetwork network = active == null ? null : networks.get(active);
        if (network == null) {
            network = create(playerId, player.getGameProfile().getName());
            setActiveNetwork(playerId, network.id());
        }
        return network;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (WaNetwork network : networks.values()) {
            list.add(network.save());
        }
        tag.put("networks", list);

        ListTag active = new ListTag();
        activeByPlayer.forEach((player, network) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putUUID("network", network);
            active.add(entry);
        });
        tag.put("active", active);

        ListTag groupList = new ListTag();
        for (WaGroup group : groups.values()) {
            groupList.add(group.save());
        }
        tag.put("groups", groupList);
        return tag;
    }

    public static NetworkSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkSavedData data = new NetworkSavedData();
        ListTag list = tag.getList("networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            WaNetwork network = WaNetwork.load(list.getCompound(i));
            data.networks.put(network.id(), network);
        }
        ListTag active = tag.getList("active", Tag.TAG_COMPOUND);
        for (int i = 0; i < active.size(); i++) {
            CompoundTag entry = active.getCompound(i);
            UUID network = entry.getUUID("network");
            if (data.networks.containsKey(network)) {
                data.activeByPlayer.put(entry.getUUID("player"), network);
            }
        }
        ListTag groupList = tag.getList("groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < groupList.size(); i++) {
            WaGroup group = WaGroup.load(groupList.getCompound(i));
            for (UUID network : group.networks()) {
                if (!data.networks.containsKey(network)) {
                    group = group.withoutNetwork(network);
                }
            }
            data.groups.put(group.id(), group);
        }
        data.refreshPaused();
        return data;
    }
}
