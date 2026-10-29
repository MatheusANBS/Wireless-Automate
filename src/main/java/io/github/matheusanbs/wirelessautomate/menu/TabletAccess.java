package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.AccessView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Guest;
import io.github.matheusanbs.wirelessautomate.network.AccessSuggestions;
import io.github.matheusanbs.wirelessautomate.network.AccessSuggestions.Candidate;
import io.github.matheusanbs.wirelessautomate.network.KnownPlayers;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess.TeamInfo;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestPayload;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestionsPayload.Suggestion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * A lista de acesso de uma rede privada no servidor: quem aparece na tela do dono, quem uma ação consegue
 * resolver (por id ou pelo nome digitado) e as sugestões do campo. Tudo sob demanda: a tela do dono, uma ação ou
 * uma consulta de sugestão; nada por tick.
 */
public final class TabletAccess {
    private TabletAccess() {
    }

    /** A lista de acesso que o dono vê; os times só com o FTB Teams carregado. */
    static AccessView view(MinecraftServer server, WaNetwork network) {
        if (network.members().isEmpty() && network.teams().isEmpty()) {
            return AccessView.EMPTY;
        }
        List<Guest> guests = new ArrayList<>(network.members().size());
        for (UUID id : network.members()) {
            guests.add(new Guest(id, KnownPlayers.name(server, id), KnownPlayers.isOnline(server, id)));
        }
        return new AccessView(guests, teamInfos(network.teams()));
    }

    /** Os times para mostrar numa lista de acesso; vazia sem o FTB Teams carregado. */
    public static List<TeamInfo> teamInfos(List<UUID> ids) {
        List<TeamInfo> teams = new ArrayList<>(ids.size());
        if (TeamAccess.LOADED) {
            for (UUID id : ids) {
                TeamInfo info = TeamAccess.info(id);
                if (info != null) {
                    teams.add(info);
                }
            }
        }
        return teams;
    }

    /** O jogador que a ação quer convidar: o id mandado (se o servidor o conhece) ou o nome digitado; senão {@code null}. */
    public static @Nullable UUID resolvePlayer(MinecraftServer server, Optional<UUID> id, String text) {
        if (id.isPresent()) {
            return KnownPlayers.knows(server, id.get()) ? id.get() : null;
        }
        String name = text.strip();
        return name.isEmpty() ? null : KnownPlayers.byName(server, name);
    }

    /** O time de grupo que a ação quer adicionar: o id mandado (se existe) ou o nome exato, se único; senão {@code null}. */
    public static @Nullable UUID resolveTeam(Optional<UUID> id, String text) {
        if (!TeamAccess.LOADED) {
            return null;
        }
        if (id.isPresent()) {
            TeamInfo info = TeamAccess.info(id.get());
            return info != null && !info.gone() ? id.get() : null;
        }
        String name = text.strip().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return null;
        }
        UUID found = null;
        for (TeamInfo team : TeamAccess.teams()) {
            if (team.name().toLowerCase(Locale.ROOT).equals(name)) {
                if (found != null) {
                    return null;
                }
                found = team.id();
            }
        }
        return found;
    }

    /**
     * As sugestões do campo para o texto digitado, ou {@code null} se o jogador não gerencia a rede ou pede rápido
     * demais (uma consulta a cada 100 ms). No máximo {@link AccessSuggestions#MAX} linhas.
     */
    public static @Nullable List<Suggestion> suggestions(ServerPlayer player, NetworkSuggestPayload.Kind kind,
            String text, UUID networkId) {
        MinecraftServer server = player.server;
        WaNetwork network = NetworkSavedData.get(server).network(networkId);
        if (network == null || !network.canManage(player)
                || !TabletPlayers.takeSuggest(player.getUUID(), server.getTickCount())) {
            return null;
        }
        String clipped = text.length() > NetworkSuggestPayload.MAX_TEXT
                ? text.substring(0, NetworkSuggestPayload.MAX_TEXT) : text;
        return kind == NetworkSuggestPayload.Kind.PLAYER
                ? players(server, id -> network.owner().equals(id) || network.members().contains(id), clipped)
                : teams(network.teams()::contains, clipped);
    }

    /**
     * As sugestões de jogador para o texto (já cortado): os conhecidos do servidor, com {@code taken} marcando quem
     * já tem o acesso. Comum às redes e à lista de acesso da Tela de Sinal.
     */
    public static List<Suggestion> players(MinecraftServer server, Predicate<UUID> taken, String text) {
        List<Candidate> all = KnownPlayers.candidates(server, taken);
        List<Suggestion> result = new ArrayList<>();
        for (Candidate candidate : AccessSuggestions.pick(all, text, AccessSuggestions.MAX)) {
            result.add(new Suggestion(candidate.id(), candidate.name(), candidate.online() ? 1 : 0, candidate.taken()));
        }
        return result;
    }

    /** As sugestões de time de grupo para o texto (já cortado); vazia sem o FTB Teams. */
    public static List<Suggestion> teams(Predicate<UUID> taken, String text) {
        List<TeamInfo> teams = TeamAccess.teams();
        Map<UUID, TeamInfo> byId = new HashMap<>();
        List<Candidate> all = new ArrayList<>(teams.size());
        for (TeamInfo team : teams) {
            byId.put(team.id(), team);
            all.add(new Candidate(team.id(), team.name(), false, taken.test(team.id())));
        }
        List<Suggestion> result = new ArrayList<>();
        for (Candidate candidate : AccessSuggestions.pick(all, text, AccessSuggestions.MAX)) {
            result.add(new Suggestion(candidate.id(), candidate.name(), byId.get(candidate.id()).members(),
                    candidate.taken()));
        }
        return result;
    }
}
