package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRangeSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.router.RangeRowButtons;
import io.github.matheusanbs.wirelessautomate.menu.MachineClipboard;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Copiar e colar o perfil por máquina no e2e do roteador de alcance (o grupo dos baús tem Trás em Extrair no perfil;
 * a fornalha, no perfil vazio do grupo dela). Pela lista: a dica do ⧉ no primeiro baú ({@code range-q1-copiar-dica}),
 * o ⧉ e o rodapé "Cópia: ..." ({@code range-q2-copia-rodape}, também no escuro), a dica do ⎘ na fornalha
 * ({@code range-q3-colar-dica}), o ⎘ que cola como exceção e o aviso com o Desfazer ({@code range-q4-colado-aviso}), e o
 * Desfazer. Pelo teclado: Ctrl+V na fornalha, Shift + Ctrl+V no perfil do grupo dela (em português, Shift + clique no
 * ⎘), o Desfazer e Ctrl+C. No fim, o ✕ do rodapé esquece a cópia. Sem o Upgrade de Alcance ({@link #noUpgradeSteps}):
 * Ctrl+C na presa, a cópia no alto do visor ({@code range-q8-sem-upgrade-copia}), Ctrl+V e o ✕ do visor.
 */
final class E2eRangeCopySteps {
    private static final int CTRL = GLFW.GLFW_MOD_CONTROL;

    private E2eRangeCopySteps() {
    }

    /** Os passos com a tela no mínimo e a lista à vista; no fim, a fornalha volta a ser o alvo, no perfil. */
    static void copySteps(List<Step> list, String label, String suffix, UUID playerId) {
        String l = label + "copiar: ";
        list.add(selectStep(l + "escolher o primeiro baú", playerId, () -> rangeChestA, RangeRowButtons.Action.COPY));
        list.add(new Step(l + "o mouse no ⧉", STEP_TIMEOUT_MS,
                () -> moveTo(routerScreen().rangeButtonCenter(RangeRowButtons.Action.COPY), "o ⧉"),
                () -> stepTicks >= 2, () -> "⧉"));
        list.add(E2eRangeReplaceSteps.hoverCapture(l + "dica do ⧉", "range-q1-copiar-dica" + suffix,
                () -> routerScreen().rangeButtonCenter(RangeRowButtons.Action.COPY) != null));
        list.add(new Step(l + "o ⧉ copia o baú", STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.COPY), "o ⧉"),
                () -> copied(playerId, Blocks.CHEST) && routerScreen().footerCenter(true) != null
                        && routerScreen().rangeButtonCenter(RangeRowButtons.Action.PASTE) != null,
                () -> "cópia " + clipboard()));
        list.add(rangeClipCheck(l + "rodapé"));
        list.add(capture("range-q2-copia-rodape" + suffix));
        if (suffix.isEmpty()) {
            E2eVariants.escuro(list, () -> {
                list.add(E2eDarkSteps.themeClick(l + "tema escuro", GuiTheme.DARK));
                list.add(rangeClipCheck(l + "rodapé no escuro"));
                list.add(capture("range-q2-copia-rodape-escuro"));
                list.add(E2eDarkSteps.themeClick(l + "de volta ao tema claro", GuiTheme.LIGHT));
            });
        }

        list.add(selectStep(l + "escolher a fornalha", playerId, () -> rangeFurnace, RangeRowButtons.Action.PASTE));
        list.add(new Step(l + "o mouse no ⎘", STEP_TIMEOUT_MS,
                () -> moveTo(routerScreen().rangeButtonCenter(RangeRowButtons.Action.PASTE), "o ⎘"),
                () -> stepTicks >= 2, () -> "⎘"));
        list.add(E2eRangeReplaceSteps.hoverCapture(l + "dica do ⎘", "range-q3-colar-dica" + suffix,
                () -> routerScreen().rangeButtonCenter(RangeRowButtons.Action.PASTE) != null));
        list.add(new Step(l + "o ⎘ cola na fornalha, como exceção", STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.PASTE), "o ⎘"),
                () -> furnaceBack() == PortMode.EXTRACT && furnaceException() && pasteUndoShown(),
                () -> "aviso " + PanelNotices.current() + ", fornalha " + furnace()));
        list.add(rangeClipCheck(l + "aviso do colar"));
        list.add(capture("range-q4-colado-aviso" + suffix));
        list.add(new Step(l + "Desfazer volta a fornalha ao perfil", STEP_TIMEOUT_MS,
                () -> clickPanel(panel().undoCenter(), "Desfazer"),
                () -> furnaceBack() == PortMode.NONE && !furnaceException() && !pasteUndoShown(),
                () -> "aviso " + PanelNotices.current() + ", fornalha " + furnace()));

        keySteps(list, l, suffix.isEmpty(), playerId);
    }

    /**
     * Pelo teclado, na fornalha com a cópia do baú: Ctrl+V, Shift + Ctrl+V no perfil do grupo ({@code keys}; senão,
     * Shift + clique no ⎘), o Desfazer e Ctrl+C; no fim, o ✕ do rodapé esquece a cópia.
     */
    private static void keySteps(List<Step> list, String l, boolean keys, UUID playerId) {
        list.add(new Step(l + "Ctrl+V cola na fornalha", STEP_TIMEOUT_MS,
                () -> routerScreen().keyPressed(GLFW.GLFW_KEY_V, 0, CTRL),
                () -> furnaceBack() == PortMode.EXTRACT && furnaceException() && pasteUndoShown(),
                () -> "fornalha " + furnace()));
        // no grupo: a exceção igual ao perfil novo some
        list.add(new Step(l + (keys ? "Shift + Ctrl+V" : "Shift + clique no ⎘") + " cola no grupo da fornalha",
                STEP_TIMEOUT_MS, () -> {
                    PanelNotices.clear();
                    if (keys) {
                        routerScreen().keyPressed(GLFW.GLFW_KEY_V, 0, CTRL | GLFW.GLFW_MOD_SHIFT);
                    } else {
                        RangeRowButtons.e2eShift = true;
                        try {
                            clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.PASTE), "o ⎘");
                        } finally {
                            RangeRowButtons.e2eShift = false;
                        }
                    }
                }, () -> furnaceProfileBack() == PortMode.EXTRACT && !furnaceException() && pasteUndoShown(),
                () -> "fornalha " + furnace()));
        list.add(new Step(l + "Desfazer volta o perfil do grupo", STEP_TIMEOUT_MS,
                () -> clickPanel(panel().undoCenter(), "Desfazer"),
                () -> furnaceProfileBack() == PortMode.NONE && furnaceException() && !pasteUndoShown(),
                () -> "fornalha " + furnace()));
        list.add(new Step(l + "Ctrl+C copia a fornalha", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                var router = router(server, rangeRouter);
                router.revertToProfile(router.remoteMachines().index(rangeFurnace));
                return null;
            });
            routerScreen().keyPressed(GLFW.GLFW_KEY_C, 0, CTRL);
        }, () -> copied(playerId, Blocks.FURNACE) && !furnaceException(), () -> "cópia " + clipboard()));
        list.add(new Step(l + "o ✕ do rodapé esquece a cópia", STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().footerCenter(true), "o ✕ da cópia"),
                () -> clipboard() == null && routerScreen().footerCenter(true) == null
                        && routerScreen().rangeButtonCenter(RangeRowButtons.Action.PASTE) == null,
                () -> "cópia " + clipboard()));
        // os baús fechados de novo, como os passos seguintes os esperam
        list.add(new Step(l + "sem o aviso e com os baús fechados", STEP_TIMEOUT_MS, () -> {
            PanelNotices.clear();
            int[] chests = routerScreen().rangeArrowPoint(rangeGroup(Blocks.CHEST));
            if (chests != null && routerScreen().rangeRowCenter(EditTarget.machine(rangeMachine(rangeChestA))) != null) {
                click(routerScreen(), chests[0], chests[1]);
            }
        }, () -> PanelNotices.current() == null
                && routerScreen().rangeRowCenter(EditTarget.machine(rangeMachine(rangeChestA))) == null,
                () -> "aviso " + PanelNotices.current()));
    }

    /**
     * Sem o Upgrade de Alcance (a tela de sempre, na presa): Ctrl+C copia a presa e a cópia aparece no alto do visor
     * ({@code range-q8-sem-upgrade-copia}); Ctrl+V cola nela e o ✕ do visor esquece a cópia.
     */
    static void noUpgradeSteps(List<Step> list, String label, UUID playerId) {
        String l = label + "sem upgrade: ";
        list.add(new Step(l + "Ctrl+C copia a presa", STEP_TIMEOUT_MS,
                () -> routerScreen().keyPressed(GLFW.GLFW_KEY_C, 0, CTRL),
                () -> copied(playerId, Blocks.CHEST) && routerScreen().footerCenter(true) != null
                        && !routerScreen().rangeListShown() && stepTicks >= 4,
                () -> "cópia " + clipboard()));
        list.add(rangeClipCheck(l + "cópia no visor"));
        list.add(capture("range-q8-sem-upgrade-copia"));
        list.add(new Step(l + "Ctrl+V cola na presa", STEP_TIMEOUT_MS,
                // a presa já está como a cópia: o aviso vem sem o Desfazer
                () -> routerScreen().keyPressed(GLFW.GLFW_KEY_V, 0, CTRL), () -> noticeHas("pasted"),
                () -> "aviso " + PanelNotices.current()));
        list.add(new Step(l + "o ✕ do visor esquece a cópia", STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().footerCenter(true), "o ✕ da cópia no visor"),
                () -> clipboard() == null && routerScreen().footerCenter(true) == null, () -> "cópia " + clipboard()));
        list.add(new Step(l + "sem o aviso", STEP_TIMEOUT_MS, PanelNotices::clear, () -> PanelNotices.current() == null,
                () -> "aviso"));
    }

    /**
     * Escolhe pelo servidor (a lista abre o grupo e rola até a linha) a máquina em {@code pos}, lida na hora do passo
     * (as posições só existem depois do preparo), até {@code button} aparecer.
     */
    private static Step selectStep(String name, UUID playerId, Supplier<BlockPos> pos, RangeRowButtons.Action button) {
        return new Step(name, STEP_TIMEOUT_MS, () -> onServer(server -> {
            BlockPos at = pos.get();
            int index = router(server, rangeRouter).remoteMachines().index(at);
            selectOnServer(server, playerId, id -> SelectTargetPayload.machine(id, index, at));
            return null;
        }), () -> rangeTargetIs(EditTarget.machine(rangeMachine(pos.get())))
                && routerScreen().rangeButtonCenter(button) != null && stepTicks >= 2,
                () -> "alvo " + routerScreen().getMenu().snapshot().target());
    }

    /** O jogador tem, no servidor, uma cópia de uma máquina do bloco {@code block}, e a tela mostra o nome dela. */
    private static boolean copied(UUID playerId, Block block) throws Exception {
        boolean server = onServer(s -> {
            MachineClipboard.Entry entry = MachineClipboard.get(playerId);
            return entry != null && entry.block().equals(BuiltInRegistries.BLOCK.getKey(block));
        });
        return server && clipboard() != null;
    }

    /** O nome da cópia que a tela mostra, ou {@code null} sem cópia. */
    private static @Nullable String clipboard() throws StepFailure {
        Component source = routerScreen().getMenu().snapshot().clipboard();
        return source.getString().isEmpty() ? null : source.getString();
    }

    /** O aviso do colar, com o Desfazer, está na tela. */
    static boolean pasteUndoShown() {
        PanelNoticePayload notice = PanelNotices.current();
        return notice != null && notice.undo() == PanelNoticePayload.Undo.UNDO_MACHINE_PASTE;
    }

    /** O aviso da tela tem o texto {@code key} (o do começo ou uma das partes depois do "·"). */
    static boolean noticeHas(String key) {
        PanelNoticePayload notice = PanelNotices.current();
        return notice != null && has(notice.message(), "gui.wirelessautomate.panel.notice.range." + key);
    }

    private static boolean has(Component message, String key) {
        if (message.getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
            return true;
        }
        for (Component part : message.getSiblings()) {
            if (has(part, key)) {
                return true;
            }
        }
        return false;
    }

    private static RangeView.MachineView furnace() throws StepFailure {
        return rangeView().machine(rangeMachine(rangeFurnace));
    }

    /** No servidor, a fornalha tem exceção. */
    private static boolean furnaceException() throws Exception {
        return onServer(server -> {
            var machines = router(server, rangeRouter).remoteMachines();
            var machine = machines.machine(machines.index(rangeFurnace));
            return machine != null && machine.hasException();
        });
    }

    /** No servidor, o modo de Itens da Trás que vale para a fornalha (a exceção ou o perfil). */
    private static PortMode furnaceBack() throws Exception {
        return onServer(server -> {
            var machines = router(server, rangeRouter).remoteMachines();
            var machine = machines.machine(machines.index(rangeFurnace));
            return machines.effective(machine).face(ResourceType.ITEM, RemoteSide.BACK).mode();
        });
    }

    /** No servidor, o modo de Itens da Trás no perfil do grupo da fornalha. */
    private static PortMode furnaceProfileBack() throws Exception {
        return onServer(server -> profileBack(server));
    }

    private static PortMode profileBack(MinecraftServer server) {
        var machines = router(server, rangeRouter).remoteMachines();
        return machines.groupOf(machines.machine(machines.index(rangeFurnace))).profile()
                .face(ResourceType.ITEM, RemoteSide.BACK).mode();
    }

    /** Põe o mouse num ponto da tela do roteador; {@code null} = o controle não está lá. */
    private static void moveTo(int @Nullable [] point, String what) throws StepFailure {
        if (point == null) {
            throw new StepFailure("não achei " + what + " na tela do roteador");
        }
        moveMouse(point[0], point[1]);
    }
}
