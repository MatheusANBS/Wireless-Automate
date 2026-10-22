package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.listClipTooltip;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.tabletScreen;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderer;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletList;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A Lista do Tablet em árvore no e2e: um roteador Elite com o Upgrade de Alcance, dois baús que movem de verdade (um
 * extrai, o outro insere) e dez fornalhas vinculadas (uma com exceção, outra com rótulo) abre na Lista; escolher uma
 * máquina põe ela como alvo do painel; o Olho a acende no mundo; ↓ e Enter escolhem a seguinte; "Mostrar todas" e
 * "Mostrar menos"; ← e → recolhem e abrem o grupo; voltar à busca devolve as setas ao texto; e as capturas em
 * inglês, no tema escuro e em português ({@code 7l-tablet-arvore*}, {@code 7m-tablet-mostrar-todas}), com a vazão
 * dos baús na cor do tipo. No Mapa, o roteador escolhido mostra as máquinas dele, e clicar numa fornalha a põe como
 * alvo do painel ({@code 7n-tablet-mapa-maquinas}, também no tema escuro).
 */
final class E2eTabletTreeSteps {
    private E2eTabletTreeSteps() {
    }

    private static final String NAME = "E2E Arvore";
    private static final int FURNACES = 10;
    /** Os baús vêm antes (índices 0 e 1): as fornalhas começam no índice 2. */
    private static final int FIRST_FURNACE = 2;
    /** A fornalha com exceção, a primeira do grupo ("#1"): fica à vista logo abaixo dos baús. */
    private static final int EXCEPTION = FIRST_FURNACE;
    private static BlockPos chest = BlockPos.ZERO;
    private static BlockPos router = BlockPos.ZERO;
    private static BlockPos source = BlockPos.ZERO;
    private static BlockPos sink = BlockPos.ZERO;
    private static final List<BlockPos> furnaces = new ArrayList<>();
    private static GuiTheme themeBefore = GuiTheme.LIGHT;
    private static int furnaceGroup = -1;

