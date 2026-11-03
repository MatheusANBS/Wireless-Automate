package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.tabletScreen;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksProbe;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A aba Redes do Tablet no e2e: "+ Grupo" cria e abre o nome (digitar troca e Enter grava), o seletor Grupo põe a rede
 * dos itens no grupo, Pausar pausa o grupo dela (as pedras param e voltam), um segundo grupo leva a rede (ela sai do
 * primeiro, "uma rede, um grupo"), o seletor de cor (fechar o Tablet com ele aberto grava), o segundo grupo escolhido
 * com a rede dele ({@code tablet-redes*}, {@code tablet-grupo*}, também no tema escuro), o "+ pôr uma rede neste
 * grupo" do primeiro a traz de volta, a grade de ícones dele ({@code tablet-grupo-icone*}) e o "Apagar grupo" de dois
 * cliques tira o segundo grupo.
 */
final class E2eNetworksSteps {
    private E2eNetworksSteps() {
    }

    static final String GROUP_NAME = "E2E Grupo";
    static final String SECOND_GROUP = "E2E Grupo 2";
    private static GuiTheme themeBefore = GuiTheme.LIGHT;
    private static int colorBefore;
    private static int chosenColor;
    private static int colorAtOpen;
    private static long firstSearchNanos;
    private static final ResourceLocation DIAMOND = ResourceLocation.withDefaultNamespace("diamond");
    private static final ResourceLocation EMERALD = ResourceLocation.withDefaultNamespace("emerald");

    private static NetworksProbe panel() throws StepFailure {
        return tabletScreen().networks().probe();
    }

    private static void clickAt(int @Nullable [] c, String what) throws StepFailure {
        if (c == null) {
            throw new StepFailure("sem " + what + " à vista");
        }
        click(tabletScreen(), c[0], c[1]);
    }

    static void networksSteps(List<Step> list) {
        list.add(new Step("redes: aba", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.tab.networks")), "aba Redes")),
                () -> panel().selectedNetwork() != null, () -> "nenhuma rede escolhida"));
        newGroupSteps(list, GROUP_NAME);
        list.add(chooseByRow("redes: "));
        list.add(new Step("redes: o seletor Grupo põe a rede no grupo", STEP_TIMEOUT_MS, () -> {
            clickAt(panel().groupPickerCenter(), "o seletor Grupo");
            clickAt(panel().pickerRowCenter(groupId(GROUP_NAME)), "a linha do grupo");
        }, () -> onlyIn(GROUP_NAME), () -> "grupo no servidor " + group(GROUP_NAME)));
        pauseSteps(list);
        newGroupSteps(list, SECOND_GROUP);
        list.add(chooseByRow("redes: segundo grupo: "));
        list.add(new Step("redes: o segundo grupo leva a rede", STEP_TIMEOUT_MS, () -> {
            clickAt(panel().groupPickerCenter(), "o seletor Grupo");
            clickAt(panel().pickerRowCenter(groupId(SECOND_GROUP)), "a linha do segundo grupo");
        }, () -> onlyIn(SECOND_GROUP), () -> "grupos no servidor " + groupsOf()));
        list.add(capture("tablet-redes"));
        list.add(clipCheck("Tablet: Redes"));
        darkCapture(list, "tablet-redes-escuro");
        colorSteps(list);
        list.add(new Step("redes: o grupo escolhido, com a rede", STEP_TIMEOUT_MS,
                () -> clickAt(panel().groupRowCenter(groupId(SECOND_GROUP)), "a linha do segundo grupo"),
                () -> java.util.Objects.equals(panel().selectedGroup(), groupId(SECOND_GROUP))
                        && find(byMessage(Component.translatable("gui.wirelessautomate.tablet.nets.pause_group"))) != null,
                () -> "sem o grupo à direita"));
        list.add(capture("tablet-grupo"));
        list.add(clipCheck("Tablet: grupo"));
        darkCapture(list, "tablet-grupo-escuro");
        list.add(new Step("redes: + pôr uma rede neste grupo", STEP_TIMEOUT_MS, () -> {
            clickAt(panel().groupRowCenter(groupId(GROUP_NAME)), "a linha do grupo");
            clickAt(panel().addToGroupCenter(), "o link de pôr uma rede");
            clickAt(panel().pickerRowCenter(otherNetwork), "a linha da rede dos itens");
        }, () -> onlyIn(GROUP_NAME), () -> "grupos no servidor " + groupsOf()));
        iconSteps(list);
        list.add(new Step("redes: o segundo grupo sai", STEP_TIMEOUT_MS, () -> {
            clickAt(panel().groupRowCenter(groupId(SECOND_GROUP)), "a linha do segundo grupo");
            int[] remove = panel().removeCenter();
            clickAt(remove, "o Apagar grupo");
            clickAt(panel().removeCenter(), "o Apagar grupo armado");
        }, () -> groupId(SECOND_GROUP) == null, () -> "grupos " + tabletScreen().getMenu().snapshot().groups()));
    }

