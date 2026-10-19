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
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A Lista do Tablet em árvore no e2e: um roteador Elite com o Upgrade de Alcance e dez fornalhas vinculadas (uma com
 * rótulo, outra com exceção) abre na Lista; escolher uma máquina põe ela como alvo do painel; o Olho a acende no
 * mundo; ↓ e Enter escolhem a seguinte; "Mostrar todas" e "Mostrar menos"; e as capturas em inglês, no tema escuro
 * e em português ({@code 7l-tablet-arvore*}, {@code 7m-tablet-mostrar-todas}).
 */
final class E2eTabletTreeSteps {
    private E2eTabletTreeSteps() {
    }

    private static final String NAME = "E2E Arvore";
    private static final int FURNACES = 10;
    private static BlockPos chest = BlockPos.ZERO;
    private static BlockPos router = BlockPos.ZERO;
    private static final List<BlockPos> furnaces = new ArrayList<>();
    private static GuiTheme themeBefore = GuiTheme.LIGHT;

    static void treeSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(setUp(playerId));
        openTreeSteps(list, "árvore: ");
        list.add(chooseMachine("árvore: ", 2));
        list.add(new Step("árvore: o Olho acende a máquina", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().treeEyeCenter(node(), 2);
            if (c == null) {
                throw new StepFailure("sem o Olho da máquina #3");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> HighlightRenderer.isLit(Level.OVERWORLD, furnaces.get(2)), () -> "acesas " + HighlightRenderer.count()));
        list.add(capture("7l-tablet-arvore"));
        list.add(clipCheck("Tablet: árvore"));
        list.add(listClipTooltip("Tablet: árvore"));
        list.add(new Step("árvore: tema escuro", STEP_TIMEOUT_MS, () -> {
            themeBefore = GuiThemes.current();
            GuiThemes.preview(GuiTheme.DARK);
        }, () -> GuiThemes.current() == GuiTheme.DARK, () -> "tema " + GuiThemes.current()));
        list.add(capture("7l-tablet-arvore-escuro"));
        list.add(new Step("árvore: tema de volta", STEP_TIMEOUT_MS, () -> GuiThemes.preview(themeBefore),
                () -> GuiThemes.current() == themeBefore, () -> "tema " + GuiThemes.current()));
        list.add(new Step("árvore: ↓ e Enter escolhem a máquina seguinte", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> EditTarget.machine(3).equals(panelTarget()), () -> "alvo " + panelTarget()));
        list.add(new Step("árvore: Mostrar todas", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            // o "mais N" fica embaixo da vista: a roda desce a lista até ele
            int[] at = screen.list().treeLineCenter(node(), ListTree.Kind.MACHINE, 3);
            int[] c = screen.list().treeLineCenter(node(), ListTree.Kind.MORE, group());
            for (int i = 0; i < 20 && c == null && at != null; i++) {
                screen.mouseScrolled(at[0], at[1], 0, -1);
                c = screen.list().treeLineCenter(node(), ListTree.Kind.MORE, group());
            }
            if (c == null) {
                throw new StepFailure("sem o \"mais N\"");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> machineRows() == FURNACES, () -> "máquinas na página " + machineRows()));
        list.add(new Step("árvore: rolar até o grupo", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            int[] at = null;
            for (int m = 0; m < FURNACES && at == null; m++) {
                at = screen.list().treeLineCenter(node(), ListTree.Kind.MACHINE, m);
            }
            for (int i = 0; i < 20 && at != null && screen.list().treeFewCenter(node(), group()) == null; i++) {
                screen.mouseScrolled(at[0], at[1], 0, 1);
            }
        }, () -> tabletScreen().list().treeFewCenter(node(), group()) != null, () -> "sem o Mostrar menos na vista"));
        list.add(capture("7m-tablet-mostrar-todas"));
        list.add(clipCheck("Tablet: mostrar todas"));
        list.add(new Step("árvore: Mostrar menos", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().treeFewCenter(node(), group());
            click(tabletScreen(), c[0], c[1]);
        }, () -> machineRows() == 8, () -> "máquinas na página " + machineRows()));
        list.add(new Step("árvore: ← recolhe o grupo", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            // da máquina, ← sobe para o grupo; de novo, recolhe
            screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        }, () -> machineRows() == 0 && rows().stream().anyMatch(r -> r instanceof TabletRows.Group g && g.collapsed()),
                () -> "linhas " + rows().size()));
        list.add(new Step("árvore: o ▸ fecha o roteador", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().treeToggleCenter(node(), ListTree.Kind.NODE, 0);
            click(tabletScreen(), c[0], c[1]);
        }, () -> rows().isEmpty(), () -> "linhas " + rows().size()));
        portugueseSteps(list);
        list.add(tearDown());
    }

    /** O roteador Elite com o upgrade num baú, as dez fornalhas vinculadas, a #1 com rótulo e a #3 com exceção. */
    private static Step setUp(UUID playerId) {
        return new Step("árvore: preparar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            chest = chestB.offset(3, 0, 0);
            router = chest.above();
            furnaces.clear();
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
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
            for (BlockPos pos : furnaces) {
                r.linkMachine(pos);
            }
            r.renameTarget(EditTarget.machine(0), "Fundição 1");
            r.configureFace(EditTarget.machine(2), ResourceType.ITEM, Direction.UP, PortMode.INSERT, 0,
                    RedstoneMode.IGNORE);
            NodeIndex.placedBy(r, playerId);
            return null;
        }), () -> onServer(server -> {
            NodeIndex.Entry entry = NodeIndex.get(server).entry(node());
            return entry != null && entry.remotes().total() == FURNACES;
        }), () -> "máquinas no índice");
    }

    /** Acha o roteador pela busca e o abre pelo ▸: a presa, o grupo das fornalhas, 8 máquinas e o "mais 2". */
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
            return s.query().text().equals(NAME) && s.nodes().size() == 1 && s.nodes().getFirst().linked() == FURNACES
                    && tabletScreen().list().treeToggleCenter(node(), ListTree.Kind.NODE, 0) != null;
        }, () -> "nós " + tabletScreen().getMenu().snapshot().nodes().size()));
        list.add(new Step(label + "o ▸ abre o roteador", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().treeToggleCenter(node(), ListTree.Kind.NODE, 0);
            click(tabletScreen(), c[0], c[1]);
        }, () -> machineRows() == 8 && rows().stream().anyMatch(r -> r instanceof TabletRows.More m && m.hidden() == 2)
                && rows().getFirst() instanceof TabletRows.Attached
                && tabletScreen().list().treeLineCenter(node(), ListTree.Kind.MACHINE, 2) != null,
                () -> "linhas " + rows()));
    }

    /** Clicar na máquina escolhe o roteador e põe a máquina como alvo do painel. */
    private static Step chooseMachine(String label, int index) {
        return new Step(label + "escolher a máquina #" + (index + 1), STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().treeLineCenter(node(), ListTree.Kind.MACHINE, index);
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
        list.add(chooseMachine("pt: árvore: ", 2));
        list.add(capture("7l-tablet-arvore-pt"));
        list.add(clipCheck("pt: Tablet: árvore"));
        list.add(listClipTooltip("pt: Tablet: árvore"));
        list.add(new Step("pt: árvore: fechar o roteador", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().treeToggleCenter(node(), ListTree.Kind.NODE, 0);
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

    /** Tira a busca, apaga as acesas e desmonta o roteador e as fornalhas. */
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
                level.removeBlock(router, false);
                level.removeBlock(chest, false);
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

    private static List<TabletRows.Row> rows() throws StepFailure {
        return tabletScreen().getMenu().snapshot().rows().rows();
    }

    private static long machineRows() throws StepFailure {
        return rows().stream().filter(r -> r instanceof TabletRows.Machine).count();
    }

    /** O id do grupo das fornalhas, na página. */
    private static int group() throws StepFailure {
        return rows().stream().filter(r -> r instanceof TabletRows.Group).map(r -> ((TabletRows.Group) r).id())
                .findFirst().orElseThrow(() -> new StepFailure("sem grupo na página"));
    }

    private static @Nullable EditTarget panelTarget() throws StepFailure {
        TabletPanelPayload panel = tabletScreen().panel();
        return panel == null ? null : panel.snapshot().map(RouterSnapshot::target).orElse(null);
    }
}
