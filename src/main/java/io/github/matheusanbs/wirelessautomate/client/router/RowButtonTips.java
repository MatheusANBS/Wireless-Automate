package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.router.RangeRowButtons.Action;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineView;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/**
 * As dicas e os textos dos botões da linha escolhida da lista das máquinas ({@link RangeRowButtons}), também os da
 * linha Editando: o nome do botão e, embaixo, o que ele faz no alvo. Montadas só com o mouse em cima.
 */
final class RowButtonTips {
    private RowButtonTips() {
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    /** O texto do ✕ armado: "Desvincular?" na máquina; no grupo, pelo que o clique vai fazer. */
    static Component unlinkArmedText(RangeView view, EditTarget target) {
        RangeView.GroupView group = target.kind() == EditTarget.Kind.GROUP ? view.group(target.id()) : null;
        if (group == null) {
            return tr("range.unlink.armed");
        }
        if (group.remotes() > 0) {
            return tr("range.unlink_group.armed", group.remotes());
        }
        return tr(group.attached() ? "range.unlink_group.armed.clear" : "range.unlink_group.armed.delete");
    }

    /**
     * A dica do ⧉ e do ⎘ no alvo: de onde é a configuração copiada, ou de onde vem ({@code clipboard}) e onde entra a
     * colada.
     */
    static Component copy(EditTarget target, Action action, Component clipboard) {
        String kind = switch (target.kind()) {
            case GROUP -> "group";
            case MACHINE -> "machine";
            case ATTACHED -> "attached";
        };
        if (action == Action.COPY) {
            return withDetail(tr("range.copy"), tr("range.copy.tooltip." + kind));
        }
        return withDetail(tr("range.paste"), tr(target.kind() == EditTarget.Kind.GROUP ? "range.paste.tooltip.group"
                : "range.paste.tooltip", clipboard));
    }

    private static Component withDetail(Component head, Component detail) {
        return head.copy().append("\n").append(detail.copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /**
     * A dica de uma ação da linha, a mesma na lista e na linha Editando; {@code armed} é o botão armado. O ⧉ e o ⎘
     * dependem da cópia e ficam com o {@link RangeRowButtons#tooltip}.
     */
    static Component tooltip(RangeView view, EditTarget target, Action action, boolean armed) {
        RangeView.GroupView group = target.kind() == EditTarget.Kind.GROUP ? view.group(target.id()) : null;
        if (action == Action.UNLINK && group != null) {
            return withDetail(armed ? unlinkGroupArmedHead(group) : tr("range.unlink_group"),
                    armed ? tr("range.unlink_group.armed.tooltip") : unlinkGroupDetail(group));
        }
        Component head = switch (action) {
            case UPDATE -> tr("range.update");
            case REVERT -> tr("range.revert");
            case REVERT_GROUP -> tr(armed ? "range.revert_group.armed" : "range.revert_group",
                    RangeRowButtons.exceptions(view, target));
            case UNLINK -> tr(armed ? "range.unlink.armed" : "range.unlink");
            case CLEAR_NETWORKS -> PanelModel.panelTr("idle.clear");
            case RENAME -> tr("range.rename");
            case COPY -> tr("range.copy");
            case PASTE -> tr("range.paste");
        };
        Component detail = switch (action) {
            case UPDATE -> updateDetail(view, target);
            case REVERT -> tr("range.revert.tooltip");
            case REVERT_GROUP -> tr(armed ? "range.revert_group.armed.tooltip" : "range.revert_group.tooltip",
                    RangeRowButtons.exceptions(view, target));
            case UNLINK -> tr(armed ? "range.unlink.armed.tooltip" : "range.unlink.tooltip");
            case CLEAR_NETWORKS -> PanelModel.panelTr("idle.clear.tooltip");
            case RENAME -> tr("range.rename.tooltip");
            case COPY, PASTE -> Component.empty();
        };
        return withDetail(head, detail);
    }

    /** A dica do ✕ do grupo armado, com o nome dele: o que o segundo clique vai fazer. */
    private static Component unlinkGroupArmedHead(RangeView.GroupView group) {
        Component name = MachineTexts.groupName(group);
        int remotes = group.remotes();
        if (remotes > 0) {
            return tr(remotes == 1 ? "range.unlink_group.armed.head.one" : "range.unlink_group.armed.head", remotes, name);
        }
        return tr(group.attached() ? "range.unlink_group.armed.head.clear" : "range.unlink_group.armed.head.delete", name);
    }

    /** O que o ✕ do grupo faz: desvincula as remotas e apaga o grupo, ou, no da presa, o deixa zerado com ela. */
    private static Component unlinkGroupDetail(RangeView.GroupView group) {
        int remotes = group.remotes();
        if (remotes == 0) {
            return tr(group.attached() ? "range.unlink_group.tooltip.clear" : "range.unlink_group.tooltip.empty");
        }
        String key = "range.unlink_group.tooltip" + (group.attached() ? ".attached" : "");
        return tr(remotes == 1 ? key + ".one" : key, remotes);
    }

    /** O que o Atualizar faz: o bloco que está no lugar da máquina, ou quantas do grupo estão trocadas. */
    static Component updateDetail(RangeView view, EditTarget target) {
        if (target.kind() == EditTarget.Kind.GROUP) {
            return tr("range.update.tooltip.group", RangeRowButtons.replaced(view, target.id()));
        }
        MachineView machine = view.machine(target.id());
        Component block = machine == null || machine.replacedBy() == null ? Component.literal("?")
                : RouterBlock.machineName(BuiltInRegistries.BLOCK.get(machine.replacedBy()));
        return tr("range.update.tooltip.machine", block);
    }
}