    /**
     * O seletor de cor da rede dos itens: o clique no slot o abre, o clique no quadrado muda a cor, Esc fecha e grava
     * (uma vez, ao fechar); o hexadecimal digitado também; fechar o Tablet com o seletor aberto também grava (e o
     * Tablet abre de novo na rede); no fim a cor de antes volta pelo servidor.
     */
    private static void colorSteps(List<Step> list) {
        list.add(new Step("redes: a cor pelo seletor", STEP_TIMEOUT_MS, () -> {
            colorBefore = serverColor();
            clickAt(panel().colorSlotCenter(), "o slot da cor");
        }, () -> panel().colorOpen(), () -> "seletor fechado"));
        list.add(capture("tablet-redes-cor"));
        darkCapture(list, "tablet-redes-cor-escuro");
        list.add(new Step("redes: o clique no quadrado muda a cor", STEP_TIMEOUT_MS,
                () -> clickAt(panel().colorSquareCenter(), "o quadrado da cor"),
                () -> (panel().colorValue() & 0xFFFFFF) != colorBefore,
                () -> "cor " + Integer.toHexString(panel().colorValue())));
        list.add(new Step("redes: Esc fecha e grava", STEP_TIMEOUT_MS, () -> {
            chosenColor = panel().colorValue() & 0xFFFFFF;
            tabletScreen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }, () -> !panel().colorOpen() && serverColor() == chosenColor,
                () -> "aberto " + panel().colorOpen() + ", servidor " + Integer.toHexString(serverColor())));
        list.add(new Step("redes: o hexadecimal", STEP_TIMEOUT_MS, () -> {
            clickAt(panel().colorSlotCenter(), "o slot da cor");
            panel().colorHexFocus();
            for (int i = 0; i < 9; i++) {
                tabletScreen().keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
            }
            type("#123456");
            tabletScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            tabletScreen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }, () -> !panel().colorOpen() && serverColor() == 0x123456,
                () -> "aberto " + panel().colorOpen() + ", servidor " + Integer.toHexString(serverColor())));
        list.add(new Step("redes: o seletor aberto de novo", STEP_TIMEOUT_MS, () -> {
            colorAtOpen = serverColor();
            clickAt(panel().colorSlotCenter(), "o slot da cor");
        }, () -> panel().colorOpen(), () -> "seletor fechado"));
        list.add(new Step("redes: o quadrado muda a cor de novo", STEP_TIMEOUT_MS,
                () -> clickAt(panel().colorSquareCenter(), "o quadrado da cor"),
                () -> (panel().colorValue() & 0xFFFFFF) != colorAtOpen,
                () -> "cor " + Integer.toHexString(panel().colorValue())));
        list.add(new Step("redes: fechar o Tablet com o seletor aberto grava", STEP_TIMEOUT_MS, () -> {
            chosenColor = panel().colorValue() & 0xFFFFFF;
            tabletScreen().onClose();
        }, () -> Minecraft.getInstance().screen == null && serverColor() == chosenColor,
                () -> "tela " + describe(Minecraft.getInstance().screen) + ", servidor " + Integer.toHexString(serverColor())));
        list.add(E2eTabletMachinesSteps.openTablet("redes: o Tablet de volta"));
        list.add(chooseNetwork("redes: de volta: "));
        list.add(new Step("redes: a cor de antes volta", STEP_TIMEOUT_MS, () -> onServer(server -> {
            NetworkSavedData data = NetworkSavedData.get(server);
            data.update(data.network(otherNetwork).withColor(colorBefore));
            return null;
        }), () -> serverColor() == colorBefore, () -> "servidor " + Integer.toHexString(serverColor())));
    }

