package io.github.matheusanbs.wirelessautomate.client.tv;

import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.*;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.router.NetworkSearch;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A lista das redes da aba Conteúdo, fixa na aba, com a busca da lista de redes do painel do roteador
 * ({@link NetworkSearch}: sem acento nem maiúscula, por trecho): o campo no topo, sempre com o teclado, o trecho achado
 * em coral, o rodapé "3 de 12" (ou "12 redes" com a lista rolando), Enter escolhe a primeira e Esc limpa a busca (vazia,
 * a tela fecha). As redes que a busca deixa, os trechos e os textos são refeitos só quando o texto muda.
 */
final class TvNetworkList {
    private static final int VISIBLE_ROWS = LIST.h() / ROW_H;
    private static final int MAX_QUERY = 24;
    private static final String ROUTER = "gui.wirelessautomate.router.network.";

    private final List<TvMenu.NetworkChoice> networks;
    private @Nullable UUID selected;
    private int scroll;
    private String query = "";

    private Font font;
    private GuiText.Fitted[] names = new GuiText.Fitted[0];
    private GuiText.Fitted placeholder;
    private GuiText.Fitted empty;
    /** As redes à vista pela busca (índices na lista do menu) e, de cada uma, o trecho achado. */
    private int[] shown = new int[0];
    private int shownCount;
    private Component[][] parts = new Component[0][];
    private int[] preWidth = new int[0];
    private int[] midWidth = new int[0];
    private @Nullable GuiText.Fitted queryText;
    private int caretX;
    private @Nullable GuiText.Fitted none;
    private @Nullable GuiText.Fitted footer;

    TvNetworkList(TvMenu menu) {
        this.networks = menu.networks();
        UUID current = menu.currentNetwork();
        selected = current != null && indexOf(current) >= 0 ? current
                : networks.isEmpty() ? null : networks.get(0).id();
    }

    void layout(Font font) {
        this.font = font;
        names = new GuiText.Fitted[networks.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = GuiText.fit(font, Component.literal(networks.get(i).name()), nameWidth());
        }
        placeholder = GuiText.fit(font, Component.translatable(ROUTER + "search"), SEARCH.w() - 8);
        empty = GuiText.fit(font, TvScreenText.key("networks.empty"), LIST.w() - 8);
        shown = new int[networks.size()];
        parts = new Component[networks.size()][];
        preWidth = new int[networks.size()];
        midWidth = new int[networks.size()];
        filter();
        scroll = Math.max(0, Math.min(maxScroll(), indexOf(selected) - VISIBLE_ROWS / 2));
    }

    private static int nameWidth() {
        return LIST.w() - 30;
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
        return Math.max(0, shownCount - VISIBLE_ROWS);
    }

