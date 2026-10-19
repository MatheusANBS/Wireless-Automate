package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.BANNER_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.NoticeBand;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Kind;
import io.github.matheusanbs.wirelessautomate.menu.PanelAccess;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.ProfileSummary;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickFilter;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * O desenho da Lista do Tablet sobre as áreas do {@link ListLayout}: a busca com as etiquetas, os filtros
 * rápidos, a faixa do perfil, a linha de baixo com a contagem, o painel do nó escolhido com Copiar e Colar e o
 * aviso sem painel. As linhas dos nós e dos grupos ficam no {@link ListRows}.
 */
final class ListPainter {
    private final TabletList list;
    private final ListLayout layout;
    /** A linha embaixo do nome do nó, guardada pelo snapshot e pela distância em blocos. */
    private @Nullable RouterSnapshot metaSnap;
    private int metaDistance = Integer.MIN_VALUE;
    private Component metaText = Component.empty();

    ListPainter(TabletList list, ListLayout layout) {
        this.list = list;
        this.layout = layout;
    }

    void renderSearch(GuiGraphics g, Font font, int mx, int my) {
        EditBox searchBox = list.search.box();
        boolean focused = searchBox != null && searchBox.isFocused();
        GuiPaint.box(g, layout.x0, layout.y0, layout.listW, ListLayout.SEARCH_H, GuiPaint.INSET,
                focused ? GuiPaint.ACCENT : GuiPaint.LINE);
        if (searchBox != null && list.search.text.isEmpty()) {
            // a dica fica com o cursor na caixa (a do EditBox some com o foco); a curta quando a inteira não cabe
            int room = searchBox.getWidth() - 4;
            Component hint = font.width(tr("search")) <= room ? tr("search") : tr("search.short");
            GuiText.draw(g, font, hint, searchBox.getX() + (focused ? 7 : 0), searchBox.getY(), room - 7,
                    GuiPaint.DISABLED);
        }
        List<ListSearch.Label> labels = list.search.labels;
        for (int i = 0; i < labels.size(); i++) {
            Hot hot = layout.hot(Kind.LABEL, i);
            if (hot == null) {
                continue;
            }
            ListSearch.Label label = labels.get(i);
            boolean hovered = hot.contains(mx, my);
            int color = label.network() != null
                    ? list.snapshot().network(label.network()).map(n -> 0xFF000000 | n.color()).orElse(GuiPaint.MUTED)
                    : GuiPaint.BUTTON_BORDER;
            GuiPaint.box(g, hot.x(), hot.y(), hot.w(), hot.h(), GuiPaint.BUTTON, hovered ? GuiPaint.ACCENT : color);
            GuiText.draw(g, font, list.texts.labelText(label), hot.x() + 3, hot.y() + 1, hot.w() - 12, GuiPaint.FG);
            GuiPaint.text(g, font, Component.literal("✕"), hot.x() + hot.w() - 8, hot.y() + 1,
                    hovered ? GuiPaint.ACCENT : GuiPaint.MUTED);
        }
    }

