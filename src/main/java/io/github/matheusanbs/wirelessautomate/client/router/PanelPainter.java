package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.NoticeBand;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.TabLayout;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Kind;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O desenho do painel do nó fora do cubo e das listas: a linha das abas com o resumo "N redes" e o corpo
 * da face escolhida (motivo do só leitura, a linha da face, os modos, a frase, a Rede, o Filtro, a
 * prioridade e o redstone), mais a faixa do aviso com o Desfazer. As posições vêm do {@link PanelLayout}.
 */
final class PanelPainter {
    private final PanelModel model;
    private final PanelLayout layout;
    private final TargetLine targetLine;

    PanelPainter(PanelModel model, PanelLayout layout, TargetLine targetLine) {
        this.model = model;
        this.layout = layout;
        this.targetLine = targetLine;
    }

    private static Component tr(String key, Object... args) {
        return PanelModel.tr(key, args);
    }

    private static Component panelTr(String key, Object... args) {
        return PanelModel.panelTr(key, args);
    }

    void renderTabs(GuiGraphics g, Font font, int mx, int my) {
        if (layout.tabsW <= 0) {
            return;
        }
        for (Hot hot : layout.hots) {
            if (hot.kind() != Kind.TAB) {
                continue;
            }
            ResourceType t = ResourceType.values()[hot.arg()];
            boolean selected = t == model.type();
            boolean hovered = hot.contains(mx, my);
            GuiPaint.tab(g, hot.x(), hot.y(), hot.w(), hot.h(), selected, hovered);
            if (hot.w() > TabLayout.ICON_TAB) {
                ResourceStyle.drawIcon(g, t, hot.x() + 4, hot.y() + 3);
                int textX = hot.x() + 4 + ResourceStyle.ICON + 3;
                GuiText.draw(g, font, ResourceStyle.name(t), textX, hot.y() + 4, hot.x() + hot.w() - 4 - textX,
                        GuiPaint.tabText(selected, hovered));
            } else {
                ResourceStyle.drawIcon(g, t, hot.x() + (hot.w() - ResourceStyle.ICON) / 2, hot.y() + 3);
            }
        }
        Hot summary = layout.hot(Kind.SUMMARY);
        if (summary != null) {
            GuiText.draw(g, font, model.networkSummary(), summary.x(), summary.y() + 3, summary.w(), GuiPaint.MUTED);
        }
        int lineY = layout.tabsY + PanelLayout.TAB_H + PanelLayout.TAB_BELOW / 2;
        g.fill(layout.tabsX, lineY, layout.tabsX + layout.tabsW, lineY + 1, GuiPaint.LINE);
    }

    /**
     * O corpo da face escolhida. {@code popup} é a lista aberta, que deixa acesa a pílula ou o botão dono
     * dela; {@code cardCount} vai para o resumo do filtro.
     */
    void renderBody(GuiGraphics g, Font font, int mx, int my, PanelLists.Popup popup, int cardCount) {
        if (layout.bodyW <= 0) {
            return;
        }
        int x = layout.bodyX;
        int w = layout.bodyW;
        FaceView v = model.view();
        Component readOnly = model.readOnly();
        if (readOnly != null && layout.reasonLines > 0) {
            GuiText.wrap(g, font, readOnly, x, layout.reasonY, w, 2, GuiPaint.WARN);
        }
        if (layout.targetY >= 0) {
            targetLine.render(g, font, mx, my, popup == PanelLists.Popup.TARGET);
        }

        if (layout.faceY >= 0) {
            Component detail = model.sideName(model.face()).copy().append(" · ").append(model.access(v));
            if (model.attached(model.face())) {
                detail = detail.copy().append(" · ").append(tr("face.attached"));
            }
            GuiText.draw(g, font, detail, x, layout.faceY, w, GuiPaint.FG);
        }

        for (Hot hot : layout.hots) {
            if (hot.kind() == Kind.MODE) {
                renderMode(g, font, hot, PortMode.values()[hot.arg()], hot.contains(mx, my), v);
            }
        }
        if (layout.phraseY >= 0) {
            GuiText.wrap(g, font, model.phrase(), x, layout.phraseY, w, 2, GuiPaint.MUTED);
        }

        if (layout.networkY >= 0) {
            GuiText.draw(g, font, tr("network.face"), x, layout.networkY + 2, w, GuiPaint.MUTED);
        }
        Hot net = layout.hot(Kind.NETWORK);
        if (net != null) {
            renderNetworkPill(g, font, net, net.contains(mx, my) || popup == PanelLists.Popup.NETWORK, v);
        }

        if (layout.filterY >= 0) {
            renderFilter(g, font, mx, my, v, x, w, cardCount, readOnly);
        }
        if (layout.prioY >= 0) {
            renderPriority(g, font, mx, my, popup, v);
        }
    }