    static void treeSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(setUp(playerId));
        openTreeSteps(list, "árvore: ");
        list.add(chooseMachine("árvore: ", EXCEPTION));
        list.add(new Step("árvore: o Olho acende a máquina", STEP_TIMEOUT_MS, () -> {
            int[] c = center(TabletList.TreePart.EYE, ListTree.Kind.MACHINE, EXCEPTION);
            if (c == null) {
                throw new StepFailure("sem o Olho da fornalha #1");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> HighlightRenderer.isLit(Level.OVERWORLD, furnaces.get(EXCEPTION - FIRST_FURNACE)), () -> "acesas " + HighlightRenderer.count()));
        list.add(flowingCapture("7l-tablet-arvore"));
        list.add(clipCheck("Tablet: árvore"));
        list.add(listClipTooltip("Tablet: árvore"));
        list.add(new Step("árvore: tema escuro", STEP_TIMEOUT_MS, () -> {
            themeBefore = GuiThemes.current();
            GuiThemes.preview(GuiTheme.DARK);
        }, () -> GuiThemes.current() == GuiTheme.DARK, () -> "tema " + GuiThemes.current()));
        list.add(flowingCapture("7l-tablet-arvore-escuro"));
        list.add(new Step("árvore: tema de volta", STEP_TIMEOUT_MS, () -> GuiThemes.preview(themeBefore),
                () -> GuiThemes.current() == themeBefore, () -> "tema " + GuiThemes.current()));
        list.add(new Step("árvore: ↓ e Enter escolhem a máquina seguinte", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> EditTarget.machine(EXCEPTION + 1).equals(panelTarget()), () -> "alvo " + panelTarget()));
        showAllSteps(list);
        arrowSteps(list);
        reopenSteps(list);
        renameSteps(list);
        list.add(new Step("árvore: o ▸ fecha o roteador", STEP_TIMEOUT_MS, () -> {
            int[] c = center(TabletList.TreePart.TOGGLE, ListTree.Kind.NODE, 0);
            click(tabletScreen(), c[0], c[1]);
        }, () -> rows().isEmpty(), () -> "linhas " + rows().size()));
        mapSteps(list);
        portugueseSteps(list);
        list.add(tearDown());
    }

    /**
     * Renomear na Lista: dois cliques no nome do roteador abrem a caixa, o texto e o Enter dão o nome (conferido no
     * servidor); o mesmo na linha da presa da árvore ({@code 7s-tablet-renomear-roteador} e
     * {@code 7s-tablet-renomear-presa}, com a caixa aberta). Depois, os dois nomes voltam a vazio.
     */
    private static void renameSteps(List<Step> list) {
        renameStep(list, "roteador", NAME + " Casa", ListTree.Kind.NODE, 0, 6);
        renameStep(list, "presa", "Fornalha Mestra", ListTree.Kind.ATTACHED, 0, 6);
        list.add(new Step("renomear: os nomes voltam", STEP_TIMEOUT_MS, () -> onServer(server -> {
            router(server, router).setName(NAME);
            router(server, router).setAttachedLabel("");
            return null;
        }), () -> tabletScreen().getMenu().snapshot().nodes().stream().anyMatch(n -> n.key().equals(node())
                && NAME.equals(n.name()) && n.attachedLabel().isEmpty()), () -> "nomes não voltaram"));
    }

    private static void renameStep(List<Step> list, String what, String name, ListTree.Kind kind, int id, int dy) {
        boolean router = kind == ListTree.Kind.NODE;
        list.add(new Step("renomear: dois cliques no nome (" + what + ")", STEP_TIMEOUT_MS, () -> {
            int[] c = center(TabletList.TreePart.LINE, kind, id);
            if (c == null) {
                throw new StepFailure("sem a linha do " + what);
            }
            click(tabletScreen(), c[0], c[1] - dy);
            click(tabletScreen(), c[0], c[1] - dy);
        }, () -> tabletScreen().list().renaming() && stepTicks >= 3, () -> "a caixa do nome não abriu"));
        list.add(new Step("renomear: digitar o nome (" + what + ")", STEP_TIMEOUT_MS, () -> {
            for (int i = 0; i < 40; i++) {
                tabletScreen().keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
            }
            type(name);
        }, () -> stepTicks >= 3, () -> ""));
        list.add(capture("7s-tablet-renomear-" + what));
        list.add(new Step("renomear: Enter salva (" + what + ")", STEP_TIMEOUT_MS,
                () -> tabletScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0),
                () -> !tabletScreen().list().renaming() && onServer(server -> {
                    var be = router(server, E2eTabletTreeSteps.router);
                    return name.equals(router ? be.name() : be.attachedLabel());
                }), () -> "o servidor não recebeu o nome"));
    }