    /** Refaz as redes à vista, os trechos em destaque e os textos da busca: só quando o texto muda. */
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
        queryText = query.isEmpty() ? null : GuiText.fit(font, Component.literal(query), SEARCH.w() - 10);
        caretX = queryText == null ? 0 : Math.min(font.width(query), SEARCH.w() - 10);
        none = query.isEmpty() || shownCount > 0 ? null
                : GuiText.fit(font, Component.translatable(ROUTER + "search.none", query), LIST.w() - 8);
        Component foot = !query.isEmpty() ? shownCount == 0 ? null
                : Component.translatable(ROUTER + "count", shownCount, networks.size())
                : networks.size() > VISIBLE_ROWS ? Component.translatable(ROUTER + "total", networks.size()) : null;
        footer = foot == null ? null : GuiText.fit(font, foot, FOOTER.w());
    }

    private void setQuery(String text) {
        query = text;
        filter();
    }

    void render(GuiGraphics g, int left, int top, int mouseX, int mouseY, @Nullable UUID current) {
        renderSearch(g, left, top);
        int x = left + LIST.x();
        int y = top + LIST.y();
        GuiPaint.inset(g, x, y, LIST.w(), LIST.h());
        if (networks.isEmpty()) {
            GuiText.draw(g, font, empty, x + 4, y + 4, GuiPaint.MUTED);
            return;
        }
        if (none != null) {
            GuiText.draw(g, font, none, x + 4, y + 4, GuiPaint.MUTED);
        }
        int end = Math.min(shownCount, scroll + VISIBLE_ROWS);
        for (int row = scroll; row < end; row++) {
            renderRow(g, x, y + (row - scroll) * ROW_H + 1, row, mouseX, mouseY, current);
        }
        if (maxScroll() > 0) {
            int track = LIST.h() - 2;
            int thumb = Math.max(8, track * VISIBLE_ROWS / shownCount);
            int thumbY = y + 1 + (track - thumb) * scroll / maxScroll();
            g.fill(x + LIST.w() - 3, thumbY, x + LIST.w() - 1, thumbY + thumb, GuiPaint.BUTTON_BORDER);
        }
        if (footer != null) {
            GuiText.draw(g, font, footer, left + FOOTER.x(), top + FOOTER.y(), GuiPaint.MUTED);
        }
    }

    private void renderSearch(GuiGraphics g, int left, int top) {
        int x = left + SEARCH.x();
        int y = top + SEARCH.y();
        GuiPaint.box(g, x, y, SEARCH.w(), SEARCH.h(), GuiPaint.INSET, GuiPaint.BUTTON_BORDER);
        if (queryText == null) {
            GuiText.draw(g, font, placeholder, x + 4, y + 3, GuiPaint.MUTED);
        } else {
            GuiText.draw(g, font, queryText, x + 4, y + 3, GuiPaint.FG);
        }
        if (System.currentTimeMillis() / 500 % 2 == 0) {
            g.fill(x + 4 + caretX, y + 2, x + 5 + caretX, y + SEARCH.h() - 2, GuiPaint.FG);
        }
    }

    private void renderRow(GuiGraphics g, int x, int rowY, int row, int mouseX, int mouseY, @Nullable UUID current) {
        int i = shown[row];
        TvMenu.NetworkChoice choice = networks.get(i);
        if (choice.id().equals(selected)) {
            g.fill(x + 1, rowY, x + LIST.w() - 1, rowY + ROW_H, GuiPaint.ROW_SELECTED);
        } else if (mouseX >= x && mouseX < x + LIST.w() && mouseY >= rowY && mouseY < rowY + ROW_H) {
            g.fill(x + 1, rowY, x + LIST.w() - 1, rowY + ROW_H, GuiPaint.ROW_HOVER);
        }
        GuiPaint.dot(g, x + 5, rowY + 4, 0xFF000000 | choice.color());
        int tx = x + 14;
        Component[] part = parts[row];
        if (part == null) {
            GuiText.draw(g, font, names[i], tx, rowY + 3, GuiPaint.FG);
        } else {
            GuiPaint.text(g, font, part[0], tx, rowY + 3, GuiPaint.FG);
            GuiPaint.text(g, font, part[1], tx + preWidth[row], rowY + 3, GuiPaint.ACCENT);
            GuiPaint.text(g, font, part[2], tx + preWidth[row] + midWidth[row], rowY + 3, GuiPaint.FG);
        }
        if (choice.id().equals(current)) {
            check(g, x + LIST.w() - 11, rowY + 5, GuiPaint.OK);
        }
    }

    /** O ✓ da rede que a Tela mostra, em 5 × 4 px, como o da lista de redes do roteador. */
    private static void check(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y + 2, x + 1, y + 3, color);
        g.fill(x + 1, y + 3, x + 2, y + 4, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
        g.fill(x + 3, y + 1, x + 4, y + 2, color);
        g.fill(x + 4, y, x + 5, y + 1, color);
    }

    boolean mouseClicked(int left, int top, double mouseX, double mouseY) {
        if (SEARCH.contains(left, top, mouseX, mouseY)) {
            return true;
        }
        if (!LIST.contains(left, top, mouseX, mouseY)) {
            return false;
        }
        int row = scroll + (int) (mouseY - top - LIST.y() - 1) / ROW_H;
        if (row >= scroll && row < shownCount) {
            selected = networks.get(shown[row]).id();
        }
        return true;
    }

    boolean mouseScrolled(int left, int top, double mouseX, double mouseY, double delta) {
        if (!LIST.contains(left, top, mouseX, mouseY)) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    /** Uma letra vai para a busca. */
    boolean charTyped(char c) {
        if (c < 32 || c == 127) {
            return false;
        }
        if (query.length() < MAX_QUERY) {
            setQuery(query + c);
        }
        return true;
    }

    /**
     * Apagar tira a última letra, Enter escolhe a primeira rede da busca e Esc limpa a busca; com ela vazia, o Esc fica
     * com a tela, que fecha. As outras teclas ficam com a busca (a letra do inventário não fecha a tela); só as F ficam
     * livres.
     */
    boolean keyPressed(int keyCode) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (query.isEmpty()) {
                    return false;
                }
                setQuery("");
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!query.isEmpty()) {
                    setQuery(query.substring(0, query.length() - 1));
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (shownCount > 0) {
                    selected = networks.get(shown[0]).id();
                }
            }
            default -> {
                return keyCode < GLFW.GLFW_KEY_F1;
            }
        }
        return true;
    }

    @Nullable UUID selected() {
        return selected;
    }

    String query() {
        return query;
    }

    int shownCount() {
        return shownCount;
    }

    /** O centro da linha de uma rede, se a busca a deixa e ela está à vista (gancho do e2e). */
    int @Nullable [] center(int left, int top, UUID id) {
        for (int row = scroll; row < Math.min(shownCount, scroll + VISIBLE_ROWS); row++) {
            if (networks.get(shown[row]).id().equals(id)) {
                return new int[] {left + LIST.x() + LIST.w() / 2, top + LIST.y() + 1 + (row - scroll) * ROW_H + ROW_H / 2};
            }
        }
        return null;
    }
}
