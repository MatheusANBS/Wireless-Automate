package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.FIELD_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.LABEL_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.LIST_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.ROW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.SEARCH_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.thumbHeight;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ItemEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.RuleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A lista de entradas à esquerda: a contagem, a busca (cada palavra no nome ou no id), as linhas com
 * ícone, nome, tipo e estoque, a rolagem pela roda e pela barra, e o clique que escolhe a entrada. A
 * entrada escolhida fica na tela, que a divide com as abas.
 */
final class EntryList {
    private final FilterScreen screen;
    private final FilterFrame frame;
    private final FilterText text;
    private final EntryIcons icons;

    /** Índices das entradas mostradas (a busca filtra), refeitos quando a visão ou a busca mudam. */
    private List<Integer> shown = List.of();
    private int shownVersion = -1;
    private String shownSearch = "";
    private int scroll;
    private boolean draggingBar;
    private boolean listPress;
    private EditBox searchBox;
    private String searchDraft = "";

    EntryList(FilterScreen screen, FilterText text, EntryIcons icons) {
        this.screen = screen;
        this.frame = screen.frame();
        this.text = text;
        this.icons = icons;
    }

    void addWidgets() {
        searchBox = screen.box(tr("search"), 64, searchDraft, value -> {
            searchDraft = value;
            scroll = 0;
        });
    }

    EditBox searchBox() {
        return searchBox;
    }

    void refresh(int x, int y) {
        searchBox.setPosition(x + X0 + 4, y + SEARCH_Y + 2);
        searchBox.setWidth(frame.listW() - 8);
    }

    int maxScroll() {
        return Math.max(0, shown.size() - frame.listRows());
    }