    /**
     * O Mapa com o roteador escolhido: as máquinas aparecem (a roda aproxima um passo, para as fornalhas vizinhas não
     * se encostarem), o clique numa fornalha a põe como alvo do painel, as capturas nos dois temas, e o
     * clique no vazio larga o roteador sem deixar ponto nem linha.
     */
    private static void mapSteps(List<Step> list) {
        int machine = FIRST_FURNACE + 5;
        list.add(new Step("mapa: abrir a aba com as máquinas do roteador", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            screen.previewTab(TabletScreen.Tab.MAP);
        }, () -> node().equals(tabletScreen().list().chosen()) && tabletScreen().mapMachineCenter(EXCEPTION) != null,
                () -> "sem as máquinas no mapa"));
        list.add(new Step("mapa: aproximar", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().mapMachineCenter(EXCEPTION);
            tabletScreen().mouseScrolled(c[0], c[1], 0, 1);
        }, () -> stepTicks >= 2 && tabletScreen().mapMachineCenter(machine) != null, () -> "sem a fornalha no mapa"));
        list.add(new Step("mapa: clicar na fornalha a põe como alvo", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().mapMachineCenter(machine);
            click(tabletScreen(), c[0], c[1]);
        }, () -> EditTarget.machine(machine).equals(panelTarget()) && stepTicks >= 4, () -> "alvo " + panelTarget()));
        list.add(capture("7n-tablet-mapa-maquinas"));
        list.add(clipCheck("Tablet: mapa com as máquinas"));
        list.add(new Step("mapa: tema escuro", STEP_TIMEOUT_MS, () -> {
            themeBefore = GuiThemes.current();
            GuiThemes.preview(GuiTheme.DARK);
        }, () -> GuiThemes.current() == GuiTheme.DARK, () -> "tema " + GuiThemes.current()));
        list.add(capture("7n-tablet-mapa-maquinas-escuro"));
        list.add(new Step("mapa: clicar no vazio larga o roteador, sem ponto nem linha", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().mapEmptyPoint();
            click(tabletScreen(), c[0], c[1]);
        }, () -> stepTicks >= 2 && tabletScreen().list().chosen() == null && tabletScreen().mapMachinesDrawn() == 0,
                () -> "escolhido " + tabletScreen().list().chosen() + ", desenha " + tabletScreen().mapMachinesDrawn()));
        list.add(new Step("mapa: arrastar move o mapa e o Centralizar aparece", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            int[] c = screen.mapEmptyPoint();
            screen.mouseClicked(c[0], c[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
            screen.mouseDragged(c[0] + 30, c[1] - 20, GLFW.GLFW_MOUSE_BUTTON_LEFT, 30, -20);
            screen.mouseReleased(c[0] + 30, c[1] - 20, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }, () -> stepTicks >= 2 && tabletScreen().mapPan()[0] != 0 && tabletScreen().list().chosen() == null
                && find(byMessage(Component.translatable("gui.wirelessautomate.tablet.map.recenter"))) != null,
                () -> "pan " + java.util.Arrays.toString(tabletScreen().mapPan())));
        list.add(capture("7n-tablet-mapa-arrastado"));
        list.add(clipCheck("Tablet: mapa arrastado"));
        list.add(new Step("mapa: o Centralizar volta ao jogador", STEP_TIMEOUT_MS, () -> click(widget(
                byMessage(Component.translatable("gui.wirelessautomate.tablet.map.recenter")), "Centralizar")),
                () -> tabletScreen().mapPan()[0] == 0 && tabletScreen().mapPan()[1] == 0,
                () -> "pan " + java.util.Arrays.toString(tabletScreen().mapPan())));
        list.add(new Step("mapa: tema de volta e a Lista", STEP_TIMEOUT_MS, () -> {
            GuiThemes.preview(themeBefore);
            tabletScreen().previewTab(TabletScreen.Tab.LIST);
        }, () -> GuiThemes.current() == themeBefore && tabletScreen().currentTab() == TabletScreen.Tab.LIST,
                () -> "tema " + GuiThemes.current()));
    }

    /** "Mostrar todas" no fim do grupo das fornalhas, a captura com o "Mostrar menos" à vista e o "Mostrar menos". */
    private static void showAllSteps(List<Step> list) {
        list.add(new Step("árvore: Mostrar todas", STEP_TIMEOUT_MS, () -> {
            // o "mais N" fica embaixo da vista: a roda desce a lista até ele
            int[] c = scrollUntil(-1, () -> center(TabletList.TreePart.LINE, ListTree.Kind.MORE, furnaceGroup));
            if (c == null) {
                throw new StepFailure("sem o \"mais N\"");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> furnaceRows() == FURNACES, () -> "fornalhas na página " + furnaceRows()));
        list.add(new Step("árvore: rolar até o grupo", STEP_TIMEOUT_MS,
                () -> scrollUntil(1, () -> center(TabletList.TreePart.FEW, ListTree.Kind.GROUP, furnaceGroup)),
                () -> center(TabletList.TreePart.FEW, ListTree.Kind.GROUP, furnaceGroup) != null,
                () -> "sem o Mostrar menos na vista"));
        list.add(capture("7m-tablet-mostrar-todas"));
        list.add(clipCheck("Tablet: mostrar todas"));
        list.add(new Step("árvore: Mostrar menos", STEP_TIMEOUT_MS, () -> {
            int[] c = center(TabletList.TreePart.FEW, ListTree.Kind.GROUP, furnaceGroup);
            click(tabletScreen(), c[0], c[1]);
        }, () -> furnaceRows() == 8, () -> "fornalhas na página " + furnaceRows()));
    }

    /** ← ← recolhe o grupo das fornalhas (da máquina sobe ao grupo), → o abre; clicar na busca devolve ← ao texto. */
    private static void arrowSteps(List<Step> list) {
        list.add(new Step("árvore: ← ← recolhe o grupo", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        }, () -> furnaceRows() == 0 && furnaceGroupRow().collapsed(), () -> "linhas " + rows().size()));
        list.add(new Step("árvore: → abre o grupo", STEP_TIMEOUT_MS,
                () -> tabletScreen().keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0),
                () -> furnaceRows() == 8 && !furnaceGroupRow().collapsed(), () -> "linhas " + rows().size()));
        list.add(new Step("árvore: na busca, ← anda no texto e não mexe na árvore", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            click(screen, screen.searchBox().getX() + 4, screen.searchBox().getY() + 4);
            screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        }, () -> stepTicks >= 20 && furnaceRows() == 8 && !furnaceGroupRow().collapsed()
                && tabletScreen().searchBox().isFocused(), () -> "linhas " + rows().size()));
    }

    /** Fechar e reabrir o Tablet com o roteador aberto: a árvore volta aberta na primeira página (só a checagem, sem captura). */
    private static void reopenSteps(List<Step> list) {
        // Esc com texto na busca só o limpa: fecha pela própria tela, com a busca e a árvore como estão.
        list.add(new Step("árvore: fechar o Tablet com o roteador aberto", STEP_TIMEOUT_MS,
                () -> tabletScreen().onClose(), () -> Minecraft.getInstance().screen == null,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(openTablet("árvore: reabrir o Tablet"));
        list.add(new Step("árvore: o roteador continua aberto", STEP_TIMEOUT_MS, () -> {
        }, () -> furnaceRows() == 8 && rows().getFirst() instanceof TabletRows.Attached
                && center(TabletList.TreePart.TOGGLE, ListTree.Kind.NODE, 0) != null,
                () -> "linhas " + rows().size()));
    }

    /** Roda a lista ({@code direction} −1 desce, 1 sobe) até a área aparecer; devolve o centro dela ou {@code null}. */
    private static int @Nullable [] scrollUntil(int direction, Center area) throws Exception {
        TabletScreen screen = tabletScreen();
        int[] at = null;
        for (int m = 0; m < FIRST_FURNACE + FURNACES && at == null; m++) {
            at = center(TabletList.TreePart.LINE, ListTree.Kind.MACHINE, m);
        }
        int[] c = area.get();
        for (int i = 0; i < 20 && c == null && at != null; i++) {
            screen.mouseScrolled(at[0], at[1], 0, direction);
            c = area.get();
        }
        return c;
    }

    private interface Center {
        int @Nullable [] get() throws Exception;
    }

    /**
     * Captura com os baús movendo: a cada tique o de origem ganha pedregulho e o de destino esvazia, até a amostra do
     * Tablet mostrar a origem movendo; o aviso de um passo anterior sai antes, para não cobrir o painel.
     */
    private static Step flowingCapture(String file) {
        return new Step("captura " + file + " com os baús movendo", STEP_TIMEOUT_MS, () -> {
            moveMouse(0, 0);
            PanelNotices.clear();
        }, () -> {
            onServer(server -> {
                Container from = (Container) server.overworld().getBlockEntity(source);
                Container to = (Container) server.overworld().getBlockEntity(sink);
                for (int i = 0; i < 4; i++) {
                    from.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                }
                to.clearContent();
                return null;
            });
            TabletRows.MachineLive live = live(0);
            if (stepTicks < 25 || live == null || live.status() != TabletRows.MachineStatus.MOVING
                    || live.given(ResourceType.ITEM) == 0 || PanelNotices.current() != null) {
                return false;
            }
            capture(Minecraft.getInstance(), file);
            return true;
        }, () -> "origem " + live(0));
    }

    /**
     * O roteador Elite com o upgrade num baú; dois baús vinculados, a origem extraindo e o destino inserindo na rede
     * principal; e as dez fornalhas, a primeira com exceção e a segunda com rótulo.
     */
    private static Step setUp(UUID playerId) {
        return new Step("árvore: preparar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            chest = chestB.offset(3, 0, 0);
            router = chest.above();
            source = chestB.offset(5, 0, 0);
            sink = chestB.offset(7, 0, 0);
            furnaces.clear();
            for (BlockPos pos : List.of(chest, source, sink)) {
                level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
            }
            for (int i = 0; i < FURNACES; i++) {
                BlockPos pos = chestB.offset(i - 2, 0, -3);
                furnaces.add(pos);
                level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState()
                        .setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
            }
            level.setBlockAndUpdate(router, ModBlocks.ROUTER.get().defaultBlockState()
                    .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ELITE));
            RouterBlockEntity r = router(server, router);
            r.setName(NAME);
            r.setRangeUpgrade(new ItemStack(ModItems.RANGE_UPGRADE.get()));
            r.linkMachine(source);
            r.linkMachine(sink);
            for (BlockPos pos : furnaces) {
                r.linkMachine(pos);
            }
            r.configureFace(EditTarget.machine(0), ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0,
                    RedstoneMode.IGNORE, mainNetwork);
            r.configureFace(EditTarget.machine(1), ResourceType.ITEM, Direction.UP, PortMode.INSERT, 0,
                    RedstoneMode.IGNORE, mainNetwork);
            r.renameTarget(EditTarget.machine(FIRST_FURNACE + 1), "Fundição 2");
            r.configureFace(EditTarget.machine(EXCEPTION), ResourceType.ITEM, Direction.UP, PortMode.INSERT, 0,
                    RedstoneMode.IGNORE);
            furnaceGroup = r.remoteMachines().machine(FIRST_FURNACE).group();
            NodeIndex.placedBy(r, playerId);
            return null;
        }), () -> onServer(server -> {
            NodeIndex.Entry entry = NodeIndex.get(server).entry(node());
            return entry != null && entry.remotes().total() == FIRST_FURNACE + FURNACES;
        }), () -> "máquinas no índice");
    }

    /** Acha o roteador pela busca e o abre pelo ▸: a presa, os baús, as fornalhas (8) e o "mais 2". */
    private static void openTreeSteps(List<Step> list, String label) {
        list.add(new Step(label + "achar o roteador pela busca", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            screen.list().clearAll();
            screen.setFocused(screen.searchBox());
            type(NAME);
        }, () -> {
            TabletSnapshot s = tabletScreen().getMenu().snapshot();
            if (tabletScreen().list().suggestionsOpen()) {
                tabletScreen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                return false;
            }
            return s.query().text().equals(NAME) && s.nodes().size() == 1
                    && s.nodes().getFirst().linked() == FIRST_FURNACE + FURNACES
                    && center(TabletList.TreePart.TOGGLE, ListTree.Kind.NODE, 0) != null;
        }, () -> "nós " + tabletScreen().getMenu().snapshot().nodes().size()));
        list.add(new Step(label + "o ▸ abre o roteador", STEP_TIMEOUT_MS, () -> {
            int[] c = center(TabletList.TreePart.TOGGLE, ListTree.Kind.NODE, 0);
            click(tabletScreen(), c[0], c[1]);
        }, () -> furnaceRows() == 8 && rows().stream().anyMatch(r -> r instanceof TabletRows.More m && m.hidden() == 2)
                && rows().getFirst() instanceof TabletRows.Attached
                && center(TabletList.TreePart.LINE, ListTree.Kind.MACHINE, EXCEPTION) != null,
                () -> "linhas " + rows()));
    }

    /** Clicar na máquina escolhe o roteador e põe a máquina como alvo do painel. */
    private static Step chooseMachine(String label, int index) {
        return new Step(label + "escolher a máquina " + index, STEP_TIMEOUT_MS, () -> {
            int[] c = center(TabletList.TreePart.LINE, ListTree.Kind.MACHINE, index);
            click(tabletScreen(), c[0], c[1]);
        }, () -> node().equals(tabletScreen().list().chosen()) && EditTarget.machine(index).equals(panelTarget())
                && stepTicks >= 4, () -> "alvo " + panelTarget());
    }

    /** Em português: fecha o Tablet, troca o idioma, abre de novo com a árvore e volta ao inglês. */
    private static void portugueseSteps(List<Step> list) {
        list.add(clearSearch("árvore: "));
        list.add(close("árvore: fechar o Tablet"));
        list.add(language("pt_br"));
        list.add(openTablet("pt: árvore: abrir o Tablet"));
        openTreeSteps(list, "pt: árvore: ");
        list.add(chooseMachine("pt: árvore: ", EXCEPTION));
        list.add(flowingCapture("7l-tablet-arvore-pt"));
        list.add(clipCheck("pt: Tablet: árvore"));
        list.add(listClipTooltip("pt: Tablet: árvore"));
        list.add(new Step("pt: árvore: fechar o roteador", STEP_TIMEOUT_MS, () -> {
            int[] c = center(TabletList.TreePart.TOGGLE, ListTree.Kind.NODE, 0);
            click(tabletScreen(), c[0], c[1]);
        }, () -> rows().isEmpty(), () -> "linhas " + rows().size()));
        list.add(clearSearch("pt: árvore: "));
        list.add(close("pt: árvore: fechar o Tablet"));
        list.add(language("en_us"));
        list.add(openTablet("árvore: abrir o Tablet de novo"));
    }

    /** Tira a busca (com ela, o Esc limpa em vez de fechar). */
    private static Step clearSearch(String label) {
        return new Step(label + "tirar a busca", STEP_TIMEOUT_MS, () -> tabletScreen().list().clearAll(),
                () -> tabletScreen().getMenu().snapshot().query().text().isEmpty(), () -> "busca ainda no servidor");
    }

    private static Step openTablet(String name) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof TabletScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen));
    }

    /** Tira a busca, apaga as acesas e desmonta o roteador, os baús e as fornalhas. */
    private static Step tearDown() {
        return new Step("árvore: limpar", STEP_TIMEOUT_MS, () -> {
            tabletScreen().list().clearAll();
            HighlightRenderer.clear();
            onServer(server -> {
                ServerLevel level = server.overworld();
                RouterBlockEntity r = router(server, router);
                r.removeRangeUpgrade();
                while (!r.remoteMachines().machines().isEmpty()) {
                    r.unlinkMachine(0);
                }
                ((Container) level.getBlockEntity(source)).clearContent();
                ((Container) level.getBlockEntity(sink)).clearContent();
                level.removeBlock(router, false);
                for (BlockPos pos : List.of(chest, source, sink)) {
                    level.removeBlock(pos, false);
                }
                for (BlockPos pos : furnaces) {
                    level.removeBlock(pos, false);
                }
                return null;
            });
        }, () -> onServer(server -> NodeIndex.get(server).entry(node()) == null)
                && tabletScreen().getMenu().snapshot().query().text().isEmpty(), () -> "roteador ainda no índice");
    }

    private static NodeIndex.NodeKey node() {
        return new NodeIndex.NodeKey(Level.OVERWORLD, router);
    }

    private static int @Nullable [] center(TabletList.TreePart part, ListTree.Kind kind, int id) throws StepFailure {
        return tabletScreen().list().treeCenter(part, node(), kind, id);
    }

    private static List<TabletRows.Row> rows() throws StepFailure {
        return tabletScreen().getMenu().snapshot().rows().rows();
    }

    private static long furnaceRows() throws StepFailure {
        return rows().stream().filter(r -> r instanceof TabletRows.Machine m && m.index() >= FIRST_FURNACE).count();
    }

    private static TabletRows.Group furnaceGroupRow() throws StepFailure {
        for (TabletRows.Row row : rows()) {
            if (row instanceof TabletRows.Group group && group.id() == furnaceGroup) {
                return group;
            }
        }
        throw new StepFailure("sem o grupo das fornalhas na página");
    }

    /** O estado e a vazão da máquina {@code index} na página, ou {@code null} se ela não está lá. */
    private static TabletRows.@Nullable MachineLive live(int index) throws StepFailure {
        TabletRows page = tabletScreen().getMenu().snapshot().rows();
        int machine = 0;
        for (TabletRows.Row row : page.rows()) {
            if (row instanceof TabletRows.Machine m) {
                if (m.index() == index) {
                    return machine < page.lives().size() ? page.lives().get(machine) : null;
                }
                machine++;
            }
        }
        return null;
    }

    private static @Nullable EditTarget panelTarget() throws StepFailure {
        TabletPanelPayload panel = tabletScreen().panel();
        return panel == null ? null : panel.snapshot().map(RouterSnapshot::target).orElse(null);
    }
}
