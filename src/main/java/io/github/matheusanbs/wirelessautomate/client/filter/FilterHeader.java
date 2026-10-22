package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.HEAD_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.PANEL_W;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.ROW2_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.SEG_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.TABS_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.TAB_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.paintSegment;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.paintTab;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.paintTextButton;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen.Tab;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.packet.EditFilterPayload.Op;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * O alto da tela de filtro: o título, o contexto (face, cartão ou armazenamento e o tipo), Voltar (ou
 * Fechar, no cartão), o botão do tema, lista branca ou negra, Componentes e os botões das abas.
 */
final class FilterHeader {
    private final FilterScreen screen;
    private final FilterFrame frame;
    private final FilterText text;
    private final FilterActions actions;

    private FlatButton backButton;
    private FlatButton whiteButton;
    private FlatButton blackButton;
    private final FlatButton[] tabButtons = new FlatButton[Tab.values().length];

    FilterHeader(FilterScreen screen, FilterText text, FilterActions actions) {
        this.screen = screen;
        this.frame = screen.frame();
        this.text = text;
        this.actions = actions;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private Filter filter() {
        return screen.filter();
    }

    void addWidgets() {
        Font font = font();
        backButton = screen.add(new FlatButton(0, 0, 40, SEG_H, Component.empty(),
                (g, b, hovered) -> paintTextButton(g, b, hovered, backLabel(), false), this::back)
                .tooltip(() -> screen.view().isCard() ? tr("close.tooltip") : tr("back.tooltip")));
        Component white = tr("list.white");
        Component black = tr("list.black");
        whiteButton = screen.add(new FlatButton(0, 0, font.width(white) + 12, SEG_H, white,
                (g, b, hovered) -> paintSegment(g, b, hovered, white, filter().listMode() == Filter.ListMode.WHITELIST),
                () -> setListMode(Filter.ListMode.WHITELIST)).tooltip(() -> tr("list.white.tooltip")));
        blackButton = screen.add(new FlatButton(0, 0, font.width(black) + 12, SEG_H, black,
                (g, b, hovered) -> paintSegment(g, b, hovered, black, filter().listMode() == Filter.ListMode.BLACKLIST),
                () -> setListMode(Filter.ListMode.BLACKLIST)).tooltip(() -> tr("list.black.tooltip")));
        for (Tab t : Tab.values()) {
            Component label = tabLabel(t);
            tabButtons[t.ordinal()] = screen.add(new FlatButton(0, 0, 40, TAB_H, label,
                    (g, b, hovered) -> paintTab(g, b, hovered, tabLabel(t), screen.tab() == t), () -> screen.openTab(t))
                    .tooltip(() -> tr("tab." + t.name().toLowerCase(Locale.ROOT) + ".tooltip")));
        }
    }

    private Component tabLabel(Tab t) {
        return t == Tab.TAGS && screen.isChemical() ? tr("tab.add") : tr("tab." + t.name().toLowerCase(Locale.ROOT));
    }

    private Component backLabel() {
        return screen.view().isCard() ? tr("close") : tr("back");
    }

    /** Posições e visibilidade do alto e das abas (só as que valem para o tipo). */
    void refresh(int x, int y, int w) {
        Component back = backLabel();
        backButton.setWidth(font().width(back) + 12);
        backButton.setPosition(x + w - X0 - ThemeToggle.SIZE - 4 - backButton.getWidth(), y + HEAD_Y);
        backButton.setMessage(back);
        whiteButton.setPosition(x + X0, y + ROW2_Y);
        blackButton.setPosition(x + X0 + whiteButton.getWidth() + 2, y + ROW2_Y);

        // abas, só as que valem para o tipo
        List<Tab> tabs = new ArrayList<>();
        for (Tab t : Tab.values()) {
            tabButtons[t.ordinal()].visible = screen.tabAvailable(t);
            if (screen.tabAvailable(t)) {
                tabs.add(t);
            }
        }
        int tx = x + frame.panelX();
        for (int i = 0; i < tabs.size(); i++) {
            int right = x + frame.panelX() + PANEL_W * (i + 1) / tabs.size();
            FlatButton b = tabButtons[tabs.get(i).ordinal()];
            b.setPosition(tx, y + TABS_Y);
            b.setWidth(right - tx + (i + 1 < tabs.size() ? 1 : 0));
            tx = right;
        }
    }

    /** O título, o contexto até o Voltar e o botão do tema. */
    void render(GuiGraphics g, int mouseX, int mouseY) {
        Font font = font();
        int x = frame.left();
        int y = frame.top();
        GuiPaint.text(g, font, tr("title"), x + X0, y + HEAD_Y + 3, GuiPaint.FG);
        int titleRight = x + X0 + font.width(tr("title")) + 8;
        int contextRight = backButton.getX() - 6;
        Component context = text.context();
        int contextW = Math.max(0, Math.min(font.width(context), contextRight - titleRight));
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, context, contextW), contextRight - contextW, y + HEAD_Y + 3,
                GuiPaint.MUTED);
        ThemeToggle.render(g, screen.themeX(), screen.themeY(), mouseX, mouseY);
    }

    private void back() {
        if (screen.view().isCard()) {
            screen.onClose();
        } else if (!actions.preview()) {
            actions.send(Op.BACK, 0, 0, "");
        }
    }

    private void setListMode(Filter.ListMode mode) {
        if (filter().listMode() != mode) {
            actions.send(Op.SET_LIST_MODE, 0, mode == Filter.ListMode.WHITELIST ? 0 : 1, "");
        }
    }

}
