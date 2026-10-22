package io.github.matheusanbs.wirelessautomate.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Uma rede: tudo que estiver nela troca recursos entre si. Guardada no {@link NetworkSavedData}.
 *
 * @param color    cor RGB (0xRRGGBB), usada nas telas e no LED de rede
 * @param isPublic pública: outros jogadores podem escolhê-la (rede ativa, mover nós para ela);
 *                 privada (o padrão): só o dono, operadores e quem foi convidado
 * @param members  jogadores convidados (usam e editam, sem gerenciar), sem repetição
 * @param teams    times do FTB Teams adicionados: os membros de cada um têm o acesso de um convidado
 */
public record WaNetwork(UUID id, String name, int color, UUID owner, boolean isPublic, List<UUID> members,
        List<UUID> teams) {
    public WaNetwork {
        members = List.copyOf(members);
        teams = List.copyOf(teams);
    }

    public WaNetwork(UUID id, String name, int color, UUID owner, boolean isPublic) {
        this(id, name, color, owner, isPublic, List.of(), List.of());
    }

    /** Rede privada, sem convidados. */
    public WaNetwork(UUID id, String name, int color, UUID owner) {
        this(id, name, color, owner, false);
    }

    public WaNetwork withName(String name) {
        return new WaNetwork(id, name, color, owner, isPublic, members, teams);
    }

    public WaNetwork withColor(int color) {
        return new WaNetwork(id, name, color & 0xFFFFFF, owner, isPublic, members, teams);
    }

    public WaNetwork withPublic(boolean isPublic) {
        return new WaNetwork(id, name, color, owner, isPublic, members, teams);
    }

    public WaNetwork withMember(UUID player) {
        return members.contains(player) ? this
                : new WaNetwork(id, name, color, owner, isPublic, plus(members, player), teams);
    }

    public WaNetwork withoutMember(UUID player) {
        return !members.contains(player) ? this
                : new WaNetwork(id, name, color, owner, isPublic, minus(members, player), teams);
    }

    public WaNetwork withTeam(UUID team) {
        return teams.contains(team) ? this : new WaNetwork(id, name, color, owner, isPublic, members, plus(teams, team));
    }

    public WaNetwork withoutTeam(UUID team) {
        return !teams.contains(team) ? this
                : new WaNetwork(id, name, color, owner, isPublic, members, minus(teams, team));
    }

    private static List<UUID> plus(List<UUID> list, UUID id) {
        List<UUID> copy = new ArrayList<>(list.size() + 1);
        copy.addAll(list);
        copy.add(id);
        return copy;
    }

    private static List<UUID> minus(List<UUID> list, UUID id) {
        List<UUID> copy = new ArrayList<>(list);
        copy.remove(id);
        return copy;
    }

    /** Pode gerenciar a rede (renomear, cor, privacidade, remover, convidar): o dono ou um operador nível 2. */
    public boolean canManage(UUID player, boolean operator) {
        return operator || owner.equals(player);
    }

    /**
     * Pode editar os nós da rede, inclusive tirar uma face dela: quem gerencia, um convidado ou um membro de um
     * time adicionado. Ser pública não basta.
     */
    public boolean canEdit(UUID player, boolean operator) {
        if (canManage(player, operator) || members.contains(player)) {
            return true;
        }
        for (UUID team : teams) {
            if (TeamAccess.isMember(team, player)) {
                return true;
            }
        }
        return false;
    }

    /** Pode pôr nós nesta rede, usá-la como ativa e vê-la: quem edita ou qualquer um se for pública. */
    public boolean canUse(UUID player, boolean operator) {
        return isPublic || canEdit(player, operator);
    }

    public boolean canManage(ServerPlayer player) {
        return canManage(player.getUUID(), player.hasPermissions(2));
    }

    public boolean canEdit(ServerPlayer player) {
        return canEdit(player.getUUID(), player.hasPermissions(2));
    }

    public boolean canUse(ServerPlayer player) {
        return canUse(player.getUUID(), player.hasPermissions(2));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putInt("color", color);
        tag.putUUID("owner", owner);
        if (isPublic) {
            tag.putBoolean("public", true);
        }
        if (!members.isEmpty()) {
            tag.put("members", saveIds(members));
        }
        if (!teams.isEmpty()) {
            tag.put("teams", saveIds(teams));
        }
        return tag;
    }

    public static WaNetwork load(CompoundTag tag) {
        return new WaNetwork(tag.getUUID("id"), tag.getString("name"), tag.getInt("color") & 0xFFFFFF,
                tag.getUUID("owner"), tag.getBoolean("public"), loadIds(tag, "members"), loadIds(tag, "teams"));
    }

    private static ListTag saveIds(List<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            list.add(NbtUtils.createUUID(id));
        }
        return list;
    }

    /** Ignora o que não for um UUID e o que se repete: um arquivo editado à mão não derruba a carga. */
    private static List<UUID> loadIds(CompoundTag tag, String key) {
        List<UUID> ids = new ArrayList<>();
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return ids;
        }
        for (Tag entry : tag.getList(key, Tag.TAG_INT_ARRAY)) {
            if (entry instanceof IntArrayTag array && array.size() == 4) {
                UUID id = NbtUtils.loadUUID(array);
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /** Nome da rede pintado com a cor dela, para mensagens. */
    public MutableComponent displayName() {
        return Component.literal(name).withStyle(style -> style.withColor(color));
    }
}
