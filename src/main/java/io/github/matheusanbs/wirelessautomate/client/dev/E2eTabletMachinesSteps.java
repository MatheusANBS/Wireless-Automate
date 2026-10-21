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
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
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
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
 * A Lista do Tablet por máquinas no e2e: um roteador Elite com o Upgrade de Alcance num baú, dois baús que movem de
 * verdade e dez fornalhas vinculadas (uma com exceção, outra com rótulo). O seletor passa a Lista a Máquinas, a busca
 * pelo nome do roteador deixa os dois blocos (os baús e as fornalhas), o clique numa fornalha escolhe o roteador e a
 * põe como alvo do painel, e o Mapa mostra as máquinas dele. "Mostrar todas" e "Mostrar menos"; ↓ e Enter escolhem a
 * seguinte; ← ← recolhe o bloco e → o abre; o clique no cabeçalho recolhe os baús; fechar e reabrir o Tablet volta em
 * Máquinas com os baús recolhidos. Capturas {@code 7o-tablet-maquinas} (também no tema escuro e em português, com a
 * aba Redes em português, {@code 7p-tablet-redes-grupos-pt}) e {@code 7o-tablet-maquinas-recolhidas}.
 */
final class E2eTabletMachinesSteps {
    private E2eTabletMachinesSteps() {
    }