    void renderQuick(GuiGraphics g, Font font, int mx, int my) {
        for (Hot hot : layout.hots) {
            boolean hovered = hot.contains(mx, my);
            if (hot.kind() == Kind.TYPE_CHIP && list.search.typeFilter.isPresent()) {
                ResourceType type = list.search.typeFilter.get();
                int color = ResourceStyle.color(type);
                GuiPaint.box(g, hot.x(), hot.y(), hot.w(), hot.h(), GuiPaint.mix(GuiPaint.BUTTON, color, 0.18f),
                        hovered ? color : GuiPaint.BUTTON_BORDER);
                g.pose().pushPose();
                g.pose().translate(hot.x() + 2, hot.y() + 1, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                ResourceStyle.drawIcon(g, type, 0, 0);
                g.pose().popPose();
                int textX = hot.x() + 3 + ResourceStyle.ICON;
                GuiText.draw(g, font, tr("list.type", ResourceStyle.name(type)), textX, hot.y() + 2,
                        hot.x() + hot.w() - 2 - textX, hovered ? GuiPaint.FG : color);
            } else if (hot.kind() == Kind.QUICK) {
                QuickFilter filter = QuickFilter.values()[hot.arg()];
                boolean on = list.search.quick.contains(filter);
                GuiPaint.box(g, hot.x(), hot.y(), hot.w(), hot.h(), on ? GuiPaint.BUTTON : GuiPaint.PANEL,
                        on ? GuiPaint.FG : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.LINE);
                if (on) {
                    g.fill(hot.x() + 1, hot.y() + hot.h() - 2, hot.x() + hot.w() - 1, hot.y() + hot.h() - 1,
                            GuiPaint.ACCENT);
                }
                g.fill(hot.x() + 3, hot.y() + 3, hot.x() + 7, hot.y() + 7, quickColor(filter));
                int index = quickIndex(hot);
                Component text = index < layout.quickLabels.size() ? layout.quickLabels.get(index) : Component.empty();
                GuiText.draw(g, font, text, hot.x() + 9, hot.y() + 2, hot.w() - 11,
                        on || hovered ? GuiPaint.FG : GuiPaint.MUTED);
            }
        }
    }

    /** A posição da área entre as do tipo e dos filtros rápidos (a mesma do texto em {@link ListLayout#quickLabels}). */
    private int quickIndex(Hot target) {
        int index = 0;
        for (Hot hot : layout.hots) {
            if (hot == target) {
                return index;
            }
            if (hot.kind() == Kind.QUICK || hot.kind() == Kind.TYPE_CHIP) {
                index++;
            }
        }
        return index;
    }

    private static int quickColor(QuickFilter filter) {
        return switch (filter) {
            case PROBLEM -> GuiPaint.WARN;
            case NO_NETWORK -> GuiPaint.DANGER;
            case UNLOADED -> GuiPaint.MUTED;
        };
    }

    void renderBanner(GuiGraphics g, Font font, int mx, int my) {
        ProfileSummary s = list.profile().orElse(null);
        Hot forget = layout.hot(Kind.FORGET, 0);
        if (s == null || forget == null) {
            return;
        }
        int x0 = layout.x0;
        int y = forget.y();
        g.fill(x0 + 1, y + 1, x0 + layout.listW - 1, y + BANNER_H - 1, GuiPaint.BUTTON);
        dashed(g, x0, y, layout.listW, BANNER_H, GuiPaint.ACCENT);
        GuiPaint.text(g, font, Component.literal("▣"), x0 + 3, y + 2, GuiPaint.ACCENT);
        GuiText.draw(g, font, tr("list.profile.short", list.texts.sourceName(s)), x0 + 12, y + 2, forget.x() - x0 - 14,
                GuiPaint.FG);
        GuiPaint.text(g, font, layout.forgetLabel(font), forget.x() + 3, y + 2,
                forget.contains(mx, my) ? GuiPaint.ACCENT : GuiPaint.MUTED);
    }

    /** A linha de baixo da lista: a contagem com Limpar (com busca ou filtro). */
    void renderBar(GuiGraphics g, Font font, int mx, int my) {
        if (list.multi || !list.search.filtering()) {
            return;
        }
        Hot clear = layout.hot(Kind.CLEAR, 0);
        int barY = layout.barY();
        int countW = clear == null ? 0 : clear.x() - 4 - layout.x0;
        GuiText.draw(g, font, list.texts.countText(), layout.x0, barY + 3, countW, GuiPaint.MUTED);
        if (clear != null) {
            GuiPaint.text(g, font, Component.literal("·"), clear.x() - 4, barY + 3, GuiPaint.MUTED);
            boolean over = clear.contains(mx, my);
            GuiPaint.text(g, font, tr("list.clear"), clear.x() + 1, barY + 3, over ? GuiPaint.ACCENT : GuiPaint.FG);
            g.fill(clear.x() + 1, barY + 12, clear.x() + clear.w() - 1, barY + 13, GuiPaint.ACCENT);
        }
    }

    void renderPanel(GuiGraphics g, Font font, int mx, int my) {
        int panelX = layout.panelX;
        int panelW = layout.panelW;
        int y0 = layout.y0;
        if (panelW <= 0) {
            return;
        }
        if (list.chosen == null) {
            GuiText.wrapCentered(g, font, tr("list.panel.pick"), panelX + panelW / 2, y0 + 60, panelW - 8, 3,
                    GuiPaint.MUTED);
            return;
        }
        TabletPanelPayload payload = list.chosenPanel.payload();
        RouterSnapshot snap = payload == null ? null : payload.snapshot().orElse(null);
        NodeView node = list.model.node(list.chosen);
        Hot full = layout.hot(Kind.FULL_SCREEN, 0);
        int nameRoom = (full == null ? panelX + panelW : full.x() - 4) - panelX;
        Component name = snap != null && !snap.name().isEmpty() ? Component.literal(snap.name())
                : node != null ? list.texts.nodeName(node)
                : snap != null ? RouterBlock.machineName(snap.machineState().getBlock()) : Component.empty();
        GuiText.draw(g, font, name, panelX, y0 + 2, nameRoom, GuiPaint.FG);
        if (full != null) {
            textButton(g, font, full, layout.fullScreenLabel(font), full.contains(mx, my), true);
        }
        if (snap == null) {
            Component wait = payload == null ? tr("list.panel.loading")
                    : Objects.requireNonNullElse(ChosenPanel.readOnlyReason(payload), tr("list.panel.loading"));
            GuiText.wrap(g, font, wait, panelX, y0 + 16, panelW, 3, payload == null ? GuiPaint.MUTED : GuiPaint.WARN);
            return;
        }
        Component meta = meta(snap, payload);
        GuiText.draw(g, font, meta, panelX, y0 + 13, panelW, GuiPaint.MUTED);
        NodePanel p = list.chosenPanel.current();
        if (p == null) {
            return;
        }
        p.render(g, list.search.suggestions.contains(mx, my) ? -1 : mx, my, null);
        renderProfileButtons(g, font, mx, my, payload.access() == PanelAccess.EDIT);
    }

    /**
     * A linha embaixo do nome do nó: a máquina presa, o tier e a distância; com uma máquina do alcance como alvo, a
     * máquina, as coordenadas dela e a distância até ela (o nome do nó já diz o roteador). Montada só quando o
     * snapshot ou a distância mudam.
     */
    private Component meta(RouterSnapshot snap, TabletPanelPayload payload) {
        RangeView.MachineView target = snap.target().kind() == EditTarget.Kind.MACHINE
                ? snap.range().machine(snap.target().id()) : null;
        BlockPos where = target != null ? target.pos() : payload.node().map(NodeKey::pos).orElse(null);
        int blocks = payload.distance() < 0 || where == null ? -1 : localDistance(where);
        if (snap == metaSnap && blocks == metaDistance) {
            return metaText;
        }
        metaSnap = snap;
        metaDistance = blocks;
        Component machine = RouterBlock.machineName(snap.machineState().getBlock());
        Component distance = blocks < 0 ? tr("list.panel.other_dimension") : plural("list.panel.distance", blocks);
        metaText = target != null
                ? machine.copy().append(" · ").append(target.pos().getX() + " " + target.pos().getY() + " "
                        + target.pos().getZ()).append(" · ").append(distance)
                : machine.copy().append(" · ").append(Component.translatable(snap.tier().translationKey()))
                        .append(" · ").append(distance);
        return metaText;
    }

    /** Copiar e Colar o perfil embaixo do painel (ou Copiar e a dica, sem perfil copiado). */
    private void renderProfileButtons(GuiGraphics g, Font font, int mx, int my, boolean edit) {
        Hot copy = layout.hot(Kind.COPY, 0);
        if (copy != null) {
            textButton(g, font, copy, layout.copyLabel(), copy.contains(mx, my), edit);
            Hot paste = layout.hot(Kind.PASTE, 0);
            if (paste != null) {
                textButton(g, font, paste, layout.pasteLabel(), paste.contains(mx, my), edit);
            } else {
                int hintX = copy.x() + copy.w() + 4;
                GuiText.draw(g, font, tr("list.copy.hint"), hintX, copy.y() + 3, layout.panelX + layout.panelW - hintX,
                        GuiPaint.DISABLED);
            }
        }
    }

    private static void textButton(GuiGraphics g, Font font, Hot b, Component text, boolean hovered, boolean active) {
        int border = hovered && active ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), active ? GuiPaint.BUTTON : GuiPaint.INSET, active ? border : GuiPaint.LINE);
        GuiText.draw(g, font, text, b.x() + 4, b.y() + (b.h() - 8) / 2, b.w() - 7, active ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    /** O aviso de colar sem painel aberto: embaixo da coluna do painel, crescendo para cima, com o Desfazer. */
    void renderOwnNotice(GuiGraphics g, Font font, int mouseX, int mouseY) {
        PanelNoticePayload notice = PanelNotices.current();
        if (notice == null || layout.panelW <= 0) {
            return;
        }
        Hot undo = layout.hot(Kind.UNDO, 0);
        NoticeBand.render(g, font, notice, layout.panelX, layout.ownNoticeY(), layout.panelW,
                undo != null ? undo.x() : -1, undo != null && undo.contains(mouseX, mouseY));
    }

    /** Contorno tracejado de 1 px (a faixa do perfil). */
    private static void dashed(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int i = 0; i < w; i += 3) {
            int end = Math.min(i + 2, w);
            g.fill(x + i, y, x + end, y + 1, color);
            g.fill(x + i, y + h - 1, x + end, y + h, color);
        }
        for (int i = 0; i < h; i += 3) {
            int end = Math.min(i + 2, h);
            g.fill(x, y + i, x + 1, y + end, color);
            g.fill(x + w - 1, y + i, x + w, y + end, color);
        }
    }

    /**
     * Blocos entre o jogador e o nó escolhido (ou a máquina do alvo), pela posição local (o servidor só diz se é
     * a mesma dimensão).
     */
    private static int localDistance(BlockPos pos) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        return (int) Math.round(Math.sqrt(player.position().distanceToSqr(Vec3.atCenterOf(pos))));
    }
}
