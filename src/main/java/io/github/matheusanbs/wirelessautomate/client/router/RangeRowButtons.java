package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineState;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineView;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Os botões pequenos da linha escolhida na lista das máquinas ({@link RangeList}), da direita para a
 * esquerda: ✕ Desvincular (a máquina, ou o grupo inteiro na linha do grupo, menos no grupo da presa sem nada a
 * zerar, {@link RangeView.GroupView#blank}, e no grupo só quando o nome cabe ao lado dos outros), ↺ Voltar ao
 * perfil (máquina ou presa com exceção), ⟳ Atualizar (máquina trocada, ou grupo com alguma trocada), o "Tirar redes" ({@link IdleNetworks}, grupo ou máquina (a presa
 * inclusive) com exceção com alguma face sem modo e com rede) e o ✎ Dar nome (máquina e presa; no grupo, depois
 * dos dois abaixo e só quando o nome cabe ao lado). Por último, ⎘ Colar (com uma cópia) e ⧉ Copiar o perfil, só
 * quando o nome da linha continua inteiro ao lado deles (senão ficam o Ctrl+C e o Ctrl+V).
 * O ✕ pede dois cliques ({@link ArmedClick}): armado, vira o botão de texto "Desvincular?" (no grupo, "Desvincular
 * 3?", "Apagar?" no grupo sem máquina ou "Zerar?" no da presa sozinha), que desarma quando o mouse sai dele ou o alvo
 * muda. O clique devolve a ação; quem manda o pedido ao servidor é a coluna da lista. Os botões são postos de novo
 * só quando algo que os muda muda (a visão, o alvo, o lugar da linha, o armado, a cópia). Público só pela
 * {@link Action}, que o e2e usa.
 */
public final class RangeRowButtons {
    public enum Action { UPDATE, REVERT, REVERT_GROUP, UNLINK, CLEAR_NETWORKS, RENAME, COPY, PASTE }

    /** Shift + clique no ⎘ nos passos do e2e, que não seguram a tecla. */
    public static boolean e2eShift;

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
    /** O lápis do ✎ em 6 × 6 px, na diagonal, com a ponta embaixo à esquerda. */
    private static final String[] PENCIL = {"....##", "...#.#", "..#.#.", ".#.#..", "#.#...", "##...."};
    /** O ⧉ em 6 × 6 px: dois quadrados sobrepostos. */
    private static final String[] COPY = {"####..", "#..#..", "#.####", "####.#", "..#..#", "..####"};
    /** O ⎘ em 6 × 6 px: a prancheta com o prendedor em cima. */
    private static final String[] PASTE = {".####.", "#.##.#", "#....#", "#.##.#", "#....#", "######"};

    private final ArmedClick unlinkArm = new ArmedClick();
    private @Nullable EditTarget armedFor;
    private final ArmedClick revertGroupArm = new ArmedClick();
    private @Nullable EditTarget revertGroupFor;
    /** O alvo tem faces sem modo com rede ({@link IdleNetworks#shown}), lido do snapshot a cada quadro. */
    private boolean idleNetworks;
    /** O nome de origem da cópia do jogador (vazio sem cópia), do snapshot. */
    private Component clipboard = Component.empty();
    /** O nome da linha e o "Desvincular 3?" do grupo, medidos só quando a visão, o alvo ou o nome da presa mudam. */
    private @Nullable RangeView textView;
    private @Nullable EditTarget textTarget;
    private @Nullable String textLabel;
    private int nameW;
    private Component unlinkText = Component.empty();
    private int unlinkTextW;
    /** Os botões postos por último e o que os decidiu ({@link #place}). */
    private List<Button> placed = List.of();
    private @Nullable RangeView placedView;
    private @Nullable EditTarget placedTarget;
    private @Nullable Component placedClipboard;
    private @Nullable String placedLabel;
    /** O topo, a altura, a borda direita, o começo do texto e os armados (bits) dos botões postos. */
    private final int[] placedAt = new int[5];

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
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

    /**
     * Mede, quando a visão, o alvo ou o nome da presa mudam, o nome da linha do alvo ("Fornalha ×3", "#2",
     * "presa") e o texto do ✕ armado.
     */
    private void measure(Font font, RangeView view, EditTarget target, String attachedLabel) {
        if (view == textView && target.equals(textTarget) && attachedLabel.equals(textLabel)) {
            return;
        }
        textView = view;
        textTarget = target;
        textLabel = attachedLabel;
        RangeView.GroupView group = target.kind() == EditTarget.Kind.GROUP ? view.group(target.id()) : null;
        Component name = switch (target.kind()) {
            case GROUP -> group == null ? Component.empty()
                    : tr("range.group.count", MachineTexts.groupName(group), group.count());
            case MACHINE -> MachineRow.rowName(view, target.id());
            case ATTACHED -> view.attachedGroup() >= 0 ? MachineRow.attachedRowName(attachedLabel)
                    : attachedLabel.isEmpty() ? tr("range.attached") : Component.literal(attachedLabel);
        };
        nameW = font.width(name);
        unlinkText = RowButtonTips.unlinkArmedText(view, target);
        unlinkTextW = font.width(unlinkText);
    }

    void idleNetworks(boolean shown) {
        idleNetworks = shown;
    }

    /** O nome de origem da cópia do jogador ({@code RouterSnapshot.clipboard}); vazio sem cópia. */
    void clipboard(Component source) {
        clipboard = source;
    }

    /** O ✕ do alvo {@code target} está armado, esperando o segundo clique. */
    boolean unlinkArmed(EditTarget target) {
        return target.equals(armedFor) && unlinkArm.armed(System.currentTimeMillis());
    }

    /** O ↺ do grupo {@code target} está armado, esperando o segundo clique. */
    boolean revertGroupArmed(EditTarget target) {
        return target.equals(revertGroupFor) && revertGroupArm.armed(System.currentTimeMillis());
    }

    /** Quantas máquinas do grupo {@code target} têm exceção. */
    static int exceptions(RangeView view, EditTarget target) {
        RangeView.GroupView group = target.kind() == EditTarget.Kind.GROUP ? view.group(target.id()) : null;
        return group == null ? 0 : group.exceptions();
    }

    private static Component revertGroupArmedText(RangeView view, EditTarget target) {
        return tr("range.revert_group.armed", exceptions(view, target));
    }

    /**
     * Os botões da linha do alvo {@code target}, com o topo em {@code ry} e a borda direita em {@code right};
     * {@code minX} é onde o texto da linha começa: até onde o armado pode crescer e onde o nome dela começa
     * ({@code attachedLabel} é o nome dado à presa). Vazia se o alvo não tem botões. Com o ✕ armado, só ele; com o
     * ↺ do grupo armado, só ele e o ✕: os outros saem para o texto caber. A lista devolvida é reusada enquanto nada
     * que a decide muda.
     */
    List<Button> place(Font font, RangeView view, EditTarget target, String attachedLabel, int ry, int rowH,
            int right, int minX) {
        boolean unlinkArmed = unlinkArmed(target);
        boolean revertArmed = revertGroupArmed(target);
        int flags = (unlinkArmed ? 4 : 0) | (revertArmed ? 2 : 0) | (idleNetworks ? 1 : 0);
        if (view == placedView && target.equals(placedTarget) && clipboard == placedClipboard
                && attachedLabel.equals(placedLabel) && placedAt[0] == ry && placedAt[1] == rowH
                && placedAt[2] == right && placedAt[3] == minX && placedAt[4] == flags) {
            return placed;
        }
        measure(font, view, target, attachedLabel);
        List<Action> actions = actions(view, target, unlinkArmed, revertArmed);
        int nameEnd = minX + nameW;
        // o ✕ do grupo fica à direita, mas só entra se o nome cabe com ele (ou armado, ou sozinho): o ↺ e o ⟳
        // consertam o grupo e vêm antes
        RangeView.GroupView group = target.kind() == EditTarget.Kind.GROUP ? view.group(target.id()) : null;
        if (group != null && !group.blank()
                && (unlinkArmed || actions.isEmpty() || fits(actions.size() + 1, nameEnd, right))) {
            actions.addFirst(Action.UNLINK);
        }
        if (!unlinkArmed && !revertArmed) {
            if (!clipboard.getString().isEmpty() && fits(actions.size() + 1, nameEnd, right)) {
                actions.add(Action.PASTE);
            }
            if (fits(actions.size() + 1, nameEnd, right)) {
                actions.add(Action.COPY);
            }
            // no grupo, o ✎ só quando o nome cabe ao lado (ele também se edita pelos dois cliques)
            if (target.kind() == EditTarget.Kind.GROUP && (actions.isEmpty() || fits(actions.size() + 1, nameEnd, right))) {
                actions.add(Action.RENAME);
            }
        }
        List<Button> buttons = new ArrayList<>(actions.size());
        int bx = right - PAD;
        int by = ry + (rowH - SIZE) / 2;
        for (Action action : actions) {
            int w = SIZE;
            if (action == Action.UNLINK && unlinkArmed) {
                w = Math.max(SIZE, Math.min(unlinkTextW + 6, bx - minX));
            } else if (action == Action.REVERT_GROUP && revertArmed) {
                w = Math.max(SIZE, Math.min(font.width(revertGroupArmedText(view, target)) + 6, bx - minX));
            }
            bx -= w;
            buttons.add(new Button(action, bx, by, w, SIZE));
            bx -= GAP;
        }
        placed = List.copyOf(buttons);
        placedView = view;
        placedTarget = target;
        placedClipboard = clipboard;
        placedLabel = attachedLabel;
        placedAt[0] = ry;
        placedAt[1] = rowH;
        placedAt[2] = right;
        placedAt[3] = minX;
        placedAt[4] = flags;
        return placed;
    }

    /** Cabem {@code count} botões de 10 px à esquerda de {@code right} com o nome inteiro até {@code nameEnd}. */
    private static boolean fits(int count, int nameEnd, int right) {
        return nameEnd <= right - PAD - count * (SIZE + GAP) - 3;
    }

    /** Os botões que a linha sempre tem, da direita para a esquerda (o ✕ do grupo, o ⎘, o ⧉ e o ✎ do grupo vêm depois). */
    private List<Action> actions(RangeView view, EditTarget target, boolean unlinkArmed, boolean revertArmed) {
        List<Action> actions = new ArrayList<>(8);
        switch (target.kind()) {
            case ATTACHED -> {
                // a presa dentro do grupo: o ↺ e o Tirar redes com a exceção, e o ✎; ela não se desvincula
                if (view.attachedGroup() >= 0) {
                    if (view.attachedException()) {
                        actions.add(Action.REVERT);
                    }
                    if (idleNetworks) {
                        actions.add(Action.CLEAR_NETWORKS);
                    }
                    actions.add(Action.RENAME);
                }
            }
            case GROUP -> {
                if (!unlinkArmed && exceptions(view, target) > 0) {
                    actions.add(Action.REVERT_GROUP);
                }
                // armado, só o ↺ fica, para o "Voltar 3?" caber
                if (!unlinkArmed && !revertArmed) {
                    if (replaced(view, target.id()) > 0) {
                        actions.add(Action.UPDATE);
                    }
                    if (idleNetworks) {
                        actions.add(Action.CLEAR_NETWORKS);
                    }
                }
            }
            case MACHINE -> {
                MachineView machine = view.machine(target.id());
                if (machine != null) {
                    actions.add(Action.UNLINK);
                    if (!unlinkArmed && machine.exception()) {
                        actions.add(Action.REVERT);
                    }
                    if (!unlinkArmed && machine.state() == MachineState.REPLACED) {
                        actions.add(Action.UPDATE);
                    }
                    if (!unlinkArmed && idleNetworks) {
                        actions.add(Action.CLEAR_NETWORKS);
                    }
                    if (!unlinkArmed) {
                        actions.add(Action.RENAME);
                    }
                }
            }
        }
        return actions;
    }

    /**
     * Acompanha o mouse e o alvo a cada quadro: o "Desvincular?" desarma quando o mouse sai dele, o alvo
     * muda ou a linha sai da vista. {@code buttons} são os da linha do alvo, postos agora (vazia se a linha
     * não está à vista).
     */
    void track(EditTarget target, List<Button> buttons, int mouseX, int mouseY) {
        if (target.equals(revertGroupFor)) {
            Button revert = null;
            for (Button button : buttons) {
                if (button.action() == Action.REVERT_GROUP) {
                    revert = button;
                }
            }
            if (revert == null || !revert.contains(mouseX, mouseY)) {
                revertGroupArm.disarm();
            }
        } else {
            revertGroupArm.disarm();
            revertGroupFor = null;
        }
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
        if (button.action() == Action.REVERT_GROUP) {
            if (!target.equals(revertGroupFor)) {
                revertGroupArm.disarm();
                revertGroupFor = target;
            }
            return revertGroupArm.click(System.currentTimeMillis()) ? Action.REVERT_GROUP : null;
        }
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

    void render(GuiGraphics g, Font font, RangeView view, EditTarget target, List<Button> buttons, int mouseX,
            int mouseY) {
        for (Button b : buttons) {
            boolean hovered = b.contains(mouseX, mouseY);
            if (b.action() == Action.REVERT_GROUP && revertGroupArmed(target)) {
                GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), GuiPaint.DANGER, GuiPaint.DANGER);
                GuiText.draw(g, font, revertGroupArmedText(view, target), b.x() + 3, b.y() + 1, b.w() - 5,
                        GuiPaint.SELECTED_TEXT);
                continue;
            }
            if (b.action() == Action.UNLINK && unlinkArmed(target)) {
                GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), GuiPaint.DANGER, GuiPaint.DANGER);
                // o texto medido na última {@link #place}, a desta linha
                GuiText.draw(g, font, unlinkText, b.x() + 3, b.y() + 1, b.w() - 5, GuiPaint.SELECTED_TEXT);
                continue;
            }
            int color = switch (b.action()) {
                case UPDATE -> GuiPaint.OK;
                case REVERT, REVERT_GROUP -> GuiPaint.ACCENT;
                case UNLINK, CLEAR_NETWORKS, RENAME, COPY, PASTE -> GuiPaint.FG;
            };
            boolean revert = b.action() == Action.REVERT || b.action() == Action.REVERT_GROUP;
            // o ↺ já tem a borda coral: com o mouse em cima, ela escurece
            int hover = revert ? GuiPaint.FG : GuiPaint.BUTTON_HOVER_BORDER;
            boolean plain = b.action() != Action.UPDATE && !revert;
            int border = hovered ? hover : plain ? GuiPaint.BUTTON_BORDER : color;
            GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), GuiPaint.BUTTON, border);
            if (b.action() == Action.UNLINK) {
                cross(g, b.x() + 2, b.y() + 2, color);
            } else if (b.action() == Action.CLEAR_NETWORKS) {
                IdleNetworks.icon(g, b.x() + 2, b.y() + 2, color);
            } else if (b.action() == Action.RENAME) {
                pencil(g, b.x() + 2, b.y() + 2, color);
            } else if (b.action() == Action.COPY || b.action() == Action.PASTE) {
                pixels(g, b.action() == Action.COPY ? COPY : PASTE, b.x() + 2, b.y() + 2, false, color);
            } else {
                arrow(g, b.x() + 2, b.y() + 2, revert, color);
            }
        }
    }

    /** O ✕ em 6 × 6 px, as duas diagonais: com a borda de 1 px, sobra 1 px nos quatro lados do botão. */
    static void cross(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 6; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            g.fill(x + 5 - i, y + i, x + 6 - i, y + i + 1, color);
        }
    }

    /** O ⟳, ou o ↺ ({@code mirror}), em 6 × 6 px (também o Voltar só com o ícone da linha da máquina). */
    static void arrow(GuiGraphics g, int x, int y, boolean mirror, int color) {
        pixels(g, ARROW, x, y, mirror, color);
    }

    /** O lápis do ✎, em 6 × 6 px (também o da linha Editando). */
    public static void pencil(GuiGraphics g, int x, int y, int color) {
        pixels(g, PENCIL, x, y, false, color);
    }

    private static void pixels(GuiGraphics g, String[] art, int x, int y, boolean mirror, int color) {
        for (int row = 0; row < art.length; row++) {
            for (int col = 0; col < art[row].length(); col++) {
                if (art[row].charAt(mirror ? art[row].length() - 1 - col : col) == '#') {
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, color);
                }
            }
        }
    }

    /** A dica do botão {@code action} do alvo {@code target}: o nome e o que ele faz. */
    Component tooltip(RangeView view, EditTarget target, Action action) {
        boolean armed = action == Action.UNLINK && unlinkArmed(target)
                || action == Action.REVERT_GROUP && revertGroupArmed(target);
        return action == Action.COPY || action == Action.PASTE ? RowButtonTips.copy(target, action, clipboard)
                : RowButtonTips.tooltip(view, target, action, armed);
    }

}