    /**
     * A grade de ícones do grupo escolhido: o clique no slot a abre com o foco na busca, "diamond" põe o diamante na
     * primeira célula (150 ms depois da última tecla), o clique nele o grava; um item solto do JEI (pelo gancho da
     * tela; o arraste de verdade fica para o teste à mão) também; o clique no escolhido tira o ícone.
     */
    private static void iconSteps(List<Step> list) {
        list.add(new Step("grupo: abrir a grade de ícones", STEP_TIMEOUT_MS, () -> {
            openIcons();
            type("diamon");
        }, () -> {
            ResourceLocation first = panel().iconCellItem(0);
            return first != null && !first.equals(DIAMOND);
        }, () -> "célula 0: " + panel().iconCellItem(0)));
        list.add(new Step("grupo: \"diamond\" põe o diamante primeiro", STEP_TIMEOUT_MS, () -> {
            firstSearchNanos = panel().iconSearchNanos();
            type("d");
        }, () -> DIAMOND.equals(panel().iconCellItem(0)) && panel().iconCellCenter(0) != null,
                () -> "célula 0: " + panel().iconCellItem(0)));
        list.add(new Step("grupo: o tempo da busca", STEP_TIMEOUT_MS, () -> WirelessAutomate.LOGGER.info(
                "E2E: busca da grade de ícones: primeira {} µs (monta o catálogo), seguinte {} µs",
                firstSearchNanos / 1000, panel().iconSearchNanos() / 1000), () -> true, () -> ""));
        list.add(capture("tablet-grupo-icone"));
        darkCapture(list, "tablet-grupo-icone-escuro");
        list.add(new Step("grupo: escolher o diamante", STEP_TIMEOUT_MS,
                () -> clickAt(panel().iconCellCenter(0), "a célula do diamante"),
                () -> !panel().iconOpen() && iconIs(DIAMOND), () -> "ícone " + serverIcon()));
        list.add(new Step("grupo: o JEI", STEP_TIMEOUT_MS, () -> {
            openIcons();
            tabletScreen().acceptGhost(new ItemStack(Items.EMERALD));
        }, () -> !panel().iconOpen() && iconIs(EMERALD), () -> "ícone " + serverIcon()));
        list.add(new Step("grupo: tirar o ícone", STEP_TIMEOUT_MS, () -> {
            openIcons();
            if (!EMERALD.equals(panel().iconCellItem(0))) {
                throw new StepFailure("a primeira célula não é o ícone escolhido: " + panel().iconCellItem(0));
            }
            clickAt(panel().iconCellCenter(0), "a célula do escolhido");
        }, () -> !panel().iconOpen() && iconIs(null), () -> "ícone " + serverIcon()));
    }

    private static void openIcons() throws StepFailure {
        clickAt(panel().iconSlotCenter(), "o slot do ícone");
        if (!panel().iconOpen()) {
            throw new StepFailure("a grade de ícones não abriu");
        }
        panel().iconSearchFocus();
    }

    /** O ícone do grupo {@link #GROUP_NAME} é {@code id} no servidor e no snapshot da tela. */
    private static boolean iconIs(@Nullable ResourceLocation id) throws Exception {
        UUID groupId = groupId(GROUP_NAME);
        boolean shown = tabletScreen().getMenu().snapshot().groups().stream()
                .anyMatch(g -> g.id().equals(groupId) && Objects.equals(g.icon().orElse(null), id));
        return shown && Objects.equals(serverIcon(), id);
    }

    private static @Nullable ResourceLocation serverIcon() throws Exception {
        WaGroup group = group(GROUP_NAME);
        return group == null ? null : group.icon();
    }

    private static int serverColor() throws Exception {
        return onServer(server -> NetworkSavedData.get(server).network(otherNetwork).color()) & 0xFFFFFF;
    }

