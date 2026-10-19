package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Hot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** As dicas do painel do nó: a de cada controle e a de cada face do cubo. */
final class PanelTooltips {
    private final PanelModel model;
    private final TargetLine targetLine;

    PanelTooltips(PanelModel model, TargetLine targetLine) {
        this.model = model;
        this.targetLine = targetLine;
    }

    private static Component tr(String key, Object... args) {
        return PanelModel.tr(key, args);
    }

    private static Component panelTr(String key, Object... args) {
        return PanelModel.panelTr(key, args);
    }

    /** A dica da área {@code hot}, ou {@code null}. */
    @Nullable Component of(Hot hot) {
        FaceView v = model.view();
        Component readOnly = model.readOnly();
        Component disabled = readOnly != null ? readOnly : tr("mode.unavailable");
        return switch (hot.kind()) {
            case TAB -> tr("tab.tooltip", ResourceStyle.name(ResourceType.values()[hot.arg()]));
            case SUMMARY -> model.networkSummaryTooltip();
            case FACE -> faceTooltip(model.faceOf(RemoteSide.values()[hot.arg()]));
            case MODE -> model.editable(v)
                    ? tr("mode." + PortMode.values()[hot.arg()].name().toLowerCase(Locale.ROOT) + ".tooltip")
                    : disabled;
            case NETWORK -> readOnly != null ? readOnly : networkTooltip(v);
            case FILTER -> v.filterSize() == 0 ? null : tr(v.blacklist() ? "filter.black" : "filter.white");
            case EDIT -> readOnly != null ? readOnly : tr("filter.edit.tooltip");
            case PRIO_MINUS, PRIO_PLUS -> model.editable(v) ? tr("priority.tooltip") : disabled;
            case REDSTONE -> model.editable(v)
                    ? tr("redstone.narration", PanelModel.redstoneName(v.redstone())).copy().append("\n")
                            .append(panelTr("redstone.hint").copy().withColor(GuiPaint.TOOLTIP_MUTED))
                    : disabled;
            case TARGET -> readOnly != null ? readOnly : targetLine.tooltip();
            // a sua dica, sem os dois cliques da lista: na pílula, eles abrem a lista do alvo
            case RENAME -> tr("range.rename").copy().append("\n")
                    .append(tr("range.rename.panel.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
            case IDLE_NETWORKS -> readOnly != null ? readOnly : IdleNetworks.tooltip();
            case REVERT -> readOnly != null ? readOnly : tr("range.revert.tooltip");
            case UPDATE -> readOnly != null ? readOnly : machineTooltip(RangeRowButtons.Action.UPDATE);
            case UNLINK -> readOnly != null ? readOnly : machineTooltip(RangeRowButtons.Action.UNLINK);
            case UNDO -> null;
        };
    }

    /** A dica de um botão da máquina, a mesma da lista das máquinas. */
    private Component machineTooltip(RangeRowButtons.Action action) {
        return RangeRowButtons.tooltip(model.range(), model.snapshot().target(), action, model.unlinkArmed());
    }

    /** Dica de uma face: o lado (e, na presa, o absoluto), o modo e o acesso, a rede e o roteador preso. */
    private Component faceTooltip(Direction direction) {
        FaceView v = model.view(direction);
        Component head = model.remote() ? model.sideName(direction)
                : model.sideName(direction).copy().append(" · ").append(tr("face." + direction.getName()));
        Component line = v.available() || model.remote()
                ? PanelModel.modeName(v.mode()).copy().append(" · ").append(model.access(v))
                : model.access(v);
        NetworkEntry entry = model.network(direction);
        Component net = entry != null ? tr("network.face.line", entry.name())
                : v.network().isPresent() ? tr("network.unknown") : tr("network.none");
        Component text = head.copy().append("\n").append(line).append("\n")
                .append(net.copy().withColor(GuiPaint.TOOLTIP_MUTED));
        if (model.attached(direction)) {
            text = text.copy().append("\n").append(tr("face.attached.tooltip"));
        }
        return text;
    }

    private Component networkTooltip(FaceView v) {
        Direction face = model.face();
        Component text = tr("network.face.tooltip", model.sideName(face), ResourceStyle.name(model.type())).copy()
                .append("\n").append(model.networkLabel(face));
        if (model.needsNetwork(v)) {
            text = text.copy().append("\n").append(panelTr("network.no_active").copy().withColor(GuiPaint.WARN));
        }
        return text.copy().append("\n").append(tr("network.face.hint").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }
}
