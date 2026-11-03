package io.github.matheusanbs.wirelessautomate.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Grupo de redes de um mesmo sistema (ex.: "Linha 5x"), só para organizar no Tablet: ver as redes
 * juntas e pausar ou retomar todas de uma vez. Não troca recursos entre as redes. Guardado no
 * {@link NetworkSavedData}.
 *
 * @param networks redes do grupo, em ordem de inclusão, sem repetição
 * @param paused   as redes do grupo ficam paradas (o motor não monta as rotas delas)
 */
public record WaGroup(UUID id, String name, UUID owner, List<UUID> networks, boolean paused) {
    public WaGroup {
        networks = List.copyOf(networks);
    }

    public WaGroup withName(String name) {
        return new WaGroup(id, name, owner, networks, paused);
    }

    public WaGroup withPaused(boolean paused) {
        return new WaGroup(id, name, owner, networks, paused);
    }

    public WaGroup withNetwork(UUID network) {
        if (networks.contains(network)) {
            return this;
        }
        List<UUID> list = new ArrayList<>(networks);
        list.add(network);
        return new WaGroup(id, name, owner, list, paused);
    }

    public WaGroup withoutNetwork(UUID network) {
        if (!networks.contains(network)) {
            return this;
        }
        List<UUID> list = new ArrayList<>(networks);
        list.remove(network);
        return new WaGroup(id, name, owner, list, paused);
    }

    /** Dono ou operador nível 2. */
    public boolean canManage(ServerPlayer player) {
        return owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putUUID("owner", owner);
        ListTag list = new ListTag();
        for (UUID network : networks) {
            list.add(NbtUtils.createUUID(network));
        }
        tag.put("networks", list);
        if (paused) {
            tag.putBoolean("paused", true);
        }
        return tag;
    }

    public static WaGroup load(CompoundTag tag) {
        List<UUID> networks = new ArrayList<>();
        ListTag list = tag.getList("networks", Tag.TAG_INT_ARRAY);
        for (Tag entry : list) {
            UUID network = NbtUtils.loadUUID(entry);
            if (!networks.contains(network)) {
                networks.add(network);
            }
        }
        return new WaGroup(tag.getUUID("id"), tag.getString("name"), tag.getUUID("owner"), networks,
                tag.getBoolean("paused"));
    }
}