    void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** Refaz a lista mostrada se a visão ou a busca mudaram. */
    void rebuildShown(boolean force) {
        String search = searchBox.getValue().strip().toLowerCase(Locale.ROOT);
        if (!force && shownVersion == screen.getMenu().version() && search.equals(shownSearch)) {
            return;
        }
        shownVersion = screen.getMenu().version();
        shownSearch = search;
        List<FilterEntry> entries = screen.filter().entries();
        List<Integer> list = new ArrayList<>(entries.size() + 1);
        if (screen.view().isStorage()) {
            list.add(EntrySelection.GLOBAL);
        }
        for (int i = 0; i < entries.size(); i++) {
            if (search.isEmpty() || matchesSearch(entries.get(i), search)) {
                list.add(i);
            }
        }
        shown = list;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** Busca na lista: cada palavra no nome ou no id da entrada ({@code #tag}, {@code @mod} também). */
    private boolean matchesSearch(FilterEntry entry, String search) {
        String haystack = (text.entryName(entry).getString() + " " + FilterText.entryId(entry)).toLowerCase(Locale.ROOT);
        for (String word : search.split("\\s+")) {
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Rola até a entrada {@code index} (já escolhida), limpando a busca se ela a esconde. */
    void scrollTo(int index) {
        int row = shown.indexOf(index);
        if (row < 0 && !searchBox.getValue().isEmpty()) {
            searchBox.setValue("");
            rebuildShown(true);
            row = shown.indexOf(index);
        }
        int rows = frame.listRows();
        if (row >= 0 && row < scroll) {
            scroll = row;
        } else if (row >= scroll + rows) {
            scroll = Math.min(maxScroll(), row - rows + 1);
        }
    }

    /**
     * Índice (na lista do filtro) da entrada na linha sob o mouse; {@link EntrySelection#GLOBAL} na linha fixa;
     * −2 numa linha vazia; −1 fora.
     */
    int entryAt(double mouseX, double mouseY) {
        double rx = mouseX - (frame.left() + X0 + 1);
        double ry = mouseY - (frame.top() + LIST_Y);
        if (rx < 0 || ry < 0 || rx >= frame.listW() - 6 || ry >= frame.listRows() * ROW) {
            return -1;
        }
        int row = scroll + (int) ry / ROW;
        return row < shown.size() ? shown.get(row) : -2;
    }

    private boolean overList(double mouseX, double mouseY) {
        int left = frame.left();
        int top = frame.top();
        return mouseX >= left + X0 && mouseX < left + X0 + frame.listW()
                && mouseY >= top + LIST_Y - 1 && mouseY < top + LIST_Y + frame.listRows() * ROW + 1;
    }

    private boolean overBar(double mouseX, double mouseY) {
        int bx = frame.left() + X0 + frame.listW() - 5;
        int top = frame.top();
        return mouseX >= bx - 1 && mouseX < bx + 5 && mouseY >= top + LIST_Y && mouseY < top + LIST_Y + frame.listRows() * ROW;
    }

    private void dragBar(double mouseY) {
        int max = maxScroll();
        int h = frame.listRows() * ROW;
        int thumb = thumbHeight(h, frame.listRows(), shown.size());
        double t = (mouseY - (frame.top() + LIST_Y) - thumb / 2.0) / (h - thumb);
        scroll = (int) Math.round(Math.max(0, Math.min(1, t)) * max);
    }

    /** A barra (botão esquerdo) ou uma linha: escolhe a entrada, ou tira a escolha clicando nela de novo. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overBar(mouseX, mouseY) && maxScroll() > 0) {
            draggingBar = true;
            dragBar(mouseY);
            return true;
        }
        int index = entryAt(mouseX, mouseY);
        if (index != -1) {
            listPress = true;
            if (screen.getFocused() == searchBox) {
                screen.unfocus();
            }
            EntrySelection selection = screen.selection();
            if (index == EntrySelection.GLOBAL) {
                selection.select(selection.global() ? -1 : EntrySelection.GLOBAL);
            } else {
                selection.select(index >= 0 && index != selection.selectedIndex() ? index : -1);
            }
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mouseY) {
        if (draggingBar) {
            dragBar(mouseY);
            return true;
        }
        return false;
    }

    /** O botão do mouse estava preso na barra ou numa linha. */
    boolean pressed() {
        return draggingBar || listPress;
    }

    void release() {
        draggingBar = false;
        listPress = false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, int step) {
        if (overList(mouseX, mouseY)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + step));
            return true;
        }
        return false;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int x = frame.left();
        int y = frame.top();
        int lw = frame.listW();
        List<FilterEntry> entries = screen.filter().entries();
        Component label = entries.size() >= Filter.MAX_ENTRIES ? tr("entries.full", entries.size())
                : tr("entries", entries.size());
        GuiPaint.text(g, font, label, x + X0, y + LABEL_Y, GuiPaint.MUTED);
        GuiPaint.box(g, x + X0, y + SEARCH_Y, lw, FIELD_H, GuiPaint.INSET, searchBox.isFocused() ? GuiPaint.ACCENT : GuiPaint.LINE);

        int rows = frame.listRows();
        int top = y + LIST_Y;
        GuiPaint.inset(g, x + X0, top - 1, lw, rows * ROW + 2);
        if (shown.isEmpty()) {
            Component empty = entries.isEmpty() ? tr("empty") : tr("search.none");
            List<FormattedCharSequence> lines = font.split(empty, lw - 16);
            int ty = top + (rows * ROW - lines.size() * 10) / 2;
            for (FormattedCharSequence line : lines) {
                GuiPaint.text(g, font, line, x + X0 + (lw - font.width(line)) / 2, ty, GuiPaint.MUTED);
                ty += 10;
            }
        }
        renderRows(g, entries, entryAt(mouseX, mouseY));
        // barra de rolagem
        int max = maxScroll();
        if (max > 0) {
            int bx = x + X0 + lw - 5;
            int hh = rows * ROW;
            g.fill(bx, top, bx + 4, top + hh, GuiPaint.PANEL);
            int thumb = thumbHeight(hh, rows, shown.size());
            int ty = top + Math.round((hh - thumb) * (scroll / (float) max));
            boolean active = draggingBar || overBar(mouseX, mouseY);
            g.fill(bx, ty, bx + 4, ty + thumb, active ? GuiPaint.ACCENT : GuiPaint.BEVEL_DARK);
        }
    }

    private void renderRows(GuiGraphics g, List<FilterEntry> entries, int hovered) {
        Font font = Minecraft.getInstance().font;
        int x = frame.left();
        int lw = frame.listW();
        int rows = frame.listRows();
        int top = frame.top() + LIST_Y;
        int textW = lw - 8 - 22;
        boolean storage = screen.view().isStorage();
        for (int r = 0; r < rows && scroll + r < shown.size(); r++) {
            int index = shown.get(scroll + r);
            int ry = top + r * ROW;
            int rx = x + X0 + 1;
            if (index == EntrySelection.GLOBAL) {
                renderGlobalRow(g, rx, ry, hovered == EntrySelection.GLOBAL);
                continue;
            }
            FilterEntry entry = entries.get(index);
            if (index == screen.selection().selectedIndex()) {
                g.fill(rx, ry, x + X0 + lw - 6, ry + ROW, GuiPaint.ROW_SELECTED);
                g.fill(rx, ry, rx + 1, ry + ROW, GuiPaint.ACCENT);
            } else if (index == hovered) {
                g.fill(rx, ry, x + X0 + lw - 6, ry + ROW, GuiPaint.ROW_HOVER);
            }
            icons.renderEntryIcon(g, entry, rx + 2, ry + 1);
            // no armazenamento a entrada é um teto (e 0 é a exceção sem limite); no roteador, o estoque
            String stock = storage ? limitBadge(entry.stock()) : entry.stock() > 0 ? "≥ " + text.stockShort(entry.stock()) : "";
            int stockW = stock.isEmpty() ? 0 : font.width(stock) + 4;
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, text.entryName(entry), textW - stockW), rx + 21, ry + 1,
                    entry instanceof RuleEntry ? GuiPaint.RULE_TEXT : GuiPaint.FG);
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, text.entryKind(entry), textW), rx + 21, ry + 10,
                    entry instanceof ItemEntry ? GuiPaint.DISABLED : GuiPaint.MUTED);
            if (!stock.isEmpty()) {
                GuiPaint.textRight(g, font, Component.literal(stock), x + X0 + lw - 8, ry + 1,
                        storage && entry.stock() == 0 ? GuiPaint.MUTED : GuiPaint.ACCENT_DEEP);
            }
        }
    }

    /** O selo do limite no armazenamento: "≤ N", ou "sem limite" com 0. */
    private String limitBadge(long limit) {
        return limit > 0 ? "≤ " + text.stockShort(limit) : tr("global.none").getString();
    }

    /**
     * A linha fixa do limite por tipo, distinta das entradas: fundo de painel, o bloco do armazenamento como ícone,
     * o nome numa linha só, o selo à direita e um traço que a separa das entradas.
     */
    private void renderGlobalRow(GuiGraphics g, int rx, int ry, boolean hovered) {
        Font font = Minecraft.getInstance().font;
        int right = frame.left() + X0 + frame.listW() - 6;
        boolean chosen = screen.selection().global();
        g.fill(rx, ry, right, ry + ROW - 1, chosen ? GuiPaint.ROW_SELECTED : hovered ? GuiPaint.ROW_HOVER : GuiPaint.PANEL);
        if (chosen) {
            g.fill(rx, ry, rx + 1, ry + ROW - 1, GuiPaint.ACCENT);
        }
        g.fill(rx, ry + ROW - 1, right, ry + ROW, GuiPaint.LINE);
        g.renderItem(screen.storageIcon(), rx + 2, ry);
        boolean limited = screen.filter().typeLimit() > 0;
        String badge = limitBadge(screen.filter().typeLimit());
        int badgeW = font.width(badge);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, tr("global.name"), right - rx - 24 - badgeW - 6), rx + 21, ry + 5,
                GuiPaint.FG);
        GuiPaint.textRight(g, font, Component.literal(badge), right - 2, ry + 5,
                limited ? GuiPaint.ACCENT_DEEP : GuiPaint.MUTED);
    }

    /** Onde soltar um ingrediente para acrescentá-lo: a lista de entradas, em coordenadas da tela. */
    Rect2i ghostArea() {
        return new Rect2i(frame.left() + X0, frame.top() + LIST_Y - 1, frame.listW(), frame.listRows() * ROW + 2);
    }

    /** Centro da linha da entrada {@code index} visível, para simular o mouse em cima na captura. */
    int[] entryCenter(int index) {
        int row = shown.indexOf(index) - scroll;
        return new int[] {frame.left() + X0 + 40, frame.top() + LIST_Y + row * ROW + ROW / 2};
    }
}
