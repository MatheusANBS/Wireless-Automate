package io.github.matheusanbs.wirelessautomate.client.router;

import static io.github.matheusanbs.wirelessautomate.client.router.MachineTexts.exceptions;
import static io.github.matheusanbs.wirelessautomate.client.router.MachineTexts.groupName;
import static io.github.matheusanbs.wirelessautomate.client.router.MachineTexts.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineView;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Lista das máquinas da tela do roteador com o Upgrade de Alcance, no lugar do visor 3D: a linha da
 * máquina presa, os grupos (ícone, nome, {@code ×N} e as exceções), que abrem e fecham, e as máquinas
 * de cada grupo aberto ({@link MachineRow}: o nome, as coordenadas e a distância, a bolinha do estado e a
 * marca de exceção). Clicar numa linha escolhe o alvo de edição; a seta de um grupo (ou um clique no grupo
 * já escolhido) abre e fecha; dois cliques no nome abrem a edição dele. A linha escolhida ganha os botões do
 * {@link RangeRowButtons}. Rola com a roda. Só cliente; todo texto passa por {@link GuiText}.
 */
final class RangeList {
    static final int ATTACHED_H = 14;
    static final int GROUP_H = 20;
    static final int MACHINE_H = GROUP_H;
    /** Rolagem de um passo da roda, em px. */
    private static final int SCROLL_STEP = 12;
    /** Largura da área da seta à esquerda de um grupo (clicar ali só abre e fecha). */
    private static final int ARROW_ZONE = 9;
    /** Onde o nome do grupo começa, depois da seta e do ícone. */
    static final int GROUP_TEXT_X = ARROW_ZONE + 20;
    /** Onde o nome da presa começa, a partir da borda da lista (depois do ícone). */
    static final int ATTACHED_TEXT_X = 19;

    enum Kind { ATTACHED, GROUP, MACHINE }

    /**
     * Uma linha: {@code group} é o id do grupo (grupo e máquina), {@code machine} o índice da máquina;
     * {@code top} é relativo ao topo do conteúdo (antes da rolagem).
     */
    record Row(Kind kind, int group, int machine, int top, int height) {
        EditTarget target() {
            return switch (kind) {
                case ATTACHED -> EditTarget.ATTACHED_TARGET;
                case GROUP -> EditTarget.group(group);
                case MACHINE -> EditTarget.machine(machine);
            };
        }
    }

    private final Set<Integer> open = new HashSet<>();
    private final RangeRowButtons buttons = new RangeRowButtons();
    private final MachineRow machineRow;
    private final RowRename rename;
    private int scroll;
    private int x;
    private int y;
    private int width;
    private int height;
    private @Nullable EditTarget shownTarget;
    private @Nullable RangeView cachedView;
    private @Nullable List<Row> cachedRows;

    /**
     * @param rename     a edição do nome das linhas, da coluna
     * @param machineRow o desenho da linha da máquina, com o Olho
     */
    RangeList(RowRename rename, MachineRow machineRow) {
        this.rename = rename;
        this.machineRow = machineRow;
    }

    void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** As linhas na ordem da tela: a presa, e cada grupo seguido das máquinas dele se estiver aberto. */
    List<Row> rows(RangeView view) {
        // as linhas só mudam com a visão (um snapshot novo é outro objeto) ou com os grupos abertos;
        // o alvo não muda a lista. Montadas uma vez e reusadas nos vários usos de cada quadro.
        if (view == cachedView && cachedRows != null) {
            return cachedRows;
        }
        List<Row> rows = new ArrayList<>();
        int top = 0;
        rows.add(new Row(Kind.ATTACHED, -1, -1, top, ATTACHED_H));
        top += ATTACHED_H;
        for (GroupView group : view.groups()) {
            rows.add(new Row(Kind.GROUP, group.id(), -1, top, GROUP_H));
            top += GROUP_H;
            if (!open.contains(group.id())) {
                continue;
            }
            for (int i = 0; i < view.machines().size(); i++) {
                if (view.machines().get(i).group() == group.id()) {
                    rows.add(new Row(Kind.MACHINE, group.id(), i, top, MACHINE_H));
                    top += MACHINE_H;
                }
            }
        }
        cachedView = view;
        cachedRows = List.copyOf(rows);
        return cachedRows;
    }

    /** Os grupos abertos mudaram: as linhas são montadas de novo. */
    private void invalidate() {
        cachedRows = null;
    }

