package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletMachinesSteps.EXCEPTION;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletMachinesSteps.FIRST_FURNACE;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletMachinesSteps.node;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletMachinesSteps.page;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.tabletScreen;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * O Selecionar com máquinas no e2e (etapa 3b), sobre o roteador das fornalhas do {@link E2eTabletMachinesSteps}: copia
 * o perfil dele, marca os roteadores A e B na visão por roteadores e três fornalhas na visão por máquinas, cola nos
 * marcados (o aviso conta roteadores e máquinas) e desfaz; depois marca o cabeçalho das fornalhas, cola no grupo
 * inteiro (o aviso conta a exceção mantida) e desfaz. Capturas {@code 7q-tablet-marcar-maquinas} (também no tema
 * escuro e em português), {@code 7r-tablet-colado} e {@code 7r-tablet-colado-grupos}.
 */
final class E2eTabletMarkSteps {
    private E2eTabletMarkSteps() {
    }

    private static final ResourceLocation FURNACE = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);
    /** As fornalhas marcadas uma a uma (nenhuma é a da exceção). */
    private static final List<Integer> MARKED = List.of(FIRST_FURNACE + 3, FIRST_FURNACE + 4, FIRST_FURNACE + 5);
    private static @Nullable RouterPreset aBefore;
    private static @Nullable RouterPreset bBefore;
    private static GuiTheme themeBefore = GuiTheme.LIGHT;
    private static MachineConfig groupBefore = new MachineConfig();

    static void markSteps(List<Step> list) {
        list.add(E2eTabletMachinesSteps.chooseMachine("marcar: ", EXCEPTION));
        list.add(new Step("marcar: copiar o perfil do roteador das fornalhas", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                router(server, E2eTabletMachinesSteps.router).setMode(ResourceType.ITEM, Direction.WEST, PortMode.INSERT);
                return null;
            });
            int[] c = tabletScreen().list().copyCenter();
            if (c == null) {
                throw new StepFailure("sem o Copiar perfil");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().getMenu().snapshot().profile().map(p -> p.origin().equals(node())).orElse(false),
                () -> "perfil " + tabletScreen().getMenu().snapshot().profile()));
        list.add(new Step("marcar: Selecionar na visão por roteadores", STEP_TIMEOUT_MS, () -> {
            tabletScreen().previewMulti(true, 0);
            int[] c = tabletScreen().list().machines().viewCenter(false);
            click(tabletScreen(), c[0], c[1]);
            tabletScreen().list().clearAll();
        }, () -> !page().active() && tabletScreen().getMenu().snapshot().query().text().isEmpty()
                && tabletScreen().getMenu().snapshot().nodes().stream().anyMatch(n -> n.key().equals(nodeA()))
                && tabletScreen().getMenu().snapshot().nodes().stream().anyMatch(n -> n.key().equals(nodeB())),
                () -> "ainda em Máquinas ou sem A e B na lista"));
        list.add(markRouter("marcar: o roteador A", E2eTabletMarkSteps::nodeA));
        list.add(markRouter("marcar: o roteador B", E2eTabletMarkSteps::nodeB));
        list.add(new Step("marcar: de volta a Máquinas", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().machines().viewCenter(true);
            click(tabletScreen(), c[0], c[1]);
        }, () -> page().active(), () -> "visão por máquinas não chegou"));
        list.add(E2eTabletMachinesSteps.search("marcar: "));
        list.add(markFurnaces("marcar: "));
        list.add(captureMarked("7q-tablet-marcar-maquinas"));
        list.add(clipCheck("Tablet: marcar máquinas"));
        list.add(new Step("marcar: tema escuro", STEP_TIMEOUT_MS, () -> {
            themeBefore = GuiThemes.current();
            GuiThemes.preview(GuiTheme.DARK);
        }, () -> GuiThemes.current() == GuiTheme.DARK, () -> "tema " + GuiThemes.current()));
        list.add(captureMarked("7q-tablet-marcar-maquinas-escuro"));
        list.add(new Step("marcar: tema de volta", STEP_TIMEOUT_MS, () -> GuiThemes.preview(themeBefore),
                () -> GuiThemes.current() == themeBefore, () -> "tema " + GuiThemes.current()));
        list.add(new Step("marcar: Colar nos marcados", STEP_TIMEOUT_MS, () -> {
            aBefore = onServer(server -> RouterPreset.copyOf(router(server, routerA)));
            bBefore = onServer(server -> RouterPreset.copyOf(router(server, routerB)));
            clickPaste();
        }, () -> pasted() && onServer(server -> {
            RemoteMachines remotes = router(server, E2eTabletMachinesSteps.router).remoteMachines();
            return MARKED.stream().allMatch(i -> remotes.machine(i).hasException())
                    && RouterPreset.copyOf(router(server, routerA)).face(ResourceType.ITEM, profileSide(server))
                    .mode() == PortMode.INSERT;
        }), () -> "aviso " + PanelNotices.current()));
        list.add(captureNotice("7r-tablet-colado"));
        list.add(undo("marcar: Desfazer devolve A, B e as fornalhas", () -> onServer(server -> {
            RemoteMachines remotes = router(server, E2eTabletMachinesSteps.router).remoteMachines();
            return RouterPreset.copyOf(router(server, routerA)).equals(aBefore)
                    && RouterPreset.copyOf(router(server, routerB)).equals(bBefore)
                    && MARKED.stream().noneMatch(i -> remotes.machine(i).hasException());
        })));
        list.add(new Step("marcar: o cabeçalho das fornalhas", STEP_TIMEOUT_MS, () -> {
            tabletScreen().previewMulti(true, 0);
            int[] c = scrollTo(() -> tabletScreen().list().machines().blockCenter(FURNACE));
            if (c == null) {
                throw new StepFailure("sem o cabeçalho das fornalhas");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().markCounts()[1] == 10, () -> "marcados " + counts()));
        list.add(new Step("marcar: Colar no bloco", STEP_TIMEOUT_MS, () -> {
            groupBefore = onServer(server -> furnaceProfile(server).copy());
            clickPaste();
        }, () -> pasted() && onServer(server -> {
            RemoteMachines remotes = router(server, E2eTabletMachinesSteps.router).remoteMachines();
            return remotes.machine(EXCEPTION).hasException() && !furnaceProfile(server).sameAs(groupBefore);
        }), () -> "aviso " + PanelNotices.current()));
        list.add(captureNotice("7r-tablet-colado-grupos"));
        list.add(clipCheck("Tablet: colado no grupo"));
        list.add(undo("marcar: Desfazer devolve o grupo",
                () -> onServer(server -> furnaceProfile(server).sameAs(groupBefore))));
        list.add(new Step("marcar: sair do Selecionar", STEP_TIMEOUT_MS, () -> tabletScreen().previewMulti(false, 0),
                () -> tabletScreen().markCounts()[0] == 0 && tabletScreen().markCounts()[1] == 0, () -> counts()));
    }

    /** Em português, com o Tablet reaberto: marca as três fornalhas e captura. */
    static void portugueseSteps(List<Step> list) {
        list.add(new Step("pt: marcar: Selecionar", STEP_TIMEOUT_MS, () -> tabletScreen().previewMulti(true, 0),
                () -> tabletScreen().pasteMarkedCenter() != null, () -> "sem o Colar nos marcados"));
        list.add(markFurnaces("pt: marcar: "));
        list.add(captureMarked("7q-tablet-marcar-maquinas-pt"));
        list.add(clipCheck("pt: Tablet: marcar máquinas"));
        list.add(new Step("pt: marcar: sair do Selecionar", STEP_TIMEOUT_MS, () -> tabletScreen().previewMulti(false, 0),
                () -> tabletScreen().markCounts()[1] == 0, () -> counts()));
    }

    /** Esquece o perfil copiado: os passos seguintes contam com o Tablet sem perfil. */
    static Step forget() {
        return new Step("marcar: esquecer o perfil", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().forgetCenter();
            if (c == null) {
                throw new StepFailure("sem o Esquecer");
            }
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletScreen().getMenu().snapshot().profile().isEmpty(),
                () -> "perfil " + tabletScreen().getMenu().snapshot().profile());
    }

    /** Marca o roteador pela linha dele, rolando a lista do topo até ela aparecer. */
    private static Step markRouter(String label, java.util.function.Supplier<NodeKey> node) {
        return new Step(label, STEP_TIMEOUT_MS, () -> {
            NodeKey key = node.get();
            TabletScreen screen = tabletScreen();
            screen.getMenu().snapshot().nodes().stream().filter(n -> n.key().equals(key)).findFirst()
                    .ifPresent(n -> screen.list().previewExpand(n.machine()));
            int[] c = scrollTo(() -> screen.nodeRowCenter(key));
            if (c == null) {
                throw new StepFailure("o roteador " + key.pos().toShortString() + " fora da vista");
            }
            click(screen, c[0], c[1]);
        }, () -> stepTicks >= 1, () -> counts());
    }

    /** Marca as três fornalhas pela linha delas. */
    private static Step markFurnaces(String label) {
        return new Step(label + "três fornalhas", STEP_TIMEOUT_MS, () -> {
            int before = tabletScreen().markCounts()[1];
            for (int index : MARKED) {
                int[] c = scrollTo(() -> tabletScreen().list().machines().machineCenter(node(), index));
                if (c == null) {
                    throw new StepFailure("a fornalha " + index + " fora da vista");
                }
                click(tabletScreen(), c[0], c[1]);
            }
            if (tabletScreen().markCounts()[1] != before + MARKED.size()) {
                throw new StepFailure("marcadas " + counts());
            }
        }, () -> tabletScreen().pasteMarkedCenter() != null, () -> counts());
    }

    private interface Center {
        int @Nullable [] get() throws Exception;
    }

    /** Rola a lista até o topo e depois desce até a área aparecer; devolve o centro dela ou {@code null}. */
    private static int @Nullable [] scrollTo(Center area) throws Exception {
        TabletScreen screen = tabletScreen();
        int[] view = screen.list().machines().viewCenter(false);
        if (view == null) {
            return area.get();
        }
        int[] at = {view[0], view[1] + 40};
        int[] c = area.get();
        for (int i = 0; i < 40 && c == null; i++) {
            screen.mouseScrolled(at[0], at[1], 0, 1);
        }
        for (int i = 0; i < 60 && c == null; i++) {
            c = area.get();
            if (c == null) {
                screen.mouseScrolled(at[0], at[1], 0, -1);
            }
        }
        return c;
    }

    private static void clickPaste() throws StepFailure {
        int[] c = tabletScreen().pasteMarkedCenter();
        if (c == null) {
            throw new StepFailure("sem o Colar nos marcados");
        }
        click(tabletScreen(), c[0], c[1]);
    }

    private static boolean pasted() {
        PanelNoticePayload notice = PanelNotices.current();
        return notice != null && notice.undo() == PanelNoticePayload.Undo.UNDO_PASTE;
    }

    /** O lado relativo do perfil que está em Inserir: o oeste da presa do roteador das fornalhas. */
    private static RelativeSide profileSide(MinecraftServer server) {
        RouterBlockEntity r = router(server, E2eTabletMachinesSteps.router);
        return RelativeSide.fromAbsolute(r.facing(), r.spin(), Direction.WEST);
    }

    /** O perfil do grupo das fornalhas. */
    private static MachineConfig furnaceProfile(MinecraftServer server) {
        RemoteMachines remotes = router(server, E2eTabletMachinesSteps.router).remoteMachines();
        return remotes.groupOf(remotes.machine(EXCEPTION)).profile();
    }

    private static Step captureMarked(String file) {
        return new Step("captura " + file, STEP_TIMEOUT_MS, () -> {
            moveMouse(0, 0);
            PanelNotices.clear();
        }, () -> {
            if (stepTicks < 3) {
                return false;
            }
            capture(net.minecraft.client.Minecraft.getInstance(), file);
            return true;
        }, () -> "captura");
    }

    private static Step captureNotice(String file) {
        return new Step("captura " + file, STEP_TIMEOUT_MS, () -> moveMouse(0, 0), () -> {
            if (stepTicks < 3 || !pasted()) {
                return false;
            }
            capture(net.minecraft.client.Minecraft.getInstance(), file);
            return true;
        }, () -> "aviso " + PanelNotices.current());
    }

    private interface Check {
        boolean ok() throws Exception;
    }

    private static Step undo(String label, Check restored) {
        return new Step(label, STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().list().undoCenter();
            if (c == null) {
                throw new StepFailure("sem o Desfazer");
            }
            click(tabletScreen(), c[0], c[1]);
        }, restored::ok, () -> "não voltou");
    }

    private static String counts() {
        try {
            int[] counts = tabletScreen().markCounts();
            return "roteadores " + counts[0] + ", máquinas " + counts[1];
        } catch (StepFailure e) {
            return e.getMessage();
        }
    }

    private static NodeKey nodeA() {
        return new NodeKey(Level.OVERWORLD, routerA);
    }

    private static NodeKey nodeB() {
        return new NodeKey(Level.OVERWORLD, routerB);
    }
}
