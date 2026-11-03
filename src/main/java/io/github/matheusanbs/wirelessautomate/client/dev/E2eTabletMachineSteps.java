package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletMachinesSteps.EXCEPTION;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletMachinesSteps.NAME;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletMachinesSteps.closeKeeping;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletMachinesSteps.node;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletMachinesSteps.openTablet;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.tabletScreen;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.client.tablet.machine.MachineProbe;
import io.github.matheusanbs.wirelessautomate.menu.EditTargetRef;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;

/**
 * A aba Máquina nova do Tablet no e2e, sobre o mundo dos passos de máquinas (um roteador Elite com dez fornalhas
 * vinculadas): a lista pelo servidor, a busca pelo teclado, o clique escolhe a máquina e o cabeçalho a mostra; as
 * capturas {@code maquina} (também {@code maquina-escuro} e {@code maquina-pt}); o Tablet reaberto volta na aba em que
 * foi fechado (a decisão 10) e o clique com o Tablet numa máquina o abre na aba Máquina com ela escolhida. Roda com a Lista
 * antiga desligada ({@link TabletScreen#legacyList}) e a religa no fim, para os passos antigos que vêm depois.
 */
final class E2eTabletMachineSteps {
    private E2eTabletMachineSteps() {
    }

    private static GuiTheme themeBefore = GuiTheme.LIGHT;

    private static MachineProbe probe() throws StepFailure {
        return tabletScreen().machine().probe();
    }

    static void tabSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(new Step("Máquina: a aba nova no lugar da Lista antiga", STEP_TIMEOUT_MS, () -> {
            TabletScreen.legacyList(false);
            TabletScreen screen = tabletScreen();
            screen.previewTab(TabletScreen.Tab.LIST);
            screen.setFocused(screen.machine().box());
        }, () -> tabletScreen().currentTab() == TabletScreen.Tab.LIST && probe().pageActive() && probe().rowCount() > 0
                && probe().searchBox() != null && probe().searchBox().visible,
                () -> "página ativa " + probe().pageActive() + ", linhas " + probe().rowCount()));
        list.add(new Step("Máquina: a busca pelo teclado acha o roteador", STEP_TIMEOUT_MS, () -> {
            tabletScreen().setFocused(probe().searchBox());
            type(NAME);
        }, () -> tabletScreen().getMenu().snapshot().query().text().equals(NAME)
                && probe().machineCenter(node(), EXCEPTION) != null,
                () -> "consulta " + tabletScreen().getMenu().snapshot().query() + ", linhas " + probe().rowCount()));
        list.add(new Step("Máquina: o clique escolhe a máquina e o cabeçalho a mostra", STEP_TIMEOUT_MS, () -> {
            int[] c = probe().machineCenter(node(), EXCEPTION);
            if (c == null) {
                throw new StepFailure("a máquina " + EXCEPTION + " fora da lista");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> probe().serverChose(node(), EXCEPTION) && probe().chosen(node(), EXCEPTION)
                && probe().headerShown() && !probe().headerTitle().isEmpty() && stepTicks >= 4,
                () -> "servidor " + probe().serverChose(node(), EXCEPTION) + ", faixa " + probe().chosen(node(), EXCEPTION)
                        + ", cabeçalho \"" + probe().headerTitle() + "\""));
        list.add(new Step("Máquina: o veredito do servidor chegou ao menu", STEP_TIMEOUT_MS, () -> moveMouse(0, 0),
                () -> tabletScreen().panel() != null, () -> "sem o painel"));
        list.add(capture("maquina"));
        list.add(clipCheck("Tablet: Máquina"));
        E2eVariants.escuro(list, () -> {
            list.add(new Step("Máquina: tema escuro", STEP_TIMEOUT_MS, () -> {
                themeBefore = GuiThemes.current();
                GuiThemes.preview(GuiTheme.DARK);
            }, () -> GuiThemes.current() == GuiTheme.DARK, () -> "tema " + GuiThemes.current()));
            list.add(capture("maquina-escuro"));
            list.add(new Step("Máquina: tema de volta", STEP_TIMEOUT_MS, () -> GuiThemes.preview(themeBefore),
                    () -> GuiThemes.current() == themeBefore, () -> "tema " + GuiThemes.current()));
        });
        E2eVariants.pt(list, () -> {
            list.add(closeKeeping("Máquina: fechar o Tablet para o português"));
            list.add(language("pt_br"));
            list.add(openTablet("pt: Máquina: abrir o Tablet"));
            list.add(new Step("pt: Máquina: a escolhida continua", STEP_TIMEOUT_MS, () -> moveMouse(0, 0),
                    () -> probe().chosen(node(), EXCEPTION) && probe().headerShown(),
                    () -> "faixa " + probe().chosen(node(), EXCEPTION)));
            list.add(capture("maquina-pt"));
            list.add(clipCheck("pt: Tablet: Máquina"));
            list.add(closeKeeping("pt: Máquina: fechar o Tablet"));
            list.add(language("en_us"));
            list.add(openTablet("Máquina: abrir o Tablet de novo"));
        });
        rememberedTabSteps(list);
        clickWithTabletSteps(list, playerId);
        list.add(new Step("Máquina: Esc limpa a busca (e não fecha o Tablet)", STEP_TIMEOUT_MS,
                () -> tabletScreen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0),
                () -> Minecraft.getInstance().screen == tabletScreen()
                        && tabletScreen().getMenu().snapshot().query().text().isEmpty()
                        && probe().searchBox().getValue().isEmpty(),
                () -> "consulta " + tabletScreen().getMenu().snapshot().query()));
        list.add(new Step("Máquina: a Lista antiga volta para os passos antigos", STEP_TIMEOUT_MS,
                () -> TabletScreen.legacyList(true), () -> true, () -> "legacyList"));
    }

