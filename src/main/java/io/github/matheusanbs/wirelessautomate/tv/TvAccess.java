package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.network.TeamAccess;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * A lista de acesso de uma Tela de Sinal, igual à de uma rede privada: os jogadores convidados e os times do FTB Teams
 * cujos membros editam a Tela como o dono (tamanho, layout e ligações, cada um dentro do que acessa nas redes). Ver a
 * Tela continua livre para todos. Guarda o id do time, não os membros: quem entra ou sai do time ganha ou perde o
 * acesso sozinho. Sem o FTB os times ficam guardados e não dão acesso a ninguém ({@link TeamAccess#isMember}).
 *
 * @param players os convidados, sem repetição, com o último nome conhecido (para mostrar quando o servidor não o sabe)
 * @param teams   os times, sem repetição
 */
public record TvAccess(List<Guest> players, List<UUID> teams) {
    public static final TvAccess EMPTY = new TvAccess(List.of(), List.of());
    /** O que a leitura do disco aceita no máximo, os tetos de faixa da config: um arquivo editado não cresce sem fim. */
    private static final int HARD_PLAYERS = 10_000;
    private static final int HARD_TEAMS = 1_000;

    /** Um convidado: o id e o nome que ele tinha quando entrou na lista. */
    public record Guest(UUID id, String name) {
    }

    public TvAccess {
        players = List.copyOf(players);
        teams = List.copyOf(teams);
    }

    public boolean isEmpty() {
        return players.isEmpty() && teams.isEmpty();
    }

    public boolean hasPlayer(UUID player) {
        for (Guest guest : players) {
            if (guest.id().equals(player)) {
                return true;
            }
        }
        return false;
    }

    public TvAccess withPlayer(UUID player, String name) {
        if (hasPlayer(player)) {
            return this;
        }
        List<Guest> copy = new ArrayList<>(players.size() + 1);
        copy.addAll(players);
        copy.add(new Guest(player, name));
        return new TvAccess(copy, teams);
    }

    public TvAccess withoutPlayer(UUID player) {
        if (!hasPlayer(player)) {
            return this;
        }
        List<Guest> copy = new ArrayList<>(players.size());
        for (Guest guest : players) {
            if (!guest.id().equals(player)) {
                copy.add(guest);
            }
        }
        return new TvAccess(copy, teams);
    }

    public TvAccess withTeam(UUID team) {
        if (teams.contains(team)) {
            return this;
        }
        List<UUID> copy = new ArrayList<>(teams.size() + 1);
        copy.addAll(teams);
        copy.add(team);
        return new TvAccess(players, copy);
    }

    public TvAccess withoutTeam(UUID team) {
        if (!teams.contains(team)) {
            return this;
        }
        List<UUID> copy = new ArrayList<>(teams);
        copy.remove(team);
        return new TvAccess(players, copy);
    }

    /** O jogador está na lista: convidado ou membro de um time liberado (só com o FTB instalado). */
    public boolean includes(UUID player) {
        if (hasPlayer(player)) {
            return true;
        }
        for (UUID team : teams) {
            if (TeamAccess.isMember(team, player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Alguém que edita a Tela pode usar a rede: o dono, um convidado, um time liberado que a rede também tem ou um
     * membro de um time liberado. A Tela no mundo lê a rede com esse acesso, para a ligação que um convidado fez à
     * rede dele mostrar o número. O dono vem primeiro: a Tela que só o dono edita custa o que custava; a lista só é
     * percorrida quando o dono não pode usar a rede (uma vez por leitura da Tela, nunca por quadro).
     */
    public boolean canRead(WaNetwork network, @Nullable UUID owner) {
        if (owner != null && network.canUse(owner, false)) {
            return true;
        }
        for (Guest guest : players) {
            if (network.canUse(guest.id(), false)) {
                return true;
            }
        }
        for (UUID team : teams) {
            if (network.teams().contains(team)) {
                return true;
            }
            for (UUID member : TeamAccess.members(team)) {
                if (network.canUse(member, false)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** O NBT, ou {@code null} com a lista vazia (a chave não vai ao disco). */
    public @Nullable CompoundTag save() {
        if (isEmpty()) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        if (!players.isEmpty()) {
            ListTag list = new ListTag();
            for (Guest guest : players) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("id", guest.id());
                entry.putString("name", guest.name());
                list.add(entry);
            }
            tag.put("players", list);
        }
        if (!teams.isEmpty()) {
            ListTag list = new ListTag();
            for (UUID team : teams) {
                list.add(NbtUtils.createUUID(team));
            }
            tag.put("teams", list);
        }
        return tag;
    }

    /** Lê a chave {@code access}; sem ela (uma Tela antiga) ou ilegível, vazia. Ignora o repetido e o que não é UUID. */
    public static TvAccess load(@Nullable Tag stored) {
        if (!(stored instanceof CompoundTag tag)) {
            return EMPTY;
        }
        List<Guest> players = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Tag entry : tag.getList("players", Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag guest && guest.hasUUID("id") && players.size() < HARD_PLAYERS) {
                UUID id = guest.getUUID("id");
                if (seen.add(id)) {
                    String name = guest.getString("name");
                    players.add(new Guest(id, name.length() > 64 ? name.substring(0, 64) : name));
                }
            }
        }
        List<UUID> teams = new ArrayList<>();
        for (Tag entry : tag.getList("teams", Tag.TAG_INT_ARRAY)) {
            if (entry instanceof IntArrayTag array && array.size() == 4 && teams.size() < HARD_TEAMS) {
                UUID id = NbtUtils.loadUUID(array);
                if (seen.add(id)) {
                    teams.add(id);
                }
            }
        }
        return players.isEmpty() && teams.isEmpty() ? EMPTY : new TvAccess(players, teams);
    }
}
