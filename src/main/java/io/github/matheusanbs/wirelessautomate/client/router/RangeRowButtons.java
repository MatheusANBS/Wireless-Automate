package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineState;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineView;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Os botões pequenos da linha escolhida na lista das máquinas ({@link RangeList}), da direita para a
 * esquerda: ✕ Desvincular (máquina), ↺ Voltar ao perfil (máquina com exceção) e ⟳ Atualizar (máquina
 * trocada, ou grupo com alguma trocada). O ✕ pede dois cliques ({@link ArmedClick}): armado, vira o botão
 * de texto "Desvincular?", que desarma quando o mouse sai dele ou o alvo muda. O clique devolve a ação; quem
 * manda o pedido ao servidor é a coluna da lista. Público só pela {@link Action}, que o e2e usa.
 */
public final class RangeRowButtons {
    public enum Action { UPDATE, REVERT, UNLINK }

    /** Um botão posto na tela. */
    record Button(Action action, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int[] center() {
            return new int[] {x + w / 2, y + h / 2};
        }
    }

    static final int SIZE = 10;
    private static final int GAP = 2;
    private static final int PAD = 3;

    /**
     * O ⟳ em 6 × 6 px, linha a linha ({@code #} aceso); o ↺ é o espelho dele. Desenhados à mão, como o ✕, para
     * ficarem no centro do botão de 10 px (os da fonte do jogo saem pequenos e fora do centro).
     */
    private static final String[] ARROW = {".###.#", "#...##", "#..###", "#.....", "#....#", ".####."};

    private final ArmedClick unlinkArm = new ArmedClick();
    private @Nullable EditTarget armedFor;

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Quantas máquinas do grupo {@code group} estão trocadas. */
    static int replaced(RangeView view, int group) {
        int count = 0;
        for (MachineView machine : view.machines()) {
            if (machine.group() == group && machine.state() == MachineState.REPLACED) {
                count++;
            }
        }
        return count;
    }

    /** O botão de {@code buttons} sob o mouse, ou {@code null}. */
    static @Nullable Button at(List<Button> buttons, double mouseX, double mouseY) {
        for (Button button : buttons) {
            if (button.contains(mouseX, mouseY)) {
                return button;
            }
        }
        return null;
    }

    /** O centro do botão {@code action} em {@code buttons}, ou {@code null} se ele não está lá. */
    static int @Nullable [] center(List<Button> buttons, Action action) {
        for (Button button : buttons) {
            if (button.action() == action) {
                return button.center();
            }
        }
        return null;
    }

    private boolean unlinkArmed(EditTarget target) {
        return target.equals(armedFor) && unlinkArm.armed(System.currentTimeMillis());
    }

    /**
     * Os botões da linha do alvo {@code target}, com o topo em {@code ry} e a borda direita em {@code right};
     * {@code minX} é até onde o "Desvincular?" pode crescer. Vazia se o alvo não tem botões.
     */
    List<Button> place(RangeView view, EditTarget target, int ry, int rowH, int right, int minX) {
        List<Action> actions = new ArrayList<>();
        switch (target.kind()) {
            case ATTACHED -> {
            }
            case GROUP -> {
                if (replaced(view, target.id()) > 0) {
                    actions.add(Action.UPDATE);
                }
            }
            case MACHINE -> {
                MachineView machine = view.machine(target.id());
                if (machine != null) {
                    actions.add(Action.UNLINK);
                    if (machine.exception()) {
                        actions.add(Action.REVERT);
                    }
                    if (machine.state() == MachineState.REPLACED) {
                        actions.add(Action.UPDATE);
                    }
                }
            }
        }
        List<Button> buttons = new ArrayList<>();
        int bx = right - PAD;
        int by = ry + (rowH - SIZE) / 2;
        for (Action action : actions) {
            int w = SIZE;
            if (action == Action.UNLINK && unlinkArmed(target)) {
                w = Math.max(SIZE, Math.min(font().width(tr("range.unlink.armed")) + 6, bx - minX));
            }
            bx -= w;
            buttons.add(new Button(action, bx, by, w, SIZE));
            bx -= GAP;
        }
        return buttons;
    }