    private static int contentHeight(List<Row> rows) {
        Row last = rows.getLast();
        return last.top() + last.height();
    }

    /** O topo da linha {@code row} na tela, com a rolagem. */
    int rowTop(Row row) {
        return y + 1 + row.top() - scroll;
    }

    /** Área útil das linhas (dentro da moldura). */
    private int innerHeight() {
        return height - 2;
    }

    private int maxScroll(List<Row> rows) {
        return Math.max(0, contentHeight(rows) - innerHeight());
    }

    /** Borda direita do texto das linhas da visão {@code view} (o lugar do Olho termina nela). */
    int textRight(RangeView view) {
        return rowRight(rows(view));
    }

    /** Borda direita do texto das linhas: com rolagem, deixa o espaço da barra. */
    private int rowRight(List<Row> rows) {
        return x + width - 1 - (maxScroll(rows) > 0 ? 3 : 0);
    }

    /**
     * Os botões da linha do alvo, onde ela está agora (o armado vai até o texto); nenhum sem a linha ou com o
     * nome dela em edição. No grupo e na máquina, na primeira linha (na máquina, antes do lugar do Olho, que
     * o "Desvincular?" armado ocupa).
     */
    private List<RangeRowButtons.Button> targetButtons(Font font, RangeView view, EditTarget target) {
        if (rename.editing(target)) {
            return List.of();
        }
        for (Row row : rows(view)) {
            if (row.target().equals(target)) {
                int ry = y + 1 + row.top() - scroll;
                // no grupo e na máquina, na primeira linha: a segunda fica com a largura toda
                boolean eye = row.kind() == Kind.MACHINE && !buttons.unlinkArmed(target);
                int right = rowRight(rows(view)) - (eye ? MachineRow.EYE_W : 0);
                int rowH = row.kind() == Kind.ATTACHED ? row.height() : RangeRowButtons.SIZE + 2;
                return buttons.place(font, view, target, ry, rowH, right, x + RowRename.nameX(row));
            }
        }
        return List.of();
    }

