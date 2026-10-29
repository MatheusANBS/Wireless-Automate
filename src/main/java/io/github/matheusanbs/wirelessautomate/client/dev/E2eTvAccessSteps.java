package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import com.mojang.authlib.GameProfile;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.access.AccessPanel;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.compat.ftbteams.FtbTeamsDev;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess;
import io.github.matheusanbs.wirelessautomate.tv.TvAccess;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * A aba Acesso da Tela de Sinal no e2e: o dono convida um jogador pelo nome digitado (um perfil falso no cache do
 * servidor, só para haver um nome) e, com o FTB Teams no jogo, um time pela lista de sugestões; a foto
 * {@code tela-acesso} (e {@code tela-acesso-escuro}, com a variante); o ✕ de dois cliques tira o convidado e a lista
 * volta vazia no fim.
 */
final class E2eTvAccessSteps {
    private static final String GUEST = "Carla";
    private static final String TEAM = "E2E Telas";

    private E2eTvAccessSteps() {
    }

    private static AccessPanel panel() throws StepFailure {
        AccessPanel panel = E2eTvSteps.screen().access();
        if (panel == null) {
            throw new StepFailure("a aba Acesso não existe para o dono");
        }
        return panel;
    }

    private static int[] counts() throws StepFailure {
        return panel().counts();
    }

    private static void clickAt(int[] point) throws StepFailure {
        click(E2eTvSteps.screen(), point[0], point[1]);
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

    static void accessSteps(List<Step> list, Supplier<BlockPos> tv) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(new Step("Tela acesso: um jogador e um time para convidar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.getProfileCache().add(new GameProfile(UUID.nameUUIDFromBytes(GUEST.getBytes()), GUEST));
            if (TeamAccess.LOADED) {
                FtbTeamsDev.createParty(server.getPlayerList().getPlayer(playerId), TEAM);
            }
            return null;
        }), () -> usercacheHas(GUEST), () -> "o cache de jogadores não chegou ao disco"));
        list.add(E2eTvSteps.tab("Tela acesso: a aba Acesso", TvScreen.Tab.ACCESS));
        list.add(new Step("Tela acesso: o painel à vista", STEP_TIMEOUT_MS, () -> {
        }, () -> panel().shown(), () -> "o painel de acesso não está à vista"));
        list.add(new Step("Tela acesso: o nome digitado convida", STEP_TIMEOUT_MS, () -> {
            clickAt(panel().fieldCenter(false));
            type(GUEST.toLowerCase());
            E2eTvSteps.screen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> counts()[0] == 1, () -> "convidados " + counts()[0]));
        if (TeamAccess.LOADED) {
            list.add(new Step("Tela acesso: clicar no campo de time", STEP_TIMEOUT_MS,
                    () -> clickAt(panel().fieldCenter(true)),
                    () -> panel().suggestionCount(true) >= 1, () -> "sugestões de time " + panel().suggestionCount(true)));
            list.add(new Step("Tela acesso: o time entra pela lista", STEP_TIMEOUT_MS, () -> {
                E2eTvSteps.screen().keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                E2eTvSteps.screen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            }, () -> counts()[1] == 1, () -> "times " + counts()[1]));
        }
        list.add(new Step("Tela acesso: o convidado está na lista do servidor", STEP_TIMEOUT_MS, () -> {
        }, () -> onServer(server -> server.overworld().getBlockEntity(tv.get()) instanceof TvBlockEntity entity
                && entity.access().players().size() == 1), () -> "a lista da Tela no servidor"));
        E2eTvSteps.shot(list, "tela-acesso");
        E2eVariants.escuro(list, () -> {
            list.add(E2eDarkSteps.themeClick("Tela acesso: escuro", GuiTheme.DARK));
            E2eTvSteps.shot(list, "tela-acesso-escuro");
            list.add(E2eDarkSteps.themeClick("Tela acesso: claro de novo", GuiTheme.LIGHT));
        });
        list.add(new Step("Tela acesso: o ✕ pede dois cliques", STEP_TIMEOUT_MS, () -> {
            int[] at = panel().removeCenter(false, 0);
            clickAt(at);
            clickAt(at);
        }, () -> counts()[0] == 0, () -> "convidados " + counts()[0]));
        list.add(new Step("Tela acesso: a lista volta vazia", STEP_TIMEOUT_MS, () -> onServer(server -> {
            if (server.overworld().getBlockEntity(tv.get()) instanceof TvBlockEntity entity) {
                entity.setAccess(TvAccess.EMPTY);
            }
            return null;
        }), () -> onServer(server -> server.overworld().getBlockEntity(tv.get()) instanceof TvBlockEntity entity
                && entity.access().isEmpty()), () -> "a lista da Tela no servidor"));
        list.add(E2eTvSteps.tab("Tela acesso: de volta ao Tamanho", TvScreen.Tab.SIZE));
    }
}
