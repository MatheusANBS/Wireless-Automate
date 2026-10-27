package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.router.NetworkSearch;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A lista das redes da aba Dado, com a busca da lista de redes do painel do roteador ({@link NetworkSearch}: sem
 * acento nem maiúscula, por trecho): o campo no topo, o trecho achado em coral, o rodapé "3 de 12" (ou "12 redes" com a
 * lista rolando), Enter escolhe a primeira. A rede marcada é a do dado da peça ({@code selected}); o clique chama
 * {@code choose}. As redes que a busca deixa, os trechos e os textos são refeitos só quando o texto muda.
 */
final class TvNetworkList extends TvControl {
    static final int ROWS = 5;
    static final int ROW_H = 11;
    private static final int SEARCH_H = TvTextField.H;
    private static final int LIST_Y = SEARCH_H + 2;
    private static final int LIST_H = ROWS * ROW_H + 2;
    static final int HEIGHT = LIST_Y + LIST_H + 10;
    private static final int MAX_QUERY = 24;
    private static final String ROUTER = "gui.wirelessautomate.router.network.";

    private final Font font;
    private final List<TvMenu.NetworkChoice> networks;
    private final Supplier<UUID> selected;
    private final Consumer<UUID> choose;
    private final TvTextField search;
    private final GuiText.Fitted[] names;
    private final GuiText.Fitted empty;
    private String query = "";
    private int scroll;
    private final int[] shown;
    private int shownCount;
    private final Component[][] parts;
    private final int[] preWidth;
    private final int[] midWidth;
    private @Nullable GuiText.Fitted none;
    private @Nullable GuiText.Fitted footer;
    private @Nullable UUID current;

    TvNetworkList(Font font, String id, int x, int y, List<TvMenu.NetworkChoice> networks, Supplier<UUID> selected,
            Consumer<UUID> choose) {
        super(id, x, y, TvForm.W, HEIGHT);
        this.font = font;
        this.networks = networks;
        this.selected = selected;
        this.choose = choose;
        this.search = new TvTextField(font, id + ".search", x, y, TvForm.W, MAX_QUERY, null,
                Component.translatable(ROUTER + "search"), () -> query, this::setQuery);
        names = new GuiText.Fitted[networks.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = GuiText.fit(font, Component.literal(networks.get(i).name()), TvForm.W - 14 - 6);
        }
        empty = GuiText.fit(font, Component.translatable("gui.wirelessautomate.tv.networks.empty"), TvForm.W - 8);
        shown = new int[networks.size()];
        parts = new Component[networks.size()][];
        preWidth = new int[networks.size()];
        midWidth = new int[networks.size()];
        filter();
        current = selected.get();
        int at = indexOf(current);
        scroll = Math.max(0, Math.min(maxScroll(), at - ROWS / 2));
    }

