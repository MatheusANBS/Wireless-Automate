package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderer;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Kind;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * A linha Editando do painel do nó, com o Upgrade de Alcance e sem a lista das máquinas à vista: a pílula
 * do alvo de edição (a máquina presa, um grupo ou uma máquina), o ✎ do nome com a caixa dele, o "Tirar
 * redes" ({@link IdleNetworks}) ao lado dela, os botões No mundo (o destaque, {@link HighlightRenderer}),
 * Voltar a igual às outras, Atualizar (máquina trocada) e Desvincular (em dois cliques) de uma máquina, a dica do
 * alvo, as linhas da lista que abre na pílula e o pedido de troca de alvo.
 */
final class TargetLine {
    /** Uma linha da lista da linha Editando. */
    record TargetRow(EditTarget target, Component label, @Nullable Component right, int rightColor,
            boolean indent) {
    }

    private final PanelModel model;
    private final PanelLayout layout;
    private final LabelEditor label;

    TargetLine(PanelModel model, PanelLayout layout, NodePanel.Actions actions) {
        this.model = model;
        this.layout = layout;
        this.label = new LabelEditor((target, pos, name) ->
                actions.send(LabelEditor.payload(actions.containerId(), target, pos, name)));
    }

    /** A caixa do nome do grupo ou da máquina do alvo, que abre pelo ✎. */
    LabelEditor label() {
        return label;
    }

    private static Component tr(String key, Object... args) {
        return PanelModel.tr(key, args);
    }

    private static Component panelTr(String key, Object... args) {
        return PanelModel.panelTr(key, args);
    }

    // ------------------------------------------------------------------ desenho

    /** A pílula do alvo e os botões da máquina; {@code open} é a lista do alvo aberta. */
    void render(GuiGraphics g, Font font, int mx, int my, boolean open) {
        if (!layout.compactTarget) {
            GuiText.draw(g, font, panelTr("target"), layout.bodyX, layout.targetY + 2, layout.bodyW, GuiPaint.MUTED);
        }
        Hot pill = layout.hot(Kind.TARGET);
        if (pill != null) {
            RangeView.MachineView machine = model.targetMachine();
            int border = machine != null && machine.exception() ? GuiPaint.ACCENT : GuiPaint.BUTTON_BORDER;
            PanelPainter.dropButton(g, font, pill, model.targetName(model.snapshot().target()),
                    pill.contains(mx, my) || open, model.readOnly() == null, border);
        }
        Hot rename = layout.hot(Kind.RENAME);
        if (rename != null) {
            GuiPaint.box(g, rename.x(), rename.y(), rename.w(), rename.h(), GuiPaint.BUTTON,
                    rename.contains(mx, my) ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            RangeRowButtons.pencil(g, rename.x() + (rename.w() - 6) / 2, rename.y() + (rename.h() - 6) / 2, GuiPaint.FG);
        }
        Hot idle = layout.hot(Kind.IDLE_NETWORKS);
        if (idle != null && layout.idleIcon) {
            boolean hovered = idle.contains(mx, my);
            GuiPaint.box(g, idle.x(), idle.y(), idle.w(), idle.h(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            IdleNetworks.icon(g, idle.x() + (idle.w() - IdleNetworks.ICON_SIZE) / 2,
                    idle.y() + (idle.h() - IdleNetworks.ICON_SIZE) / 2, GuiPaint.FG);
        } else if (idle != null) {
            clippedButton(g, font, idle, IdleNetworks.label(), idle.contains(mx, my), GuiPaint.BUTTON_BORDER);
        }
        Hot highlight = layout.hot(Kind.HIGHLIGHT);
        if (highlight != null) {
            renderHighlight(g, font, highlight, highlight.contains(mx, my));
        }
        Hot revert = layout.hot(Kind.REVERT);
        if (revert != null && layout.revertIcon) {
            boolean active = model.readOnly() == null;
            GuiPaint.box(g, revert.x(), revert.y(), revert.w(), revert.h(), active ? GuiPaint.BUTTON : GuiPaint.INSET,
                    revert.contains(mx, my) && active ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            RangeRowButtons.arrow(g, revert.x() + (revert.w() - 6) / 2, revert.y() + (revert.h() - 6) / 2, true,
                    active ? GuiPaint.ACCENT : GuiPaint.DISABLED);
        } else if (revert != null) {
            clippedButton(g, font, revert, layout.revertLabel, revert.contains(mx, my), GuiPaint.BUTTON_BORDER);
        }
        Hot update = layout.hot(Kind.UPDATE);
        if (update != null && layout.compactTarget) {
            iconButton(g, update, update.contains(mx, my), GuiPaint.OK);
            RangeRowButtons.arrow(g, update.x() + (update.w() - 6) / 2, update.y() + (update.h() - 6) / 2, false,
                    GuiPaint.OK);
        } else if (update != null) {
            clippedButton(g, font, update, tr("range.update"), update.contains(mx, my), GuiPaint.OK);
        }
        Hot unlink = layout.hot(Kind.UNLINK);
        if (unlink != null && model.unlinkArmed()) {
            GuiPaint.box(g, unlink.x(), unlink.y(), unlink.w(), unlink.h(), GuiPaint.DANGER, GuiPaint.DANGER);
            centered(g, font, unlink, tr("range.unlink.armed"), GuiPaint.SELECTED_TEXT);
        } else if (unlink != null && layout.compactTarget) {
            iconButton(g, unlink, unlink.contains(mx, my), GuiPaint.BUTTON_BORDER);
            RangeRowButtons.cross(g, unlink.x() + (unlink.w() - 6) / 2, unlink.y() + (unlink.h() - 6) / 2, GuiPaint.FG);
        } else if (unlink != null) {
            clippedButton(g, font, unlink, tr("range.unlink"), unlink.contains(mx, my), GuiPaint.BUTTON_BORDER);
        }
    }

    /** O fundo de um botão só com o ícone, na linha Editando apertada. */
    private static void iconButton(GuiGraphics g, Hot b, boolean hovered, int border) {
        GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), GuiPaint.BUTTON, hovered ? GuiPaint.BUTTON_HOVER_BORDER : border);
    }

    /** O "No mundo": o Olho e a borda ciano com a máquina acesa; vale também só para ler (é só do cliente). */
    private void renderHighlight(GuiGraphics g, Font font, Hot b, boolean hovered) {
        boolean lit = highlightLit();
        int border = lit ? GuiPaint.HIGHLIGHT : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), GuiPaint.BUTTON, border);
        int eyeColor = lit ? GuiPaint.HIGHLIGHT : GuiPaint.MUTED;
        if (layout.highlightIcon) {
            GuiPaint.eye(g, b.x() + (b.w() - 5) / 2, b.y() + (b.h() - 5) / 2, eyeColor);
            return;
        }
        GuiPaint.eye(g, b.x() + PanelLayout.TARGET_PAD, b.y() + (b.h() - 5) / 2, eyeColor);
        int textX = b.x() + PanelLayout.TARGET_PAD + PanelLayout.HIGHLIGHT_EYE;
        int room = b.x() + b.w() - PanelLayout.TARGET_PAD - textX;
        GuiText.draw(g, font, LitFooter.SHOW, textX, b.y() + (b.h() - 8) / 2, room, GuiPaint.FG);
    }