    private static final String NAME = "E2E Maquinas";
    private static final int FURNACES = 10;
    static final int FIRST_FURNACE = 2;
    static final int EXCEPTION = FIRST_FURNACE;
    private static final ResourceLocation FURNACE = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);
    private static final ResourceLocation CHEST = BuiltInRegistries.BLOCK.getKey(Blocks.CHEST);
    private static BlockPos chest = BlockPos.ZERO;
    static BlockPos router = BlockPos.ZERO;
    private static BlockPos source = BlockPos.ZERO;
    private static BlockPos sink = BlockPos.ZERO;
    private static final List<BlockPos> furnaces = new ArrayList<>();
    private static GuiTheme themeBefore = GuiTheme.LIGHT;

    static void machineSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(setUp(playerId));
        list.add(new Step("máquinas: a Lista", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewTab(TabletScreen.Tab.LIST),
                () -> tabletScreen().currentTab() == TabletScreen.Tab.LIST, () -> "aba " + tabletScreen().currentTab()));
        list.add(new Step("máquinas: o seletor passa a Máquinas", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().machines().viewCenter(true);
            if (c == null) {
                throw new StepFailure("sem o seletor");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> page().active(), () -> "visão por máquinas não chegou"));
        list.add(search("máquinas: "));
        list.add(chooseMachine("máquinas: ", EXCEPTION));
        list.add(flowingCapture("7o-tablet-maquinas"));
        list.add(clipCheck("Tablet: máquinas"));
        list.add(listClipTooltip("Tablet: máquinas"));
        list.add(new Step("máquinas: tema escuro", STEP_TIMEOUT_MS, () -> {
            themeBefore = GuiThemes.current();
            GuiThemes.preview(GuiTheme.DARK);
        }, () -> GuiThemes.current() == GuiTheme.DARK, () -> "tema " + GuiThemes.current()));
        list.add(flowingCapture("7o-tablet-maquinas-escuro"));
        list.add(new Step("máquinas: tema de volta", STEP_TIMEOUT_MS, () -> GuiThemes.preview(themeBefore),
                () -> GuiThemes.current() == themeBefore, () -> "tema " + GuiThemes.current()));
        list.add(new Step("máquinas: o Mapa mostra as máquinas do roteador", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewTab(TabletScreen.Tab.MAP),
                () -> stepTicks >= 2 && tabletScreen().mapMachinesDrawn() > 0, () -> "o Mapa sem as máquinas"));
        list.add(new Step("máquinas: de volta à Lista", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewTab(TabletScreen.Tab.LIST),
                () -> tabletScreen().currentTab() == TabletScreen.Tab.LIST && !page().rows().isEmpty(),
                () -> "aba " + tabletScreen().currentTab() + ", linhas " + page().rows().size()));
        showAllSteps(list);
        keySteps(list);
        list.add(new Step("máquinas: o cabeçalho recolhe os baús", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().machines().blockCenter(CHEST);
            click(tabletScreen(), c[0], c[1]);
        }, () -> block(CHEST) != null && block(CHEST).collapsed() && furnaceRows() == 8,
                () -> "linhas " + page().rows().size()));
        list.add(capture("7o-tablet-maquinas-recolhidas"));
        list.add(clipCheck("Tablet: máquinas recolhidas"));
        list.add(closeKeeping("máquinas: fechar o Tablet"));
        list.add(openTablet("máquinas: reabrir o Tablet"));
        list.add(new Step("máquinas: volta em Máquinas, com os baús recolhidos", STEP_TIMEOUT_MS, () -> {
        }, () -> page().active() && block(CHEST) != null && block(CHEST).collapsed() && furnaceRows() == 8,
                () -> "linhas " + page().rows().size()));
        E2eTabletMarkSteps.markSteps(list);
        portugueseSteps(list);
        list.add(E2eTabletMarkSteps.forget());
        list.add(tearDown());
    }

    /** "Mostrar todas" no fim das fornalhas e o "Mostrar menos" no cabeçalho delas. */
    private static void showAllSteps(List<Step> list) {
        list.add(new Step("máquinas: Mostrar todas", STEP_TIMEOUT_MS, () -> {
            int[] c = scrollUntil(-1, () -> tabletScreen().list().machines().moreCenter(FURNACE));
            if (c == null) {
                throw new StepFailure("sem o \"mais N\"");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> furnaceRows() == FURNACES, () -> "fornalhas na página " + furnaceRows()));
        list.add(new Step("máquinas: Mostrar menos", STEP_TIMEOUT_MS, () -> {
            int[] c = scrollUntil(1, () -> tabletScreen().list().machines().fewCenter(FURNACE));
            if (c == null) {
                throw new StepFailure("sem o Mostrar menos");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> furnaceRows() == 8, () -> "fornalhas na página " + furnaceRows()));
    }

    /** ↓ e Enter escolhem a fornalha seguinte; ← ← recolhe as fornalhas (da máquina sobe ao cabeçalho), → abre. */
    private static void keySteps(List<Step> list) {
        list.add(chooseMachine("máquinas: de novo ", EXCEPTION));
        list.add(new Step("máquinas: ↓ e Enter escolhem a seguinte", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> EditTarget.machine(EXCEPTION + 1).equals(panelTarget()), () -> "alvo " + panelTarget()));
        list.add(new Step("máquinas: ← ← recolhe as fornalhas", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        }, () -> furnaceRows() == 0 && block(FURNACE) != null && block(FURNACE).collapsed(),
                () -> "linhas " + page().rows().size()));
        list.add(new Step("máquinas: → abre as fornalhas", STEP_TIMEOUT_MS,
                () -> tabletScreen().keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0),
                () -> furnaceRows() == 8 && !block(FURNACE).collapsed(), () -> "linhas " + page().rows().size()));
    }

    /** Em português: fecha, troca o idioma, abre em Máquinas, a captura, a aba Redes e volta ao inglês. */
    private static void portugueseSteps(List<Step> list) {
        list.add(closeKeeping("máquinas: fechar o Tablet para o português"));
        list.add(language("pt_br"));
        list.add(openTablet("pt: máquinas: abrir o Tablet"));
        list.add(new Step("pt: máquinas: abrir os baús", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().machines().blockCenter(CHEST);
            click(tabletScreen(), c[0], c[1]);
        }, () -> page().active() && block(CHEST) != null && !block(CHEST).collapsed(),
                () -> "linhas " + page().rows().size()));
        list.add(chooseMachine("pt: máquinas: ", EXCEPTION));
        list.add(flowingCapture("7o-tablet-maquinas-pt"));
        list.add(clipCheck("pt: Tablet: máquinas"));
        list.add(listClipTooltip("pt: Tablet: máquinas"));
        E2eTabletMarkSteps.portugueseSteps(list);
        list.add(E2eNetworkSectionSteps.chooseNetwork("pt: redes: "));
        list.add(capture("7p-tablet-redes-grupos-pt"));
        list.add(clipCheck("pt: Tablet: Redes com grupos"));
        list.add(new Step("pt: de volta à Lista", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewTab(TabletScreen.Tab.LIST),
                () -> tabletScreen().currentTab() == TabletScreen.Tab.LIST, () -> "aba " + tabletScreen().currentTab()));
        list.add(closeKeeping("pt: máquinas: fechar o Tablet"));
        list.add(language("en_us"));
        list.add(openTablet("máquinas: abrir o Tablet de novo"));
    }

    /** Acha o roteador pela busca: os baús (a presa e os dois) e as fornalhas (8 e o "mais 2"). */
    static Step search(String label) {
        return new Step(label + "achar o roteador pela busca", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            screen.list().clearAll();
            screen.setFocused(screen.searchBox());
            type(NAME);
        }, () -> {
            if (tabletScreen().list().suggestionsOpen()) {
                tabletScreen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                return false;
            }
            TabletMachineRows page = page();
            return tabletScreen().getMenu().snapshot().query().text().equals(NAME) && page.active()
                    && furnaceRows() == 8 && block(CHEST) != null && block(CHEST).machines() == 3
                    && page.rows().stream().anyMatch(r -> r instanceof TabletMachineRows.More m && m.hidden() == 2);
        }, () -> "linhas " + page().rows());
    }

    /** Clicar na máquina escolhe o roteador e põe a máquina como alvo do painel. */
    static Step chooseMachine(String label, int index) {
        return new Step(label + "escolher a máquina " + index, STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().machines().machineCenter(node(), index);
            if (c == null) {
                throw new StepFailure("a máquina " + index + " fora da vista");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> node().equals(tabletScreen().list().chosen()) && EditTarget.machine(index).equals(panelTarget())
                && stepTicks >= 4, () -> "alvo " + panelTarget());
    }

    /** Roda a lista ({@code direction} −1 desce, 1 sobe) até a área aparecer; devolve o centro dela ou {@code null}. */
    private static int @Nullable [] scrollUntil(int direction, Center area) throws Exception {
        TabletScreen screen = tabletScreen();
        // um ponto nas linhas, logo abaixo do seletor
        int[] view = screen.list().machines().viewCenter(true);
        int[] at = view == null ? null : new int[] {view[0], view[1] + 40};
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
     * Captura com os baús movendo: a cada tique o de origem ganha pedregulho e o de destino esvazia, até a amostra
     * mostrar a origem movendo; o aviso de um passo anterior sai antes, para não cobrir o painel.
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
        return new Step("máquinas: preparar", STEP_TIMEOUT_MS, () -> onServer(server -> {
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
            NodeIndex.placedBy(r, playerId);
            return null;
        }), () -> onServer(server -> {
            NodeIndex.Entry entry = NodeIndex.get(server).entry(node());
            return entry != null && entry.remotes().total() == FIRST_FURNACE + FURNACES;
        }), () -> "máquinas no índice");
    }

    /** Fecha pela própria tela, com a busca como está (o Esc a limparia); termina quando o servidor fechou o menu. */
    private static Step closeKeeping(String name) {
        return new Step(name, STEP_TIMEOUT_MS, () -> tabletScreen().onClose(), () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            return Minecraft.getInstance().screen == null && onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                return player.containerMenu == player.inventoryMenu;
            });
        }, () -> "tela " + describe(Minecraft.getInstance().screen));
    }

    private static Step openTablet(String name) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof TabletScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen));
    }

    /** De volta aos roteadores, sem a busca, e desmonta o roteador, os baús e as fornalhas. */
    private static Step tearDown() {
        return new Step("máquinas: limpar", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            int[] c = screen.list().machines().viewCenter(false);
            click(screen, c[0], c[1]);
            screen.list().clearAll();
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
        }, () -> onServer(server -> NodeIndex.get(server).entry(node()) == null) && !page().active()
                && tabletScreen().getMenu().snapshot().query().text().isEmpty(), () -> "ainda em Máquinas ou no índice");
    }

    static NodeIndex.NodeKey node() {
        return new NodeIndex.NodeKey(Level.OVERWORLD, router);
    }

    static TabletMachineRows page() throws StepFailure {
        return tabletScreen().getMenu().snapshot().machineRows();
    }

    static TabletMachineRows.@Nullable Block block(ResourceLocation id) throws StepFailure {
        for (TabletMachineRows.Row row : page().rows()) {
            if (row instanceof TabletMachineRows.Block block && block.block().equals(id)) {
                return block;
            }
        }
        return null;
    }

    /** As fornalhas do roteador na página. */
    private static long furnaceRows() throws StepFailure {
        TabletMachineRows page = page();
        return page.rows().stream().filter(r -> r instanceof TabletMachineRows.Machine m && !m.attached()
                && m.index() >= FIRST_FURNACE && page.routers().get(m.router()).key().equals(node())).count();
    }

    /** O estado e a vazão da máquina {@code index} do roteador na página, ou {@code null} se ela não está lá. */
    private static TabletRows.@Nullable MachineLive live(int index) throws StepFailure {
        TabletMachineRows page = page();
        int machine = 0;
        for (TabletMachineRows.Row row : page.rows()) {
            if (row instanceof TabletMachineRows.Machine m) {
                if (!m.attached() && m.index() == index && page.routers().get(m.router()).key().equals(node())) {
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
