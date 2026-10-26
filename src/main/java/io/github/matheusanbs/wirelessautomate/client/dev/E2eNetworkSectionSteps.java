package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.tabletScreen;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A aba Redes com os grupos de redes como seções no e2e: um segundo grupo com a rede dos itens (que fica em dois
 * grupos), a rede escolhida com a dica "também em …" nas duas seções ({@code 7p-tablet-redes-grupos}, também no
 * tema escuro e, nos passos da visão por máquinas, em português) e o cabeçalho de um grupo escolhido, com o
 * detalhe dele à direita ({@code 7p-tablet-redes-grupo}). No fim, o segundo grupo sai.
 */
final class E2eNetworkSectionSteps {
    private E2eNetworkSectionSteps() {
    }

    static final String SECOND_GROUP = "E2E Grupo 2";
    private static GuiTheme themeBefore = GuiTheme.LIGHT;

    static void sectionSteps(List<Step> list) {
        list.add(new Step("redes: segundo grupo", STEP_TIMEOUT_MS, () -> {
            click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.group.new")), "Novo grupo"));
            type(SECOND_GROUP);
            tabletScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> groupId(SECOND_GROUP) != null && tabletScreen().groupNetworkCenter(otherNetwork) != null,
                () -> "grupos " + tabletScreen().getMenu().snapshot().groups()));
        list.add(new Step("redes: a rede dos itens também no segundo grupo", STEP_TIMEOUT_MS, () -> {
            int[] box = tabletScreen().groupNetworkCenter(otherNetwork);
            click(tabletScreen(), box[0], box[1]);
        }, () -> tabletScreen().getMenu().snapshot().groups().stream()
                .filter(g -> g.networks().contains(otherNetwork)).count() == 2,
                () -> "grupos " + tabletScreen().getMenu().snapshot().groups()));
        list.add(chooseNetwork("redes: "));
        list.add(capture("7p-tablet-redes-grupos"));
        list.add(clipCheck("Tablet: Redes com grupos"));
        E2eVariants.escuro(list, () -> {
            list.add(new Step("redes: tema escuro", STEP_TIMEOUT_MS, () -> {
                themeBefore = GuiThemes.current();
                GuiThemes.preview(GuiTheme.DARK);
            }, () -> GuiThemes.current() == GuiTheme.DARK, () -> "tema " + GuiThemes.current()));
            list.add(capture("7p-tablet-redes-grupos-escuro"));
            list.add(new Step("redes: tema de volta", STEP_TIMEOUT_MS, () -> GuiThemes.preview(themeBefore),
                    () -> GuiThemes.current() == themeBefore, () -> "tema " + GuiThemes.current()));
        });
        list.add(new Step("redes: o cabeçalho escolhe o grupo", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().groupRowCenter(groupId(E2eTabletSteps.GROUP_NAME));
            if (c == null) {
                throw new StepFailure("sem o cabeçalho do grupo");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().groupNetworkCenter(otherNetwork) != null
                && find(byMessage(Component.translatable("gui.wirelessautomate.tablet.group.pause"))) != null,
                () -> "sem o detalhe do grupo"));
        list.add(capture("7p-tablet-redes-grupo"));
        list.add(clipCheck("Tablet: Redes com o grupo escolhido"));
        list.add(new Step("redes: o segundo grupo sai", STEP_TIMEOUT_MS, () -> onServer(server -> {
            NetworkSavedData data = NetworkSavedData.get(server);
            for (WaGroup group : List.copyOf(data.groups())) {
                if (group.name().equals(SECOND_GROUP)) {
                    data.removeGroup(group.id());
                }
            }
            return null;
        }), () -> groupId(SECOND_GROUP) == null, () -> "segundo grupo ainda na tela"));
    }

    /** Escolhe a rede dos itens na lista da aba Redes. */
    static Step chooseNetwork(String label) {
        return new Step(label + "escolher a rede dos itens", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            if (screen.currentTab() != TabletScreen.Tab.NETWORKS) {
                screen.previewTab(TabletScreen.Tab.NETWORKS);
            }
            List<TabletSnapshot.NetworkView> networks = screen.getMenu().snapshot().networks();
            for (int i = 0; i < networks.size(); i++) {
                if (networks.get(i).id().equals(otherNetwork)) {
                    screen.previewNetwork(i);
                }
            }
        }, () -> stepTicks >= 2 && tabletScreen().currentTab() == TabletScreen.Tab.NETWORKS,
                () -> "aba " + tabletScreen().currentTab());
    }

    static @Nullable UUID groupId(String name) throws StepFailure {
        for (TabletSnapshot.GroupView group : tabletScreen().getMenu().snapshot().groups()) {
            if (group.name().equals(name)) {
                return group.id();
            }
        }
        return null;
    }
}
