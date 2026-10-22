package io.github.matheusanbs.wirelessautomate.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * O que os GameTests de times fazem com a API do FTB Teams. Fica separado de {@link TeamGameTests} porque o
 * NeoForge inspeciona a classe de teste por reflexão, mesmo sem o FTB: nenhum tipo dele nas assinaturas de lá.
 */
final class TeamTestSupport {
    private TeamTestSupport() {
    }

    static boolean managerLoaded() {
        return FTBTeamsAPI.api().isManagerLoaded();
    }

    /** Um time de grupo feito pelo jogador (que passa a ser o dono e o primeiro membro). */
    static UUID createParty(ServerPlayer owner, String name) throws CommandSyntaxException {
        Team team = FTBTeamsAPI.api().getManager().createPartyTeam(owner, name, null, null);
        return team.getId();
    }

    /** O jogador entra no time, sem convite (o que o comando de entrar faz depois de um convite aceito). */
    static void join(UUID team, UUID player, String name) throws CommandSyntaxException {
        ((PartyTeam) FTBTeamsAPI.api().getManager().getTeamByID(team).orElseThrow())
                .join(null, new GameProfile(player, name));
    }

    static void leave(UUID team, UUID player) throws CommandSyntaxException {
        ((PartyTeam) FTBTeamsAPI.api().getManager().getTeamByID(team).orElseThrow()).leave(player);
    }

    /** Desfaz o time (o dono sai por último ou o servidor o desfaz). */
    static void disband(MinecraftServer server, UUID team) throws CommandSyntaxException {
        ((PartyTeam) FTBTeamsAPI.api().getManager().getTeamByID(team).orElseThrow())
                .forceDisband(server.createCommandSourceStack());
    }

    /** O id do time pessoal do jogador, que nunca é um time de rede. */
    static UUID personalTeam(UUID player) {
        return FTBTeamsAPI.api().getManager().getPlayerTeamForPlayerID(player).orElseThrow().getId();
    }
}
