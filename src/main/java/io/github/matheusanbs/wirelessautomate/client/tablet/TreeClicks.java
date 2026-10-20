package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderer;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Kind;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Line;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.menu.TreeOrder;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.packet.TabletTreePayload.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * O mouse nas linhas da árvore da Lista do Tablet e as dicas delas: o ▸/▾ abre e fecha o nó ou recolhe o grupo, o
 * "mais N" mostra todas e o "Mostrar menos" volta a poucas (só o {@code TabletTreePayload}; a árvore nova vem na
 * próxima página), o Olho acende a máquina no mundo e o resto da linha a escolhe ({@link TreeNav#choose}). No modo
 * Selecionar, que marca só nós, o clique no corpo de uma linha da árvore não faz nada.
 */
final class TreeClicks {
    private final TabletList list;

    TreeClicks(TabletList list) {
        this.list = list;
    }

    void click(Hot hot) {
        ListTree<ResourceLocation> tree = list.model.tree();
        Line<ResourceLocation> line = tree.lines().get(hot.arg());
        NodeView node = list.snapshot().nodes().get(line.node());
        switch (hot.kind()) {
            case TREE_TOGGLE -> {
                if (line.kind() == Kind.NODE) {
                    list.treeNav.send(tree.open(line.node()) ? Action.CLOSE : Action.OPEN, node.key(), 0);
                } else if (list.model.row(line) instanceof TabletRows.Group group) {
                    list.treeNav.send(group.collapsed() ? Action.EXPAND_GROUP : Action.COLLAPSE_GROUP, node.key(),
                            group.id());
                }
            }
            case TREE_FEW -> {
                if (list.model.row(line) instanceof TabletRows.Group group) {
                    list.treeNav.send(Action.SHOW_FEW, node.key(), group.id());
                }
            }
            case TREE_EYE -> {
                if (list.model.row(line) instanceof TabletRows.Machine machine) {
                    HighlightRenderer.toggle(node.key().dimension(), BlockPos.of(machine.pos()), node.key().pos());
                }
            }
            case TREE -> {
                if (line.kind() == Kind.MORE || !list.multi) {
                    list.treeNav.choose(hot.arg());
                }
            }
            default -> {
            }
        }
    }

    /** A dica de uma área da árvore, ou {@code null} se a área não é dela. */
    @Nullable Component tooltip(Hot hot) {
        Line<ResourceLocation> line = list.model.tree().lines().get(hot.arg());
        NodeView node = list.snapshot().nodes().get(line.node());
        return switch (hot.kind()) {
            case TREE_TOGGLE -> line.kind() == Kind.NODE
                    ? tr(list.model.tree().open(line.node()) ? "tree.close.tooltip" : "tree.open.tooltip")
                    : tr(line.collapsed() ? "tree.group.open" : "tree.group.close");
            case TREE_FEW -> tr("tree.show_few.tooltip", TreeOrder.FEW);
            case TREE_EYE -> list.model.row(line) instanceof TabletRows.Machine machine
                    && HighlightRenderer.isLit(node.key().dimension(), machine.pos())
                    ? Component.translatable("gui.wirelessautomate.highlight.eye.off.tooltip")
                    : Component.translatable("gui.wirelessautomate.highlight.eye.on.tooltip");
            case TREE -> lineTooltip(line);
            default -> null;
        };
    }

    private Component lineTooltip(Line<ResourceLocation> line) {
        TreeTexts texts = list.treeRows.texts;
        int r = line.row();
        return switch (list.model.row(line)) {
            case TabletRows.Attached attached -> texts.names[r].copy().append("\n")
                    .append(texts.seconds[r].copy().withColor(GuiPaint.TOOLTIP_MUTED)).append("\n")
                    .append(Component.translatable("gui.wirelessautomate.router.range.attached.tooltip").copy()
                            .withColor(GuiPaint.TOOLTIP_MUTED));
            case TabletRows.Group group -> {
                MutableComponent text = texts.names[r].copy();
                if (texts.secondW[r] > 0) {
                    text.append("\n").append(texts.seconds[r].copy().withColor(GuiPaint.TOOLTIP_MUTED));
                }
                yield text.append("\n").append(Component.translatable("gui.wirelessautomate.router.range.group.tooltip")
                        .copy().withColor(GuiPaint.TOOLTIP_MUTED));
            }
            case TabletRows.Machine machine -> machineTooltip(machine, r, texts);
            case TabletRows.More more -> tr("tree.more.tooltip");
        };
    }

    /** O nome com o bloco, as coordenadas, se segue o perfil, o estado, a vazão por extenso e o que o clique faz. */
    private Component machineTooltip(TabletRows.Machine machine, int r, TreeTexts texts) {
        MutableComponent text = texts.names[r].copy();
        Component follows = Component.translatable("gui.wirelessautomate.router.range.machine."
                + (machine.exception() ? "exception" : "follows"));
        text.append("\n").append(texts.seconds[r].copy().append(TreeTexts.SEPARATOR).append(follows)
                .withColor(GuiPaint.TOOLTIP_MUTED));
        MachineLive live = texts.lives[r];
        if (live != null) {
            text.append("\n").append(texts.states[r].copy().withColor(TreeTexts.stateColor(live.status())));
            text.append(TreeTexts.rateTooltip(live));
        }
        return text.append("\n").append(Component.translatable("gui.wirelessautomate.router.range.machine.tooltip")
                .copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }
}
