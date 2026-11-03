package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.HEAD_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.SEP_Y;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen.Tab;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A moldura do Tablet (mockup {@code moldura}): o relevo do painel, "Tablet" em negrito, as três abas à direita (a
 * ativa em {@code FG} com o sublinhado coral, as outras em {@code MUTED}), o botão do tema logo depois do título, a
 * linha embaixo da barra e o aviso do servidor (4 s) entre o botão do tema e as abas. As abas são {@link FlatButton}s
 * (o e2e as acha pelo texto); as larguras são medidas no {@code init}.
 */
final class TabletFrame {
    /** A ordem da barra, da esquerda para a direita. O Mapa fica fora (sai do Tablet na etapa 4). */
    static final Tab[] BAR = {Tab.NETWORKS, Tab.LIST, Tab.FLOWS};
    private static final int TITLE_X = 8;
    private static final int TAB_Y = 6;
    private static final int TAB_H = 17;
    private static final int TAB_GAP = 16;
    private static final int NOTICE_Y = 4;
    private static final int NOTICE_H = 16;

    private final TabletLayout layout;
    private final Component title = tr("frame.title").copy().withStyle(ChatFormatting.BOLD);
    private int titleW;
    /** Onde começa a primeira aba, relativo ao painel (o aviso termina antes dela). */
    private int firstTabX;
    private int lastNoticeId;
    private long noticeUntil;
    private Component noticeText = Component.empty();

    /** @param noticeId o aviso do snapshot de abertura, que não aparece de novo */
    TabletFrame(TabletLayout layout, int noticeId) {
        this.layout = layout;
        this.lastNoticeId = noticeId;
    }

    int themeX() {
        return layout.left + TITLE_X + titleW + 6;
    }

    int themeY() {
        return layout.top + 7;
    }

    private static String key(Tab t) {
        return switch (t) {
            case LIST -> "machine";
            case FLOWS -> "flows";
            case NETWORKS -> "networks";
            case MAP -> "map";
        };
    }

    private static Component label(Tab t) {
        return tr("tab." + key(t));
    }

    /** Mede o título e põe as abas da barra, da direita para a esquerda. */
    void addTabs(TabletScreen.Buttons buttons, Font font, Supplier<Tab> current, Consumer<Tab> switchTo) {
        titleW = font.width(title);
        int left = layout.left;
        int top = layout.top;
        int ax = layout.w - 12;
        for (int i = BAR.length - 1; i >= 0; i--) {
            Tab t = BAR[i];
            Component text = label(t);
            int tw = font.width(text);
            ax -= tw;
            buttons.add(null, new FlatButton(left + ax - 1, top + TAB_Y, tw + 2, TAB_H, text,
                    (g, b, hovered) -> paintTab(g, font, b, tw, t == current.get(), hovered), () -> switchTo.accept(t)));
            firstTabX = ax - 1;
            ax -= TAB_GAP;
        }
    }

    private void paintTab(GuiGraphics g, Font font, FlatButton b, int tw, boolean active, boolean hovered) {
        int top = layout.top;
        GuiPaint.text(g, font, b.getMessage(), b.getX() + 1, top + HEAD_Y, active || hovered ? GuiPaint.FG : GuiPaint.MUTED);
        if (active) {
            g.fill(b.getX(), top + 21, b.getX() + tw + 1, top + SEP_Y, GuiPaint.ACCENT);
        }
    }

    /** Snapshot novo: um aviso novo aparece por 4 s. */
    void onSnapshot(TabletSnapshot s) {
        if (s.noticeId() != lastNoticeId) {
            lastNoticeId = s.noticeId();
            noticeText = s.notice();
            noticeUntil = noticeText.getString().isEmpty() ? 0 : System.currentTimeMillis() + 4000;
        }
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x = layout.left;
        int y = layout.top;
        int w = layout.w;
        PanelPaint.relief(g, x, y, w, layout.h, GuiPaint.PANEL, GuiPaint.RELIEF_LIGHT, GuiPaint.RELIEF_DARK);
        GuiPaint.text(g, font, title, x + TITLE_X, y + HEAD_Y, GuiPaint.FG);
        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);
        g.fill(x + 2, y + SEP_Y, x + w - 2, y + SEP_Y + 1, GuiPaint.INSET);
    }

    /** O aviso do servidor, por cima da barra, entre o botão do tema e a primeira aba. */
    void renderNotice(GuiGraphics g, Font font) {
        if (System.currentTimeMillis() > noticeUntil) {
            return;
        }
        int x = themeX() + ThemeToggle.SIZE + 6;
        int room = layout.left + firstTabX - 6 - x;
        int width = Math.min(room, font.width(noticeText) + 12);
        if (width <= 12) {
            return;
        }
        int y = layout.top + NOTICE_Y;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        GuiPaint.box(g, x, y, width, NOTICE_H, GuiPaint.INSET, GuiPaint.FG);
        GuiText.draw(g, font, noticeText, x + 6, y + 4, width - 12, GuiPaint.FG);
        g.pose().popPose();
    }
}