    /** A decisão 10: o Tablet fechado em Fluxos reabre em Fluxos (e a volta à Máquina fica lembrada). */
    private static void rememberedTabSteps(List<Step> list) {
        list.add(new Step("Máquina: ir para Fluxos", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewTab(TabletScreen.Tab.FLOWS),
                () -> tabletScreen().currentTab() == TabletScreen.Tab.FLOWS, () -> "aba " + tabletScreen().currentTab()));
        list.add(closeKeeping("Máquina: fechar o Tablet em Fluxos"));
        list.add(openTablet("Máquina: reabrir o Tablet"));
        list.add(new Step("Máquina: reabre em Fluxos", STEP_TIMEOUT_MS, () -> {
        }, () -> tabletScreen().currentTab() == TabletScreen.Tab.FLOWS, () -> "aba " + tabletScreen().currentTab()));
        list.add(new Step("Máquina: de volta à aba Máquina", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewTab(TabletScreen.Tab.LIST),
                () -> tabletScreen().currentTab() == TabletScreen.Tab.LIST && probe().chosen(node(), EXCEPTION),
                () -> "aba " + tabletScreen().currentTab() + ", escolhida " + probe().chosen(node(), EXCEPTION)));
    }

    /** O clique com o Tablet numa máquina (o servidor abre o menu com o nó e o alvo): abre em Máquina com ela escolhida. */
    private static void clickWithTabletSteps(List<Step> list, UUID playerId) {
        int target = EXCEPTION + 1;
        list.add(closeKeeping("Máquina: fechar o Tablet para o clique com ele"));
        list.add(new Step("Máquina: o clique com o Tablet na máquina " + target, STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            // a fornalha de índice `target` é a de número target - FIRST_FURNACE da fileira
            BlockPos pos = chestB.offset(target - E2eTabletMachinesSteps.FIRST_FURNACE - 2, 0, -3);
            TabletMenu.open(player, node(), new EditTargetRef(EditTarget.machine(target), pos, null));
            return null;
        }), () -> Minecraft.getInstance().screen instanceof TabletScreen screen
                && screen.currentTab() == TabletScreen.Tab.LIST && screen.machine().probe().chosen(node(), target)
                && screen.machine().probe().headerShown(),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("Máquina: a lista rolou até a escolhida", STEP_TIMEOUT_MS, () -> {
        }, () -> probe().machineCenter(node(), target) != null && probe().serverChose(node(), target),
                () -> "servidor " + probe().serverChose(node(), target)));
    }
}
