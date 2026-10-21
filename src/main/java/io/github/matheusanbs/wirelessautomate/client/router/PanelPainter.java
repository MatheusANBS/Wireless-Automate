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
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * O desenho do painel do nó fora do cubo e das listas: a linha das abas com o resumo "N redes" e o corpo
 * da face escolhida (motivo do só leitura, a linha da face, os modos, a frase, a Rede, o Filtro, a
 * prioridade e o redstone), mais a faixa do aviso com o Desfazer. As posições vêm do {@link PanelLayout}.
 */
final class PanelPainter {
    private static final ItemStack DUST = new ItemStack(Items.REDSTONE);

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
            renderFaceLine(g, font, x, w, v);
        }
        if (layout.networkY >= 0) {
            label(g, font, tr("network.face"), layout.networkY + 3);
        }
        Hot net = layout.hot(Kind.NETWORK);
        if (net != null) {
            renderNetworkPill(g, font, net, net.contains(mx, my) || popup == PanelLists.Popup.NETWORK, v);
        }
        if (layout.filterY >= 0) {
            renderFilter(g, font, mx, my, v, x, w, cardCount, readOnly);
        }
        if (layout.redstoneY >= 0) {
            renderRedstone(g, font, mx, my, popup, v);
        }
        if (layout.prioY >= 0) {
            renderPriority(g, font, mx, my, v);
        }
    }

    /** O rótulo de uma linha de ajuste, no começo dela; sem rótulo num corpo estreito. */
    private void label(GuiGraphics g, Font font, Component text, int y) {
        if (layout.labelW > 0) {
            GuiText.draw(g, font, text, layout.bodyX, y, layout.labelW - 2, GuiPaint.MUTED);
        }
    }

    /**
     * A linha da face: "Cima ▪ Extrai · 27 slots · roteador aqui", o lado em grafite, a tarja e o modo na cor do
     * modo e o resto apagado (o texto explicando o modo vai na dica do botão da face).
     */
    private void renderFaceLine(GuiGraphics g, Font font, int x, int w, FaceView v) {
        int y = layout.faceY;
        int right = x + w;
        Direction face = model.face();
        Component side = model.sideName(face);
        GuiText.draw(g, font, side, x, y, right - x, GuiPaint.FG);
        int cx = x + font.width(side);
        Component rest = Component.literal(" · ").append(model.access(v));
        if (model.attached(face)) {
            rest = rest.copy().append(" · ").append(tr("face.attached"));
        }
        if (model.differsFromGroup(face)) {
            rest = rest.copy().append(" · ").append(panelTr("face.differs"));
        }
        if (v.available() || model.remote()) {
            int color = GuiPaint.modeColor(v.mode());
            cx += 4;
            g.fill(cx, y + 1, cx + 3, y + 8, color);
            cx += 6;
            Component mode = PanelModel.modeName(v.mode());
            GuiText.draw(g, font, mode, cx, y, right - cx, color);
            cx += font.width(mode);
        }
        if (right - cx > 0) {
            GuiText.draw(g, font, rest, cx, y, right - cx, GuiPaint.MUTED);
        }
    }

    /** A linha do Filtro: o resumo, os slots de cartão (a tela os desenha) e o Editar, ou "sem filtro". */
    private void renderFilter(GuiGraphics g, Font font, int mx, int my, FaceView v, int x, int w, int cardCount,
            @Nullable Component readOnly) {
        label(g, font, tr("filter.label"), layout.filterY + 5);
        Hot filter = layout.hot(Kind.FILTER);
        Hot edit = layout.hot(Kind.EDIT);
        if (filter != null && edit != null) {
            GuiText.draw(g, font, model.filterSummary(v, cardCount), filter.x(), filter.y() + 3, filter.w(),
                    GuiPaint.FG);
            textButton(g, font, edit, tr("filter.edit"), edit.contains(mx, my), readOnly == null, false);
        } else {
            GuiText.draw(g, font, panelTr("filter.none"), x + layout.labelW, layout.filterY + 5, w - layout.labelW,
                    GuiPaint.MUTED);
        }
    }

    /** A linha do Redstone: a caixa com o pó e o modo, e as setas ‹ › que trocam o modo. */
    private void renderRedstone(GuiGraphics g, Font font, int mx, int my, PanelLists.Popup popup, FaceView v) {
        int y = layout.redstoneY;
        label(g, font, panelTr("redstone.label"), y + 3);
        Hot box = layout.hot(Kind.REDSTONE);
        Hot prev = layout.hot(Kind.REDSTONE_PREV);
        Hot next = layout.hot(Kind.REDSTONE_NEXT);
        if (box == null || prev == null || next == null) {
            return;
        }
        boolean editable = model.editable(v);
        int x = box.x();
        int right = next.x() + next.w();
        boolean hovered = (box.contains(mx, my) || popup == PanelLists.Popup.REDSTONE) && editable;
        GuiPaint.box(g, x, y, right - x, PanelLayout.BUTTON_H, editable ? GuiPaint.BUTTON : GuiPaint.INSET,
                !editable ? GuiPaint.LINE : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        g.pose().pushPose();
        g.pose().translate(x + 2, y + 1, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.renderFakeItem(DUST, 0, 0);
        g.pose().popPose();
        GuiText.draw(g, font, PanelModel.redstoneName(v.redstone()), x + 16, y + 3, prev.x() - x - 16,
                editable ? GuiPaint.FG : GuiPaint.DISABLED);
        arrow(g, prev, true, prev.contains(mx, my) && editable, editable);
        arrow(g, next, false, next.contains(mx, my) && editable, editable);
    }

    /** A linha da prioridade: ‹ valor ›, numa caixa só. */
    private void renderPriority(GuiGraphics g, Font font, int mx, int my, FaceView v) {
        int y = layout.prioY;
        GuiText.draw(g, font, panelTr("priority"), layout.bodyX, y + 3, layout.prioLabelW - 2, GuiPaint.MUTED);
        boolean editable = model.editable(v);
        Hot minus = layout.hot(Kind.PRIO_MINUS);
        Hot plus = layout.hot(Kind.PRIO_PLUS);
        if (minus == null || plus == null) {
            return;
        }
        int x = minus.x();
        int w = plus.x() + plus.w() - x;
        GuiPaint.box(g, x, y, w, PanelLayout.BUTTON_H, editable ? GuiPaint.BUTTON : GuiPaint.INSET,
                editable ? GuiPaint.BUTTON_BORDER : GuiPaint.LINE);
        arrow(g, minus, true, minus.contains(mx, my) && editable, editable);
        arrow(g, plus, false, plus.contains(mx, my) && editable, editable);
        GuiPaint.textCentered(g, font, Component.literal(Integer.toString(v.priority())), x + w / 2 + 1, y + 3,
                editable ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    /** A seta ‹ ou › de 3 × 5 px no meio da área. */
    private static void arrow(GuiGraphics g, Hot area, boolean left, boolean hovered, boolean active) {
        int color = !active ? GuiPaint.DISABLED : hovered ? GuiPaint.FG : GuiPaint.MUTED;
        int x = area.x() + (area.w() - 3) / 2;
        int y = area.y() + (area.h() - 5) / 2;
        if (left) {
            g.fill(x + 2, y, x + 3, y + 5, color);
            g.fill(x + 1, y + 1, x + 2, y + 4, color);
            g.fill(x, y + 2, x + 1, y + 3, color);
        } else {
            GuiPaint.arrowRight(g, x, y, color);
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
        int dotY = b.y() + (b.h() - 5) / 2;
        if (entry != null) {
            GuiPaint.dot(g, b.x() + 4, dotY, color);
        } else {
            GuiPaint.outline(g, b.x() + 4, dotY, 5, 5, ask ? GuiPaint.ACCENT : GuiPaint.MUTED);
        }
        int textX = b.x() + 13;
        int room = b.x() + b.w() - 12 - textX;
        Component label = model.networkLabel(model.face());
        if (ask && v.network().isEmpty()) {
            // o pedido inteiro, ou o curto se a pílula for estreita
            label = font.width(panelTr("network.choose")) <= room ? panelTr("network.choose")
                    : panelTr("network.choose.short");
        }
        GuiText.draw(g, font, label, textX, b.y() + (b.h() - 8) / 2, room,
                readOnly ? GuiPaint.DISABLED : entry != null || ask ? GuiPaint.FG : GuiPaint.MUTED);
        GuiPaint.arrowDown(g, b.x() + b.w() - 9, b.y() + b.h() / 2 - 1, hovered ? GuiPaint.FG : GuiPaint.MUTED);
    }

    /** Botão com uma lista: o texto e a seta. */
    static void dropButton(GuiGraphics g, Font font, Hot b, Component text, boolean hovered, boolean active,
            int border) {
        dropButton(g, font, b, text, hovered, active, border, ItemStack.EMPTY);
    }

    /** Botão com uma lista, com o ícone de um item (a 3/4) antes do texto. */
    static void dropButton(GuiGraphics g, Font font, Hot b, Component text, boolean hovered, boolean active,
            int border, ItemStack icon) {
        GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), active ? GuiPaint.BUTTON : GuiPaint.INSET,
                !active ? GuiPaint.LINE : hovered ? GuiPaint.BUTTON_HOVER_BORDER : border);
        int inset = 4;
        if (!icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(b.x() + 3, b.y() + (b.h() - 12) / 2f, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
            inset = 17;
        }
        GuiText.draw(g, font, text, b.x() + inset, b.y() + (b.h() - 8) / 2, b.w() - inset - 11,
                active ? GuiPaint.FG : GuiPaint.DISABLED);
        GuiPaint.arrowDown(g, b.x() + b.w() - 9, b.y() + b.h() / 2 - 1, hovered && active ? GuiPaint.FG : GuiPaint.MUTED);
    }

    static void textButton(GuiGraphics g, Font font, Hot b, Component text, boolean hovered, boolean active,
            boolean armed) {
        int border = armed ? GuiPaint.DANGER : hovered && active ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), active ? GuiPaint.BUTTON : GuiPaint.INSET, active ? border : GuiPaint.LINE);
        int room = b.w() - 4;
        int textW = font.width(text);
        if (textW <= room) {
            GuiPaint.textCentered(g, font, text, b.x() + b.w() / 2 + 1, b.y() + (b.h() - 8) / 2,
                    !active ? GuiPaint.DISABLED : armed ? GuiPaint.DANGER : GuiPaint.FG);
        } else {
            GuiText.draw(g, font, text, b.x() + 2, b.y() + (b.h() - 8) / 2, room,
                    !active ? GuiPaint.DISABLED : armed ? GuiPaint.DANGER : GuiPaint.FG);
        }
    }

    /** O aviso do painel, embaixo dele: o texto, que cresce para cima, e o Desfazer sublinhado de coral. */
    void renderNotice(GuiGraphics g, Font font, PanelNoticePayload notice, int mouseX, int mouseY) {
        Hot undo = layout.hot(Kind.UNDO);
        NoticeBand.render(g, font, notice, layout.bodyX, layout.noticeY(), layout.bodyW, undo != null ? undo.x() : -1,
                undo != null && undo.contains(mouseX, mouseY));
    }
}