    private int indexOf(@Nullable UUID id) {
        for (int i = 0; i < networks.size(); i++) {
            if (networks.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private int maxScroll() {
        return Math.max(0, shownCount - ROWS);
    }

    /** Refaz as redes à vista, os trechos em destaque e os textos do rodapé: só quando o texto da busca muda. */
    private void filter() {
        String folded = NetworkSearch.fold(query);
        shownCount = 0;
        for (int i = 0; i < networks.size(); i++) {
            String name = networks.get(i).name();
            int at = NetworkSearch.find(name, folded);
            if (at < 0) {
                continue;
            }
            int row = shownCount++;
            shown[row] = i;
            if (folded.isEmpty() || names[i].cut()) {
                parts[row] = null;
                continue;
            }
            int end = at + folded.length();
            parts[row] = new Component[] {Component.literal(name.substring(0, at)),
                    Component.literal(name.substring(at, end)), Component.literal(name.substring(end))};
            preWidth[row] = font.width(name.substring(0, at));
            midWidth[row] = font.width(name.substring(at, end));
        }
        scroll = 0;
        none = query.isEmpty() || shownCount > 0 ? null
                : GuiText.fit(font, Component.translatable(ROUTER + "search.none", query), TvForm.W - 8);
        Component foot = !query.isEmpty() ? shownCount == 0 ? null
                : Component.translatable(ROUTER + "count", shownCount, networks.size())
                : networks.size() > ROWS ? Component.translatable(ROUTER + "total", networks.size()) : null;
        footer = foot == null ? null : GuiText.fit(font, foot, TvForm.W);
    }

    private void setQuery(String text) {
        query = text;
        filter();
    }

    @Override
    void place(int originX, int originY) {
        super.place(originX, originY);
        search.place(originX, originY);
    }

    @Override
    void refresh() {
        current = selected.get();
        search.refresh();
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        search.render(g, mouseX, mouseY);
        int listY = ay + LIST_Y;
        GuiPaint.inset(g, ax, listY, w, LIST_H);
        if (networks.isEmpty()) {
            GuiText.draw(g, font, empty, ax + 4, listY + 4, GuiPaint.MUTED);
            return;
        }
        if (none != null) {
            GuiText.draw(g, font, none, ax + 4, listY + 4, GuiPaint.MUTED);
        }
        int end = Math.min(shownCount, scroll + ROWS);
        for (int row = scroll; row < end; row++) {
            renderRow(g, listY + 1 + (row - scroll) * ROW_H, row, mouseX, mouseY);
        }
        if (maxScroll() > 0) {
            int track = LIST_H - 2;
            int thumb = Math.max(6, track * ROWS / shownCount);
            int thumbY = listY + 1 + (track - thumb) * scroll / maxScroll();
            g.fill(ax + w - 3, thumbY, ax + w - 1, thumbY + thumb, GuiPaint.BUTTON_BORDER);
        }
        if (footer != null) {
            GuiText.draw(g, font, footer, ax, listY + LIST_H + 1, GuiPaint.MUTED);
        }
    }

    private void renderRow(GuiGraphics g, int rowY, int row, int mouseX, int mouseY) {
        int i = shown[row];
        TvMenu.NetworkChoice choice = networks.get(i);
        if (choice.id().equals(current)) {
            g.fill(ax + 1, rowY, ax + w - 1, rowY + ROW_H, GuiPaint.ROW_SELECTED);
        } else if (mouseX >= ax && mouseX < ax + w && mouseY >= rowY && mouseY < rowY + ROW_H) {
            g.fill(ax + 1, rowY, ax + w - 1, rowY + ROW_H, GuiPaint.ROW_HOVER);
        }
        GuiPaint.dot(g, ax + 4, rowY + 3, 0xFF000000 | choice.color());
        int tx = ax + 14;
        Component[] part = parts[row];
        if (part == null) {
            GuiText.draw(g, font, names[i], tx, rowY + 2, GuiPaint.FG);
        } else {
            GuiPaint.text(g, font, part[0], tx, rowY + 2, GuiPaint.FG);
            GuiPaint.text(g, font, part[1], tx + preWidth[row], rowY + 2, GuiPaint.ACCENT);
            GuiPaint.text(g, font, part[2], tx + preWidth[row] + midWidth[row], rowY + 2, GuiPaint.FG);
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (search.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int listY = ay + LIST_Y;
        if (mouseX < ax || mouseX >= ax + w || mouseY < listY || mouseY >= listY + LIST_H) {
            return false;
        }
        int row = scroll + (int) (mouseY - listY - 1) / ROW_H;
        if (row >= scroll && row < Math.min(shownCount, scroll + ROWS)) {
            choose.accept(networks.get(shown[row]).id());
        }
        return true;
    }

    @Override
    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listY = ay + LIST_Y;
        if (mouseX < ax || mouseX >= ax + w || mouseY < listY || mouseY >= listY + LIST_H || maxScroll() == 0) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    @Override
    boolean wantsKeys() {
        return search.wantsKeys();
    }

    @Override
    void blur() {
        search.blur();
    }

    /** Enter escolhe a primeira rede da busca; o resto é do campo. */
    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.wantsKeys() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && shownCount > 0) {
            choose.accept(networks.get(shown[0]).id());
        }
        return search.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    boolean charTyped(char c, int modifiers) {
        return search.charTyped(c, modifiers);
    }

    /** O centro da linha de uma rede, se a busca a deixa e ela está à vista (gancho do e2e). */
    int @Nullable [] centerOf(UUID id) {
        for (int row = scroll; row < Math.min(shownCount, scroll + ROWS); row++) {
            if (networks.get(shown[row]).id().equals(id)) {
                return new int[] {ax + w / 2, ay + LIST_Y + 1 + (row - scroll) * ROW_H + ROW_H / 2};
            }
        }
        return null;
    }
}