    /** "+ Grupo" cria com o nome livre e abre o nome; digitar troca o texto todo e Enter grava {@code name}. */
    private static void newGroupSteps(List<Step> list, String name) {
        String base = Component.translatable("gui.wirelessautomate.tablet.nets.default_group").getString();
        list.add(new Step("redes: + Grupo cria e abre o nome (" + name + ")", STEP_TIMEOUT_MS,
                () -> clickAt(panel().newGroupCenter(), "o + Grupo"), () -> {
                    EditBox box = panel().titleBox();
                    UUID chosen = panel().selectedGroup();
                    return box != null && box.isFocused() && chosen != null && tabletScreen().getMenu().snapshot()
                            .groups().stream().anyMatch(g -> g.id().equals(chosen) && g.name().startsWith(base));
                }, () -> "grupos " + tabletScreen().getMenu().snapshot().groups()));
        list.add(new Step("redes: o nome do grupo (" + name + ")", STEP_TIMEOUT_MS, () -> {
            type(name);
            tabletScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> group(name) != null, () -> "grupos no servidor " + groupsOf()));
    }

    /** Pausar (o grupo da rede) pausa a rede dos itens: as pedras param; o mesmo botão retoma e elas chegam. */
    private static void pauseSteps(List<Step> list) {
        list.add(new Step("redes: Pausar pausa o grupo da rede", STEP_TIMEOUT_MS,
                () -> clickAt(panel().pauseCenter(), "o Pausar"),
                () -> onServer(server -> NetworkSavedData.get(server).isPaused(otherNetwork))
                        && tabletScreen().getMenu().snapshot().groups().stream()
                                .anyMatch(g -> g.name().equals(GROUP_NAME) && g.paused()),
                () -> "pausada no servidor: " + onServer(server -> NetworkSavedData.get(server).isPaused(otherNetwork))));
        list.add(capture("tablet-redes-pausada"));
        list.add(new Step("rede pausada: pedras param", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ((Container) server.overworld().getBlockEntity(chestA)).setItem(0, new ItemStack(Items.STONE, 64));
            return null;
        }), () -> {
            if (onServer(server -> count(server, chestB, Items.STONE)) != 192) {
                throw new StepFailure("pedras chegaram a B com a rede pausada");
            }
            return stepTicks >= 40 && onServer(server -> count(server, chestA, Items.STONE) == 64);
        }, () -> onServer(server -> "A=" + count(server, chestA, Items.STONE) + " B=" + count(server, chestB, Items.STONE))));
        list.add(new Step("retomar: pedras chegam", 30_000, () -> clickAt(panel().pauseCenter(), "o Retomar"),
                () -> onServer(server -> !NetworkSavedData.get(server).isPaused(otherNetwork)
                        && count(server, chestA, Items.STONE) == 0 && count(server, chestB, Items.STONE) == 256),
                () -> onServer(server -> "pausada " + NetworkSavedData.get(server).isPaused(otherNetwork) + ", A="
                        + count(server, chestA, Items.STONE) + " B=" + count(server, chestB, Items.STONE))));
    }

    private static void darkCapture(List<Step> list, String file) {
        E2eVariants.escuro(list, () -> {
            list.add(new Step("redes: tema escuro (" + file + ")", STEP_TIMEOUT_MS, () -> {
                themeBefore = GuiThemes.current();
                GuiThemes.preview(GuiTheme.DARK);
            }, () -> GuiThemes.current() == GuiTheme.DARK, () -> "tema " + GuiThemes.current()));
            list.add(capture(file));
            list.add(new Step("redes: tema de volta (" + file + ")", STEP_TIMEOUT_MS,
                    () -> GuiThemes.preview(themeBefore),
                    () -> GuiThemes.current() == themeBefore, () -> "tema " + GuiThemes.current()));
        });
    }

    /** Clica na linha da rede dos itens na coluna. */
    private static Step chooseByRow(String label) {
        return new Step(label + "escolher a rede dos itens", STEP_TIMEOUT_MS,
                () -> clickAt(panel().networkRowCenter(otherNetwork), "a linha da rede dos itens"),
                () -> otherNetwork.equals(panel().selectedNetwork()), () -> "escolhida " + panel().selectedNetwork());
    }

    /** Escolhe a rede dos itens na aba Redes pelo gancho (sem clicar). */
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

    /** O id do grupo {@code name} no snapshot da tela, ou {@code null}. */
    static @Nullable UUID groupId(String name) throws StepFailure {
        for (TabletSnapshot.GroupView group : tabletScreen().getMenu().snapshot().groups()) {
            if (group.name().equals(name)) {
                return group.id();
            }
        }
        return null;
    }

    /** O grupo {@code name} no servidor, ou {@code null}. */
    static @Nullable WaGroup group(String name) throws Exception {
        return onServer(server -> NetworkSavedData.get(server).groups().stream()
                .filter(g -> g.name().equals(name)).findFirst().orElse(null));
    }

    /** A rede dos itens está só no grupo {@code name}, no servidor. */
    private static boolean onlyIn(String name) throws Exception {
        return onServer(server -> {
            List<WaGroup> with = NetworkSavedData.get(server).groups().stream()
                    .filter(g -> g.networks().contains(otherNetwork)).toList();
            return with.size() == 1 && with.get(0).name().equals(name);
        });
    }

    private static List<String> groupsOf() throws Exception {
        return onServer(server -> NetworkSavedData.get(server).groups().stream()
                .map(g -> g.name() + g.networks()).toList());
    }
}
