package io.github.matheusanbs.wirelessautomate.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess.TeamInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Os times de grupo do FTB Teams. A única classe do mod que toca na API dele; só carrega com o FTB presente
 * ({@link io.github.matheusanbs.wirelessautomate.network.TeamAccess} confere antes). Sem o gerenciador de times
 * carregado (servidor ainda subindo ou já parando), nenhum time existe.
 */
public final class FtbTeams {
    private FtbTeams() {
    }

    private static Optional<Team> party(UUID id) {
        FTBTeamsAPI.API api = FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) {
            return Optional.empty();
        }
        return api.getManager().getTeamByID(id).filter(team -> team.isPartyTeam() && team.isValid());
    }

    /** Os membros do time de grupo; vazio se ele não existe mais ou não é de grupo. */
    public static Set<UUID> members(UUID team) {
        Optional<Team> found = party(team);
        return found.isPresent() ? found.get().getMembers() : Set.of();
    }

    public static boolean isMember(UUID team, UUID player) {
        Optional<Team> found = party(team);
        return found.isPresent() && found.get().getMembers().contains(player);
    }

    public static TeamInfo info(UUID id) {
        Optional<Team> found = party(id);
        if (found.isEmpty()) {
            return new TeamInfo(id, id.toString().substring(0, 8), 0, true);
        }
        return describe(found.get());
    }

    public static List<TeamInfo> teams() {
        FTBTeamsAPI.API api = FTBTeamsAPI.api();
        List<TeamInfo> result = new ArrayList<>();
        if (!api.isManagerLoaded()) {
            return result;
        }
        for (Team team : api.getManager().getTeams()) {
            if (team.isPartyTeam() && team.isValid()) {
                result.add(describe(team));
            }
        }
        return result;
    }

    private static TeamInfo describe(Team team) {
        // o pacote leva o nome em até 64 caracteres
        String name = team.getName().getString();
        return new TeamInfo(team.getId(), name.length() > 64 ? name.substring(0, 64) : name,
                team.getMembers().size(), false);
    }
}