    /**
     * Acompanha o mouse e o alvo a cada quadro: o "Desvincular?" desarma quando o mouse sai dele ou o alvo
     * muda. {@code buttons} são os da linha do alvo, postos agora.
     */
    void track(EditTarget target, List<Button> buttons, int mouseX, int mouseY) {
        if (!target.equals(armedFor)) {
            unlinkArm.disarm();
            armedFor = null;
            return;
        }
        for (Button button : buttons) {
            if (button.action() == Action.UNLINK && button.contains(mouseX, mouseY)) {
                return;
            }
        }
        unlinkArm.disarm();
    }

    /** O clique no botão {@code button} do alvo {@code target}: a ação, ou {@code null} se só armou o ✕. */
    @Nullable Action press(Button button, EditTarget target) {
        if (button.action() != Action.UNLINK) {
            return button.action();
        }
        if (!target.equals(armedFor)) {
            unlinkArm.disarm();
            armedFor = target;
        }
        return unlinkArm.click(System.currentTimeMillis()) ? Action.UNLINK : null;
    }

    // ------------------------------------------------------------------ desenho e dicas

    void render(GuiGraphics g, Font font, EditTarget target, List<Button> buttons, int mouseX, int mouseY) {
        for (Button b : buttons) {
            boolean hovered = b.contains(mouseX, mouseY);
            if (b.action() == Action.UNLINK && unlinkArmed(target)) {
                GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), GuiPaint.DANGER, GuiPaint.DANGER);
                GuiText.draw(g, font, tr("range.unlink.armed"), b.x() + 3, b.y() + 1, b.w() - 5, GuiPaint.SELECTED_TEXT);
                continue;
            }
            int color = switch (b.action()) {
                case UPDATE -> GuiPaint.OK;
                case REVERT -> GuiPaint.ACCENT;
                case UNLINK -> GuiPaint.FG;
            };
            int border = hovered ? GuiPaint.BUTTON_HOVER_BORDER
                    : b.action() == Action.UNLINK ? GuiPaint.BUTTON_BORDER : color;
            GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), GuiPaint.BUTTON, border);
            if (b.action() == Action.UNLINK) {
                cross(g, b.x() + 2, b.y() + 2, color);
            } else {
                arrow(g, b.x() + 2, b.y() + 2, b.action() == Action.REVERT, color);
            }
        }
    }

    /** O ✕ em 6 × 6 px, as duas diagonais: com a borda de 1 px, sobra 1 px nos quatro lados do botão. */
    private static void cross(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 6; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            g.fill(x + 5 - i, y + i, x + 6 - i, y + i + 1, color);
        }
    }

    private static void arrow(GuiGraphics g, int x, int y, boolean mirror, int color) {
        for (int row = 0; row < ARROW.length; row++) {
            for (int col = 0; col < ARROW[row].length(); col++) {
                if (ARROW[row].charAt(mirror ? ARROW[row].length() - 1 - col : col) == '#') {
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, color);
                }
            }
        }
    }

    /** A dica do botão {@code action} do alvo {@code target}: o nome e o que ele faz. */
    Component tooltip(RangeView view, EditTarget target, Action action) {
        boolean armed = action == Action.UNLINK && unlinkArmed(target);
        return tooltip(view, target, action, armed);
    }

    /** A dica de uma ação de máquina, a mesma na lista e na linha Editando; {@code armed} é o ✕ armado. */
    static Component tooltip(RangeView view, EditTarget target, Action action, boolean armed) {
        Component head = switch (action) {
            case UPDATE -> tr("range.update");
            case REVERT -> tr("range.revert");
            case UNLINK -> tr(armed ? "range.unlink.armed" : "range.unlink");
        };
        Component detail = switch (action) {
            case UPDATE -> updateDetail(view, target);
            case REVERT -> tr("range.revert.tooltip");
            case UNLINK -> tr(armed ? "range.unlink.armed.tooltip" : "range.unlink.tooltip");
        };
        return head.copy().append("\n").append(detail.copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** O que o Atualizar faz: o bloco que está no lugar da máquina, ou quantas do grupo estão trocadas. */
    static Component updateDetail(RangeView view, EditTarget target) {
        if (target.kind() == EditTarget.Kind.GROUP) {
            return tr("range.update.tooltip.group", replaced(view, target.id()));
        }
        MachineView machine = view.machine(target.id());
        Component block = machine == null || machine.replacedBy() == null ? Component.literal("?")
                : RouterBlock.machineName(BuiltInRegistries.BLOCK.get(machine.replacedBy()));
        return tr("range.update.tooltip.machine", block);
    }
}
