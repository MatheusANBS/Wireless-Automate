package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.HEAD_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.SEP_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * O cabeçalho da tela do Tablet: o título, as contagens de nós, máquinas e redes (montadas quando o snapshot
 * chega), o botão do tema e a linha que o separa das abas, e o aviso do servidor (4 s), desenhado por cima do
 * cabeçalho.
 */
final class TabletHeader {
    private final TabletLayout layout;
    private int lastNoticeId;
    private long noticeUntil;
    private Component noticeText = Component.empty();
    private Component meta = Component.empty();
    private int metaW;
    private int metaNodes = -1;
    private int metaMachines = -1;
    private int metaNetworks = -1;

    /** @param noticeId o aviso do snapshot de abertura, que não aparece de novo */
    TabletHeader(TabletLayout layout, int noticeId) {
        this.layout = layout;
        this.lastNoticeId = noticeId;
    }

    int themeX() {
        return layout.left + layout.w - 4 - ThemeToggle.SIZE;
    }

    int themeY() {
        return layout.top + HEAD_Y + 2;
    }

    /** Snapshot novo: um aviso novo aparece; as contagens, se mudaram. */
    void onSnapshot(TabletSnapshot s) {
        if (s.totalNodes() != metaNodes || s.machines() != metaMachines || s.networks().size() != metaNetworks) {
            metaNodes = s.totalNodes();
            metaMachines = s.machines();
            metaNetworks = s.networks().size();
            meta = plural("meta.nodes", metaNodes).copy().append(" · ").append(plural("meta.machines", metaMachines))
                    .append(" · ").append(plural("meta.networks", metaNetworks));
            metaW = TabletList.font().width(meta);
        }
        if (s.noticeId() != lastNoticeId) {
            lastNoticeId = s.noticeId();
            noticeText = s.notice();
            noticeUntil = System.currentTimeMillis() + 4000;
        }
    }

    void render(GuiGraphics g, Font font, TabletSnapshot s, int mouseX, int mouseY) {
        int x = layout.left;
        int y = layout.top;
        int x1 = layout.x1();
        GuiPaint.text(g, font, tr("title"), x + X0, y + HEAD_Y + 3, GuiPaint.FG);
        int titleEnd = X0 + font.width(tr("title")) + 8;
        int shownW = Math.min(metaW, x1 - ThemeToggle.SIZE - 4 - titleEnd);
        GuiText.draw(g, font, meta, x + x1 - ThemeToggle.SIZE - 4 - shownW, y + HEAD_Y + 3, shownW, GuiPaint.MUTED);
        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);
        g.fill(x + X0, y + SEP_Y, x + x1, y + SEP_Y + 1, GuiPaint.LINE);
    }

    void renderNotice(GuiGraphics g, Font font) {
        if (System.currentTimeMillis() > noticeUntil || noticeText.getString().isEmpty()) {
            return;
        }
        // sobre o cabeçalho, até antes do botão do tema: embaixo cobriria botões
        int right = themeX() - 3;
        int width = Math.min(right - (layout.left + X0), font.width(noticeText) + 12);
        int x = right - width;
        int y = layout.top + HEAD_Y - 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        GuiPaint.box(g, x, y, width, 16, GuiPaint.INSET, GuiPaint.ACCENT);
        GuiText.draw(g, font, noticeText, x + 6, y + 4, width - 12, GuiPaint.FG);
        g.pose().popPose();
    }
}
