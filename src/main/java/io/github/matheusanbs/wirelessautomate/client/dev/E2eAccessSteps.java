package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eNetworkSectionSteps.chooseNetwork;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.tabletScreen;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import com.mojang.authlib.GameProfile;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.compat.ftbteams.FtbTeamsDev;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * O acesso a uma rede privada no e2e, na aba Redes do Tablet: a rede dos itens ganha convidados escolhidos pela lista
 * de sugestões (setas e Enter) e pelo nome digitado, e um time do FTB Teams (com ele no jogo); depois a lista de
 * acesso (capturas {@code 7s-tablet-acesso*}, também no tema escuro) e o ✕ de dois cliques. No fim a rede volta ao
 * que era. Três jogadores falsos entram no cache de perfis só para haver nomes a sugerir.
 */
final class E2eAccessSteps {
    private E2eAccessSteps() {
    }

    private static final String[] NAMES = {"Ana", "Bruno", "Bruna"};
    private static final String TEAM = "E2E Mineiros";
    private static GuiTheme themeBefore = GuiTheme.LIGHT;

    private static int[] counts() throws StepFailure {
        return tabletScreen().access().counts();
    }

    private static boolean usercacheHas(String name) throws Exception {
        return onServer(server -> {
            try {
                var file = server.getFile("usercache.json");
                return Files.isRegularFile(file) && Files.readString(file).contains(name);
            } catch (java.io.IOException e) {
                return false;
            }
        });
    }

    static void accessSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(chooseNetwork("acesso: "));
        list.add(new Step("acesso: jogadores e time de teste", STEP_TIMEOUT_MS, () -> onServer(server -> {
            for (String name : NAMES) {
                server.getProfileCache().add(new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
            }
            if (TeamAccess.LOADED) {
                FtbTeamsDev.createParty(server.getPlayerList().getPlayer(playerId), TEAM);
            }
            return null;
        }), () -> usercacheHas("Bruna"), () -> "o cache de jogadores não chegou ao disco"));
        list.add(new Step("acesso: a seção aparece na rede privada", STEP_TIMEOUT_MS, () -> {
        }, () -> tabletScreen().access().shown(), () -> "a seção de acesso não está à vista"));
        list.add(new Step("acesso: clicar no campo de convidar", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().access().fieldCenter(false);
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().access().suggestionCount(false) >= 3,
                () -> "sugestões " + tabletScreen().access().suggestionCount(false)));
        list.add(new Step("acesso: digitar Br", STEP_TIMEOUT_MS, () -> type("Br"),
                () -> tabletScreen().access().suggestionCount(false) == 2,
                () -> "sugestões " + tabletScreen().access().suggestionCount(false)));
        list.add(capture("7s-tablet-acesso-sugestoes"));
        list.add(clipCheck("Tablet: acesso com sugestões"));
        list.add(new Step("acesso: seta para baixo e Enter convidam", STEP_TIMEOUT_MS, () -> {
            tabletScreen().keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
            tabletScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> counts()[0] == 1, () -> "convidados " + counts()[0]));
        list.add(new Step("acesso: o nome digitado também convida", STEP_TIMEOUT_MS, () -> {
            type("ana");
            tabletScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> counts()[0] == 2, () -> "convidados " + counts()[0]));
        if (TeamAccess.LOADED) {
            list.add(new Step("acesso: clicar no campo de time", STEP_TIMEOUT_MS, () -> {
                int[] c = tabletScreen().access().fieldCenter(true);
                click(tabletScreen(), c[0], c[1]);
            }, () -> tabletScreen().access().suggestionCount(true) >= 1,
                    () -> "sugestões de time " + tabletScreen().access().suggestionCount(true)));
            list.add(new Step("acesso: o time entra pela lista", STEP_TIMEOUT_MS, () -> {
                tabletScreen().keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                tabletScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            }, () -> counts()[1] == 1, () -> "times " + counts()[1]));
        }
        list.add(capture("7s-tablet-acesso"));
        list.add(clipCheck("Tablet: lista de acesso"));
        list.add(new Step("acesso: tema escuro", STEP_TIMEOUT_MS, () -> {
            themeBefore = GuiThemes.current();
            GuiThemes.preview(GuiTheme.DARK);
        }, () -> GuiThemes.current() == GuiTheme.DARK, () -> "tema " + GuiThemes.current()));
        list.add(capture("7s-tablet-acesso-escuro"));
        list.add(new Step("acesso: tema de volta", STEP_TIMEOUT_MS, () -> GuiThemes.preview(themeBefore),
                () -> GuiThemes.current() == themeBefore, () -> "tema " + GuiThemes.current()));
        list.add(new Step("acesso: o ✕ pede dois cliques", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().access().removeCenter(false, 0);
            click(tabletScreen(), c[0], c[1]);
            click(tabletScreen(), c[0], c[1]);
        }, () -> counts()[0] == 1, () -> "convidados " + counts()[0]));
        list.add(new Step("acesso: a rede volta ao que era", STEP_TIMEOUT_MS, () -> onServer(server -> {
            NetworkSavedData data = NetworkSavedData.get(server);
            WaNetwork network = data.network(otherNetwork);
            for (UUID id : List.copyOf(network.members())) {
                network = network.withoutMember(id);
            }
            for (UUID id : List.copyOf(network.teams())) {
                network = network.withoutTeam(id);
            }
            data.update(network);
            return null;
        }), () -> counts()[0] == 0 && counts()[1] == 0, () -> "acesso " + counts()[0] + "/" + counts()[1]));
    }
}