    /** A linha do Filtro: o resumo com o Editar, ou "sem filtro". */
    private void renderFilter(GuiGraphics g, Font font, int mx, int my, FaceView v, int x, int w, int cardCount,
            @Nullable Component readOnly) {
        GuiText.draw(g, font, tr("filter.label"), x, layout.filterY + 3, w, GuiPaint.MUTED);
        Hot filter = layout.hot(Kind.FILTER);
        Hot edit = layout.hot(Kind.EDIT);
        if (filter != null && edit != null) {
            GuiText.draw(g, font, model.filterSummary(v, cardCount), filter.x(), layout.filterY + 3, filter.w(),
                    GuiPaint.FG);
            textButton(g, font, edit, tr("filter.edit"), edit.contains(mx, my), readOnly == null);
        } else {
            int labelW = font.width(tr("filter.label")) + 5;
            GuiText.draw(g, font, panelTr("filter.none"), x + labelW, layout.filterY + 3, w - labelW, GuiPaint.MUTED);
        }
    }

    /** A linha da prioridade (− valor +) com o botão do redstone. */
    private void renderPriority(GuiGraphics g, Font font, int mx, int my, PanelLists.Popup popup, FaceView v) {
        int prioY = layout.prioY;
        if (layout.prioLabel) {
            GuiText.draw(g, font, panelTr("priority"), layout.bodyX, prioY + 3, layout.bodyW, GuiPaint.MUTED);
        }
        boolean editable = model.editable(v);
        Hot minus = layout.hot(Kind.PRIO_MINUS);
        Hot plus = layout.hot(Kind.PRIO_PLUS);
        if (minus != null && plus != null) {
            textButton(g, font, minus, Component.literal("-"), minus.contains(mx, my), editable);
            textButton(g, font, plus, Component.literal("+"), plus.contains(mx, my), editable);
            int boxX = minus.x() + minus.w() + 1;
            GuiPaint.box(g, boxX, prioY, PanelLayout.PRIO_VALUE_W, PanelLayout.BUTTON_H, GuiPaint.INSET, GuiPaint.LINE);
            GuiPaint.textCentered(g, font, Component.literal(Integer.toString(v.priority())),
                    boxX + PanelLayout.PRIO_VALUE_W / 2 + 1, prioY + 3, editable ? GuiPaint.FG : GuiPaint.DISABLED);
        }
        Hot redstone = layout.hot(Kind.REDSTONE);
        if (redstone != null) {
            dropButton(g, font, redstone, layout.redstoneLabel,
                    redstone.contains(mx, my) || popup == PanelLists.Popup.REDSTONE, editable, GuiPaint.BUTTON_BORDER);
        }
    }

    private void renderMode(GuiGraphics g, Font font, Hot hot, PortMode mode, boolean hovered, FaceView v) {
        boolean active = model.editable(v);
        boolean selected = (v.available() || model.remote()) && v.mode() == mode;
        int color = GuiPaint.modeColor(mode);
        int fill = selected ? GuiPaint.mix(GuiPaint.BUTTON, color, 0.28f) : GuiPaint.BUTTON;
        int border = selected ? color : hovered && active ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, hot.x(), hot.y(), hot.w(), hot.h(), fill, border);
        // o botão tem 14 px: o ícone do modo vai a 3/4 (12 px); sem o nome, centrado
        int iconX = layout.modeNames ? hot.x() + 2 : hot.x() + (hot.w() - 12) / 2;
        g.pose().pushPose();
        g.pose().translate(iconX, hot.y() + 1, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        GuiPaint.port(g, mode, 0, 0, active ? 1f : 0.3f);
        g.pose().popPose();
        if (layout.modeNames) {
            GuiText.draw(g, font, PanelModel.modeName(mode), hot.x() + PanelLayout.MODE_TEXT_X + 1, hot.y() + 3,
                    hot.w() - PanelLayout.MODE_TEXT_X - 2, active ? GuiPaint.FG : GuiPaint.DISABLED);
        }
    }

