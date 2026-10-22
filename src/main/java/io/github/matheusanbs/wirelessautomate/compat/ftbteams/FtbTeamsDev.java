package io.github.matheusanbs.wirelessautomate.compat.ftbteams;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.server.level.ServerPlayer;

/**
 * O que o e2e de desenvolvimento ({@code client/dev}) faz com o FTB Teams: criar um time de grupo para a tela de
 * acesso ter o que mostrar. Só é chamado com o FTB presente, nunca em jogo normal.
 */
public final class FtbTeamsDev {
    private FtbTeamsDev() {
    }

    /** Um time de grupo do jogador, ou nada se o gerenciador de times não está carregado ou o FTB recusa. */
    public static void createParty(ServerPlayer owner, String name) {
        FTBTeamsAPI.API api = FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) {
            return;
        }
        try {
            api.getManager().createPartyTeam(owner, name, null, null);
        } catch (CommandSyntaxException e) {
            WirelessAutomate.LOGGER.warn("E2E: o FTB Teams recusou o time {}: {}", name, e.getMessage());
        }
    }
}