    /** A máquina do alvo está acesa no mundo. */
    boolean highlightLit() {
        RangeView.MachineView machine = model.targetMachine();
        return machine != null && HighlightRenderer.isLit(model.snapshot().dimension(), machine.pos());
    }

    /** O "No mundo": acende ou apaga a máquina do alvo, com a linha até o roteador do painel. */
    void toggleHighlight() {
        RangeView.MachineView machine = model.targetMachine();
        if (machine != null) {
            HighlightRenderer.toggle(model.snapshot().dimension(), machine.pos(), model.snapshot().pos());
        }
    }

    /** A caixa do nome aberta, por cima da pílula do alvo; sem a pílula (a linha saiu), salva e fecha. */
    void renderLabel(GuiGraphics g, int mx, int my) {
        Hot pill = layout.hot(Kind.TARGET);
        if (pill == null) {
            label.finish(true);
        } else if (label.editing()) {
            label.place(pill.x(), pill.y() + 1, pill.w());
            label.render(g, mx, my);
        }
    }

    /**
     * Botão de texto que abrevia pelo {@link GuiText} quando o texto não cabe (o inteiro vai na dica);
     * {@code border} é a borda sem o mouse em cima.
     */
    private void clippedButton(GuiGraphics g, Font font, Hot b, Component text, boolean hovered, int border) {
        boolean active = model.readOnly() == null;
        int edge = hovered && active ? GuiPaint.BUTTON_HOVER_BORDER : active ? border : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), active ? GuiPaint.BUTTON : GuiPaint.INSET, edge);
        centered(g, font, b, text, active ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    private static void centered(GuiGraphics g, Font font, Hot b, Component text, int color) {
        int room = b.w() - 2 * PanelLayout.TARGET_PAD;
        int textW = Math.min(font.width(text), room);
        GuiText.draw(g, font, text, b.x() + PanelLayout.TARGET_PAD + (room - textW + 1) / 2, b.y() + (b.h() - 8) / 2,
                room, color);
    }

    /** Dica da pílula: o alvo, quantas máquinas seguem o perfil e o que a lista faz. */
    Component tooltip() {
        RangeView.MachineView machine = model.targetMachine();
        Component scope;
        if (machine != null) {
            Component state = machine.exception() ? tr("range.machine.exception") : tr("range.machine.follows");
            scope = tr("range.scope.machine", MachineTexts.machineName(model.range(), model.snapshot().target().id()),
                    state);
        } else {
            RangeView.GroupView group = model.targetGroup();
            scope = group == null ? panelTr("target.attached")
                    : tr("range.scope.group", MachineTexts.groupName(group), group.count() - group.exceptions(), group.count());
        }
        return scope.copy().append("\n").append(panelTr("target.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    // ------------------------------------------------------------------ a lista do alvo

    List<TargetRow> rows() {
        List<TargetRow> rows = new ArrayList<>();
        rows.add(new TargetRow(EditTarget.ATTACHED_TARGET, model.targetName(EditTarget.ATTACHED_TARGET), null, 0,
                false));
        RangeView view = model.range();
        for (RangeView.GroupView group : view.groups()) {
            rows.add(new TargetRow(EditTarget.group(group.id()), panelTr("target.group", MachineTexts.groupName(group)),
                    Component.literal(Integer.toString(group.count())), GuiPaint.MUTED, false));
            for (int i = 0; i < view.machines().size(); i++) {
                RangeView.MachineView machine = view.machines().get(i);
                if (machine.group() == group.id()) {
                    rows.add(new TargetRow(EditTarget.machine(i), MachineTexts.machineName(view, i),
                            machine.exception() ? panelTr("target.exception") : null, GuiPaint.WARN, true));
                }
            }
        }
        return rows;
    }

    @Nullable TargetRow currentRow() {
        for (TargetRow row : rows()) {
            if (row.target().equals(model.snapshot().target())) {
                return row;
            }
        }
        return null;
    }

    /** Largura pedida pela lista: a da pílula, ou a da linha mais comprida. */
    int listWidth(Font font, int anchorW) {
        int width = anchorW;
        for (TargetRow row : rows()) {
            int right = row.right() == null ? 0 : font.width(row.right()) + 6;
            width = Math.max(width, font.width(row.label()) + right + (row.indent() ? 18 : 10));
        }
        return width;
    }

    void renderRow(GuiGraphics g, Font font, int x, int ry, int w, int index, boolean hovered) {
        TargetRow row = rows().get(index);
        if (hovered) {
            g.fill(x + 1, ry, x + w - 1, ry + DropdownList.ROW, GuiPaint.ROW_SELECTED);
        }
        if (row.target().equals(model.snapshot().target())) {
            g.fill(x + 1, ry, x + 2, ry + DropdownList.ROW, GuiPaint.ACCENT);
        }
        int textX = x + (row.indent() ? 14 : 6);
        int rightW = 0;
        if (row.right() != null) {
            rightW = Math.min(font.width(row.right()), w / 2);
            GuiText.draw(g, font, row.right(), x + w - 5 - rightW, ry + 2, rightW, row.rightColor());
        }
        GuiText.draw(g, font, row.label(), textX, ry + 2, x + w - 5 - rightW - (rightW > 0 ? 4 : 0) - textX, GuiPaint.FG);
    }

    /** Abre a caixa do nome com o nome dado do alvo (grupo ou máquina) e o nome de quando ele está vazio. */
    void startRename() {
        EditTarget target = model.snapshot().target();
        RangeView.MachineView machine = model.targetMachine();
        if (machine != null) {
            label.start(target, machine.pos(), machine.label(), MachineTexts.blockName(model.range(), target.id()));
            return;
        }
        if (target.kind() == EditTarget.Kind.ATTACHED) {
            RouterSnapshot snapshot = model.snapshot();
            label.start(target, null, snapshot.attachedLabel(),
                    RouterBlock.machineName(snapshot.machineState().getBlock()));
            return;
        }
        RangeView.GroupView group = target.kind() == EditTarget.Kind.GROUP ? model.range().group(target.id()) : null;
        if (group != null) {
            label.start(target, null, group.name(), MachineTexts.groupName(group));
        }
    }

    /** O pedido ao servidor para editar {@code target}, ou {@code null} se o grupo ou a máquina sumiu. */
    @Nullable CustomPacketPayload select(int containerId, EditTarget target) {
        return switch (target.kind()) {
            case ATTACHED -> SelectTargetPayload.attached(containerId);
            case GROUP -> {
                RangeView.GroupView group = model.range().group(target.id());
                yield group == null ? null : SelectTargetPayload.group(containerId, group.id(), group.block());
            }
            case MACHINE -> {
                RangeView.MachineView machine = model.range().machine(target.id());
                yield machine == null ? null : SelectTargetPayload.machine(containerId, target.id(), machine.pos());
            }
        };
    }
}