    /** A pílula da linha Rede: borda e ponto na cor da rede; com modo e sem rede, contorno coral e o pedido. */
    private void renderNetworkPill(GuiGraphics g, Font font, Hot b, boolean hovered, FaceView v) {
        NetworkEntry entry = model.network(model.face());
        boolean ask = model.needsNetwork(v);
        boolean readOnly = model.readOnly() != null;
        int color = entry != null ? 0xFF000000 | entry.color() : GuiPaint.MUTED;
        int fill = hovered && !readOnly ? GuiPaint.BUTTON : GuiPaint.PANEL;
        int border = ask ? GuiPaint.ACCENT : entry != null ? color : GuiPaint.LINE;
        GuiPaint.pill(g, b.x(), b.y(), b.w(), b.h(), fill, border);
        if (entry != null) {
            GuiPaint.eye(g, b.x() + 4, b.y() + 3, color);
        } else {
            GuiPaint.outline(g, b.x() + 4, b.y() + 3, 5, 5, ask ? GuiPaint.ACCENT : GuiPaint.MUTED);
        }
        int textX = b.x() + 13;
        int room = b.x() + b.w() - 12 - textX;
        Component label = model.networkLabel(model.face());
        if (ask && v.network().isEmpty()) {
            // o pedido inteiro, ou o curto se a pílula for estreita
            label = font.width(panelTr("network.choose")) <= room ? panelTr("network.choose")
                    : panelTr("network.choose.short");
        }
        GuiText.draw(g, font, label, textX, b.y() + 2, room,
                readOnly ? GuiPaint.DISABLED : entry != null || ask ? GuiPaint.FG : GuiPaint.MUTED);
        GuiPaint.arrowDown(g, b.x() + b.w() - 9, b.y() + 4, hovered ? GuiPaint.FG : GuiPaint.MUTED);
    }

    /** Botão com uma lista: o texto e a seta. */
    static void dropButton(GuiGraphics g, Font font, Hot b, Component text, boolean hovered, boolean active,
            int border) {
        GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), active ? GuiPaint.BUTTON : GuiPaint.INSET,
                !active ? GuiPaint.LINE : hovered ? GuiPaint.BUTTON_HOVER_BORDER : border);
        GuiText.draw(g, font, text, b.x() + 4, b.y() + (b.h() - 8) / 2, b.w() - 15, active ? GuiPaint.FG : GuiPaint.DISABLED);
        GuiPaint.arrowDown(g, b.x() + b.w() - 9, b.y() + b.h() / 2 - 1, hovered && active ? GuiPaint.FG : GuiPaint.MUTED);
    }

    private static void textButton(GuiGraphics g, Font font, Hot b, Component text, boolean hovered, boolean active) {
        int border = hovered && active ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), active ? GuiPaint.BUTTON : GuiPaint.INSET, active ? border : GuiPaint.LINE);
        GuiPaint.textCentered(g, font, text, b.x() + b.w() / 2 + 1, b.y() + (b.h() - 8) / 2,
                active ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    /** O aviso do painel, embaixo dele: o texto, que cresce para cima, e o Desfazer sublinhado de coral. */
    void renderNotice(GuiGraphics g, Font font, PanelNoticePayload notice, int mouseX, int mouseY) {
        Hot undo = layout.hot(Kind.UNDO);
        NoticeBand.render(g, font, notice, layout.bodyX, layout.noticeY(), layout.bodyW, undo != null ? undo.x() : -1,
                undo != null && undo.contains(mouseX, mouseY));
    }
}