    /**
     * Chamado a cada quadro: quando o alvo muda (pela lista ou pelo servidor), o grupo dele abre e a
     * linha dele entra na área visível. Os outros grupos ficam como o jogador deixou. {@code idleNetworks}
     * diz se a linha do alvo tem o "Tirar redes".
     */
    void sync(RangeView view, EditTarget target, boolean idleNetworks) {
        buttons.idleNetworks(idleNetworks);
        if (target.equals(shownTarget)) {
            scroll = Math.max(0, Math.min(scroll, maxScroll(rows(view))));
            return;
        }
        shownTarget = target;
        int group = groupOf(view, target);
        if (group >= 0 && open.add(group)) {
            invalidate();
        }
        List<Row> rows = rows(view);
        for (Row row : rows) {
            if (row.target().equals(target)) {
                if (row.top() < scroll) {
                    scroll = row.top();
                } else if (row.top() + row.height() > scroll + innerHeight()) {
                    scroll = row.top() + row.height() - innerHeight();
                }
            }
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll(rows)));
    }

    private static int groupOf(RangeView view, EditTarget target) {
        return switch (target.kind()) {
            case ATTACHED -> -1;
            case GROUP -> target.id();
            case MACHINE -> {
                MachineView machine = view.machine(target.id());
                yield machine == null ? -1 : machine.group();
            }
        };
    }

    // ------------------------------------------------------------------ entrada

    @Nullable Row rowAt(RangeView view, double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY) || mouseY < y + 1 || mouseY >= y + 1 + innerHeight()) {
            return null;
        }
        double content = mouseY - (y + 1) + scroll;
        for (Row row : rows(view)) {
            if (content >= row.top() && content < row.top() + row.height()) {
                return row;
            }
        }
        return null;
    }

    /**
     * Clique na lista: um botão da linha escolhida devolve a ação ({@code act}) sem escolher nem abrir; a
     * seta de um grupo, ou o grupo já escolhido, abre e fecha; qualquer outra linha escolhe o alvo
     * ({@code select}). Devolve se o clique foi na lista.
     */
    boolean click(Font font, RangeView view, EditTarget target, double mouseX, double mouseY, Consumer<Row> select,
            BiConsumer<Row, RangeRowButtons.Action> act) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        Row row = rowAt(view, mouseX, mouseY);
        if (row == null) {
            return true;
        }
        RangeRowButtons.Button button = RangeRowButtons.at(targetButtons(font, view, target), mouseX, mouseY);
        if (button != null) {
            RangeRowButtons.Action action = buttons.press(button, target);
            if (action != null) {
                act.accept(row, action);
            }
            return true;
        }
        if (rename.doubleClick(row, mouseX >= x + RowRename.nameX(row))) {
            act.accept(row, RangeRowButtons.Action.RENAME);
            return true;
        }
        if (row.kind() == Kind.GROUP && (mouseX < x + 1 + ARROW_ZONE || row.target().equals(target))) {
            if (!open.remove(row.group())) {
                open.add(row.group());
            }
            invalidate();
            scroll = Math.max(0, Math.min(scroll, maxScroll(rows(view))));
            return true;
        }
        if (!row.target().equals(target)) {
            select.accept(row);
        }
        return true;
    }

    boolean scroll(RangeView view, double mouseX, double mouseY, double scrollY) {
        if (!contains(mouseX, mouseY) || scrollY == 0) {
            return false;
        }
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(scrollY) * SCROLL_STEP, maxScroll(rows(view))));
        return true;
    }

    Component tooltip(RangeView view, Row row, Component attachedName) {
        return switch (row.kind()) {
            case ATTACHED -> tr("range.attached").copy()
                    .append("\n").append(attachedName.copy().withColor(GuiPaint.TOOLTIP_MUTED))
                    .append("\n").append(tr("range.attached.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
            case GROUP -> {
                GroupView group = view.group(row.group());
                if (group == null) {
                    yield Component.empty();
                }
                Component text = tr("range.group.count", groupName(group), group.count());
                if (group.exceptions() > 0) {
                    text = text.copy().append("\n").append(exceptions(group).copy().withColor(GuiPaint.ACCENT));
                }
                yield text.copy().append("\n").append(tr("range.group.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
            }
            case MACHINE -> MachineRow.tooltip(view, row.machine());
        };
    }

    /** A dica do botão da linha escolhida sob o mouse, ou {@code null} se o mouse não está num botão. */
    @Nullable Component buttonTooltip(Font font, RangeView view, EditTarget target, double mouseX, double mouseY) {
        Row row = rowAt(view, mouseX, mouseY);
        RangeRowButtons.Button button = row == null ? null
                : RangeRowButtons.at(targetButtons(font, view, target), mouseX, mouseY);
        return button == null ? null : buttons.tooltip(view, target, button.action());
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font, RangeView view, EditTarget target, ItemStack attachedIcon,
            String attachedLabel, int mouseX, int mouseY) {
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, GuiPaint.PANEL);
        GuiPaint.outline(g, x, y, width, height, GuiPaint.LINE);
        List<Row> rows = rows(view);
        int max = maxScroll(rows);
        scroll = Math.max(0, Math.min(scroll, max));
        // com rolagem, uma barra fina à direita (o texto deixa o espaço dela)
        int right = rowRight(rows);
        Row hovered = rowAt(view, mouseX, mouseY);
        int top = y + 1;
        int bottom = y + 1 + innerHeight();
        g.enableScissor(x + 1, top, x + width - 1, bottom);
        boolean targetShown = false;
        rename.beginFrame();
        for (Row row : rows) {
            int ry = top + row.top() - scroll;
            if (ry + row.height() <= top || ry >= bottom) {
                continue;
            }
            boolean selected = row.target().equals(target);
            if (selected) {
                g.fill(x + 1, ry, right, ry + row.height(), GuiPaint.ROW_SELECTED);
                g.fill(x + 1, ry, x + 2, ry + row.height(), GuiPaint.ACCENT);
            } else if (row.equals(hovered)) {
                g.fill(x + 1, ry, right, ry + row.height(), GuiPaint.ROW_HOVER);
            }
            List<RangeRowButtons.Button> placed = selected ? targetButtons(font, view, target) : List.of();
            int textRight = placed.isEmpty() ? right : placed.getLast().x() - 1;
            switch (row.kind()) {
                case ATTACHED -> renderAttached(g, font, attachedIcon, attachedLabel, ry, right);
                case GROUP -> renderGroup(g, font, view, row, ry, textRight, right);
                case MACHINE -> machineRow.render(g, font, view, row.machine(), x, ry,
                        placed.isEmpty() ? right - MachineRow.EYE_W : textRight, right, placed.isEmpty(),
                        !selected || !buttons.unlinkArmed(target));
            }
            if (ry >= top && ry + row.height() <= bottom) {
                rename.place(row, x, ry, right);
            }
            if (selected) {
                targetShown = true;
                buttons.track(target, placed, mouseX, mouseY);
                buttons.render(g, font, target, placed, mouseX, mouseY);
            }
        }
        if (!targetShown) { // a linha do alvo saiu da vista pela rolagem: o "Desvincular?" desarma
            buttons.track(target, List.of(), mouseX, mouseY);
        }
        if (view.machines().isEmpty()) {
            int hy = top + ATTACHED_H + 3 - scroll;
            GuiText.wrap(g, font, tr("range.list.empty"), x + 5, hy, right - x - 8, 4, GuiPaint.MUTED);
        }
        g.disableScissor();
        if (max > 0) {
            int track = innerHeight();
            int thumb = Math.max(8, track * track / (track + max));
            int thumbY = top + (track - thumb) * scroll / max;
            g.fill(x + width - 3, top, x + width - 2, bottom, GuiPaint.INSET);
            g.fill(x + width - 3, thumbY, x + width - 2, thumbY + thumb, GuiPaint.LINE);
        }
        rename.endFrame(view, g, mouseX, mouseY);
    }

    private void renderAttached(GuiGraphics g, Font font, ItemStack icon, String label, int ry, int right) {
        if (!icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + 4, ry + 1, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        }
        int textX = x + 19;
        if (!rename.editing(EditTarget.ATTACHED_TARGET)) {
            GuiText.draw(g, font, label.isEmpty() ? tr("range.attached") : Component.literal(label), textX, ry + 3,
                    right - textX - 2, GuiPaint.FG);
        }
    }

    /** Um grupo; {@code nameRight} é até onde vai a primeira linha (antes dos botões) e {@code right} a segunda. */
    private void renderGroup(GuiGraphics g, Font font, RangeView view, Row row, int ry, int nameRight, int right) {
        GroupView group = view.group(row.group());
        if (group == null) {
            return;
        }
        if (open.contains(group.id())) {
            GuiPaint.arrowDown(g, x + 3, ry + 9, GuiPaint.MUTED);
        } else {
            GuiPaint.arrowRight(g, x + 4, ry + 8, GuiPaint.MUTED);
        }
        ItemStack icon = group.icon();
        if (!icon.isEmpty()) {
            g.renderItem(icon, x + ARROW_ZONE + 1, ry + 2);
        }
        int textX = x + GROUP_TEXT_X;
        GuiText.draw(g, font, tr("range.group.count", groupName(group), group.count()), textX, ry + 2,
                nameRight - textX - 2,
                GuiPaint.FG);
        int replaced = RangeRowButtons.replaced(view, group.id());
        Component detail;
        int color;
        if (replaced > 0) {
            detail = replaced == 1 ? tr("range.group.replaced.one") : tr("range.group.replaced", replaced);
            color = GuiPaint.BRASS;
        } else {
            detail = group.exceptions() > 0 ? exceptions(group) : tr("range.group.profile");
            color = group.exceptions() > 0 ? GuiPaint.ACCENT : GuiPaint.MUTED;
        }
        GuiText.draw(g, font, detail, textX, ry + 11, right - textX - 2, color);
    }

    // ------------------------------------------------------------------ captura de desenvolvimento

    /** Centro da linha do alvo {@code target} em {@link #rows}, na tela (gancho do e2e); {@code null} se fora da vista. */
    int @Nullable [] rowCenter(RangeView view, EditTarget target) {
        for (Row row : rows(view)) {
            if (row.target().equals(target)) {
                int ry = y + 1 + row.top() - scroll;
                if (ry < y + 1 || ry + row.height() > y + 1 + innerHeight()) {
                    return null;
                }
                return new int[] {x + width / 2, ry + row.height() / 2};
            }
        }
        return null;
    }

    /** Centro do botão {@code act} da linha do alvo (gancho do e2e); {@code null} se ele não está lá. */
    int @Nullable [] buttonCenter(Font font, RangeView view, EditTarget target, RangeRowButtons.Action act) {
        return rowCenter(view, target) == null ? null : RangeRowButtons.center(targetButtons(font, view, target), act);
    }

    /** O ✕ do alvo está armado (gancho do e2e). */
    boolean unlinkArmed(EditTarget target) {
        return buttons.unlinkArmed(target);
    }

    /** Ponto da seta de um grupo (abre e fecha sem escolher; gancho do e2e). */
    int @Nullable [] arrowPoint(RangeView view, int group) {
        int[] center = rowCenter(view, EditTarget.group(group));
        return center == null ? null : new int[] {x + 4, center[1]};
    }
}
