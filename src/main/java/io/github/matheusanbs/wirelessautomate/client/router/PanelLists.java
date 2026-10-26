package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Kind;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * As listas que abrem no painel do nó, uma por vez, numa {@link DropdownList}: a da linha Rede (título, o campo de
 * busca, "Sem rede" fixo no topo e as redes do seletor, filtradas pelo texto digitado), a do redstone e a da
 * linha Editando ({@link TargetLine}). A escolha volta ao painel pela {@link Choice}, que manda o pedido.
 */
final class PanelLists {
    enum Popup { NONE, NETWORK, REDSTONE, TARGET }

    /** O que o jogador escolheu numa lista. */
    interface Choice {
        void network(Optional<UUID> network);

        void redstone(RedstoneMode mode);

        void target(EditTarget target);
    }

    private final PanelModel model;
    private final PanelLayout layout;
    private final TargetLine targetLine;
    private final DropdownList list = new DropdownList();
    private Popup popup = Popup.NONE;
    /** O texto da busca da lista de redes e o mesmo dobrado ({@link NetworkSearch#fold}); vazio ao abrir. */
    private String query = "";
    private String folded = "";
    /** As redes que a busca deixa, com onde ela acerta em cada nome; refeitas só quando o snapshot ou o texto mudam. */
    private List<NetworkEntry> shown = List.of();
    private int[] hits = new int[0];
    private @Nullable List<NetworkEntry> shownFor;
    private String shownQuery = "";
    private static final int MAX_QUERY = 24;
    private static final int TITLE_H = DropdownList.ROW;
    private static final int SEARCH_H = 13;
    /** Título, busca e a linha fixa "Sem rede" com a divisória: a faixa de cima da lista de redes. */
    private static final int NETWORK_TOP = TITLE_H + 2 + SEARCH_H + 2 + DropdownList.ROW + 1;

    PanelLists(PanelModel model, PanelLayout layout, TargetLine targetLine) {
        this.model = model;
        this.layout = layout;
        this.targetLine = targetLine;
    }

    // ------------------------------------------------------------------ estado

    Popup popup() {
        return popup;
    }

    /** Alguma lista está aberta. */
    boolean open() {
        return popup != Popup.NONE;
    }

    void close() {
        popup = Popup.NONE;
        setQuery("");
    }

    /** Abre a lista {@code popup} rolada até {@code scroll}; a busca das redes começa vazia. */
    void open(Popup popup, int scroll) {
        this.popup = popup;
        setQuery("");
        list.setScroll(scroll);
    }

    private void setQuery(String text) {
        query = text;
        folded = NetworkSearch.fold(text);
        list.setScroll(0);
    }

    /** As redes da lista, depois da busca. */
    private List<NetworkEntry> shown() {
        List<NetworkEntry> all = model.snapshot().networks();
        if (all != shownFor || !folded.equals(shownQuery)) {
            List<NetworkEntry> out = new ArrayList<>();
            int[] found = new int[all.size()];
            for (NetworkEntry entry : all) {
                int at = NetworkSearch.find(entry.name(), folded);
                if (at >= 0) {
                    found[out.size()] = at;
                    out.add(entry);
                }
            }
            shown = out;
            hits = found;
            shownFor = all;
            shownQuery = folded;
        }
        return shown;
    }

    /** O rodapé da lista de redes ("3 de 12", "12 redes"): só filtrando ou com a lista rolando. */
    private @Nullable Component networkFooter() {
        int total = model.snapshot().networks().size();
        if (!folded.isEmpty()) {
            return shown().isEmpty() ? null : PanelModel.tr("network.count", shown().size(), total);
        }
        return total > DropdownList.MAX_ROWS ? PanelModel.tr("network.total", total) : null;
    }

    private int bottomH() {
        return popup == Popup.NETWORK && networkFooter() != null ? DropdownList.ROW : 0;
    }

    private int topH() {
        return popup == Popup.NETWORK ? NETWORK_TOP : 0;
    }

    /** A rolagem que deixa o alvo atual à vista ao abrir a lista da linha Editando. */
    int targetScroll() {
        List<TargetLine.TargetRow> rows = targetLine.rows();
        return Math.max(0, Math.min(rows.indexOf(targetLine.currentRow()), rows.size() - DropdownList.MAX_ROWS));
    }

    // ------------------------------------------------------------------ geometria

    /** Põe a lista aberta sob o controle dela (depois do {@link PanelLayout#arrange}). */
    void place(Font font) {
        Hot anchor = anchor();
        int count = count();
        if (anchor == null) {
            list.unplace(count, topH(), bottomH());
            return;
        }
        list.place(anchor.x(), anchor.y(), anchor.h(), wantedWidth(font, anchor), layout.bodyX, layout.bodyW, count,
                topH(), bottomH());
    }

    /** Linhas da lista aberta (sem o título). */
    private int count() {
        return switch (popup) {
            // sem nenhuma rede, uma linha com o aviso
            case NETWORK -> Math.max(1, shown().size());
            case REDSTONE -> RedstoneMode.values().length;
            case TARGET -> targetLine.rows().size();
            case NONE -> 0;
        };
    }

    private @Nullable Hot anchor() {
        return switch (popup) {
            case NETWORK -> layout.hot(Kind.NETWORK);
            case REDSTONE -> layout.hot(Kind.REDSTONE);
            case TARGET -> layout.hot(Kind.TARGET);
            case NONE -> null;
        };
    }

    private Component networkListTitle() {
        return PanelModel.tr("network.list.title", model.sideName(model.face()), ResourceStyle.name(model.type()));
    }

    private int wantedWidth(Font font, Hot anchor) {
        int width = anchor.w();
        switch (popup) {
            case NETWORK -> {
                for (NetworkEntry entry : model.snapshot().networks()) {
                    width = Math.max(width, font.width(entry.name()) + 24);
                }
                width = Math.max(width, font.width(PanelModel.tr("network.none")) + 24);
                width = Math.max(width, font.width(networkListTitle()) + 12);
                width = Math.max(width, 110);
            }
            case TARGET -> width = targetLine.listWidth(font, width);
            case REDSTONE, NONE -> {
            }
        }
        return width;
    }

    boolean contains(double mx, double my) {
        return popup != Popup.NONE && list.contains(mx, my);
    }

    /** Centro da linha {@code row} da lista {@code which}, se é ela que está aberta e a linha está à vista. */
    int @Nullable [] rowCenter(Popup which, int row) {
        return popup == which ? list.rowCenter(row) : null;
    }

    // ------------------------------------------------------------------ entrada

    /** Um clique com uma lista aberta: escolhe a linha, ou fecha a lista se o clique foi fora dela. */
    void click(double mx, double my, Choice choice) {
        int row = list.rowAt(mx, my);
        if (row < 0) {
            if (!contains(mx, my)) {
                close();
            } else if (popup == Popup.NETWORK) {
                headerClick(mx, my, choice);
            }
            return;
        }
        switch (popup) {
            case NETWORK -> {
                List<NetworkEntry> networks = shown();
                // uma rede que o jogador não pode usar fica na lista só para mostrar o nome: sem clique
                if (row < networks.size() && networks.get(row).usable()) {
                    choice.network(Optional.of(networks.get(row).id()));
                }
            }
            case REDSTONE -> {
                popup = Popup.NONE;
                choice.redstone(RedstoneMode.values()[row]);
            }
            case TARGET -> choice.target(targetLine.rows().get(row).target());
            case NONE -> {
            }
        }
    }

    void scroll(double mx, double my, double scrollY) {
        list.scroll(mx, my, scrollY);
    }

    /** O clique na faixa de cima da lista de redes: "Sem rede" escolhe, o ✕ do campo limpa a busca. */
    private void headerClick(double mx, double my, Choice choice) {
        if (my >= pinY() && my < pinY() + DropdownList.ROW) {
            choice.network(Optional.empty());
        } else if (!query.isEmpty() && my >= searchY() && my < searchY() + SEARCH_H
                && mx >= list.x() + list.width() - 3 - 11 && mx < list.x() + list.width() - 3) {
            setQuery("");
        }
    }

    // ------------------------------------------------------------------ teclado (a busca das redes)

    /** Uma letra digitada com a lista de redes aberta vai para a busca. */
    boolean charTyped(char c) {
        if (popup != Popup.NETWORK || c < 32 || c == 127) {
            return false;
        }
        if (query.length() < MAX_QUERY) {
            setQuery(query + c);
        }
        return true;
    }

    /**
     * Uma tecla com a lista de redes aberta: Apagar tira a última letra, Enter escolhe a primeira rede da busca e Esc
     * limpa a busca (de novo, fecha a lista). As outras teclas ficam com a lista, para a letra da busca não acionar
     * atalhos da tela (como o do inventário); só as F ficam livres.
     */
    boolean keyPressed(int keyCode, Choice choice) {
        if (popup != Popup.NETWORK) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (query.isEmpty()) {
                    close();
                } else {
                    setQuery("");
                }
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!query.isEmpty()) {
                    setQuery(query.substring(0, query.length() - 1));
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                for (NetworkEntry entry : shown()) {
                    if (entry.usable()) {
                        choice.network(Optional.of(entry.id()));
                        break;
                    }
                }
            }
            default -> {
                return keyCode < GLFW.GLFW_KEY_F1;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ geometria da faixa de cima

    private int searchY() {
        return list.y() + 2 + TITLE_H + 2;
    }

    private int pinY() {
        return searchY() + SEARCH_H + 2;
    }

    /** Põe o texto na busca da lista de redes (captura sem teclado). */
    void previewQuery(String text) {
        setQuery(text);
    }

    /** O centro da linha fixa "Sem rede", com a lista de redes aberta (gancho do e2e e das capturas). */
    int @Nullable [] noneCenter() {
        return popup == Popup.NETWORK && list.placed()
                ? new int[] {list.x() + list.width() / 2, pinY() + DropdownList.ROW / 2}
                : null;
    }

    // ------------------------------------------------------------------ desenho

    /** A lista aberta, por cima de tudo. */
    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (popup == Popup.NONE || !list.placed()) {
            return;
        }
        boolean network = popup == Popup.NETWORK;
        DropdownList.BandPainter top = network
                ? (gg, f, x, y, w) -> renderNetworkTop(gg, f, x, y, w, mouseX, mouseY)
                : null;
        list.render(g, font, top, network && networkFooter() != null ? this::renderNetworkFooter : null, mouseX,
                mouseY, switch (popup) {
            case NETWORK -> this::renderNetworkRow;
            case REDSTONE -> this::renderRedstoneRow;
            case TARGET -> targetLine::renderRow;
            case NONE -> (gg, f, x, ry, w, index, hovered) -> {
            };
        });
    }

    /** O título, o campo de busca e a linha fixa "Sem rede" com a divisória. */
    private void renderNetworkTop(GuiGraphics g, Font font, int x, int top, int w, int mouseX, int mouseY) {
        GuiText.draw(g, font, networkListTitle(), x + 5, top + 2, w - 10, GuiPaint.MUTED);
        g.fill(x + 3, top + TITLE_H, x + w - 3, top + TITLE_H + 1, GuiPaint.LINE);
        int sy = top + TITLE_H + 2;
        GuiPaint.box(g, x + 3, sy, w - 6, SEARCH_H, GuiPaint.INSET, GuiPaint.BUTTON_BORDER);
        if (query.isEmpty()) {
            GuiText.draw(g, font, PanelModel.tr("network.search"), x + 7, sy + 3, w - 16, GuiPaint.MUTED);
        } else {
            GuiText.draw(g, font, Component.literal(query), x + 7, sy + 3, w - 24, GuiPaint.FG);
            if (System.currentTimeMillis() / 500 % 2 == 0) {
                int cx = Math.min(x + 7 + font.width(query), x + w - 17);
                g.fill(cx, sy + 2, cx + 1, sy + SEARCH_H - 2, GuiPaint.FG);
            }
            RangeRowButtons.cross(g, x + w - 3 - 9, sy + 4, GuiPaint.MUTED);
        }
        int py = sy + SEARCH_H + 2;
        boolean current = model.snapshot().network(model.type(), model.face()).isEmpty();
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= py && mouseY < py + DropdownList.ROW;
        if (hovered) {
            g.fill(x + 1, py, x + w - 1, py + DropdownList.ROW, GuiPaint.ROW_SELECTED);
        }
        if (current) {
            g.fill(x + 1, py, x + 2, py + DropdownList.ROW, GuiPaint.ACCENT);
            check(g, x + w - 11, py + 4, GuiPaint.ACCENT);
        }
        GuiPaint.outline(g, x + 7, py + 3, 7, 7, GuiPaint.MUTED);
        GuiText.draw(g, font, PanelModel.tr("network.none"), x + 19, py + 2, w - 34, GuiPaint.MUTED);
        g.fill(x + 3, py + DropdownList.ROW, x + w - 3, py + DropdownList.ROW + 1, GuiPaint.LINE);
    }

    /** "3 de 12" ou "12 redes", embaixo das linhas. */
    private void renderNetworkFooter(GuiGraphics g, Font font, int x, int y, int w) {
        Component footer = networkFooter();
        if (footer == null) {
            return;
        }
        g.fill(x + 3, y, x + w - 3, y + 1, GuiPaint.LINE);
        GuiText.draw(g, font, footer, x + 7, y + 2, w - 16, GuiPaint.MUTED);
    }

    /** O ✓ da rede atual, em 5 × 4 px. */
    private static void check(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y + 2, x + 1, y + 3, color);
        g.fill(x + 1, y + 3, x + 2, y + 4, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
        g.fill(x + 3, y + 1, x + 4, y + 2, color);
        g.fill(x + 4, y, x + 5, y + 1, color);
    }

    private void renderNetworkRow(GuiGraphics g, Font font, int x, int ry, int w, int index, boolean hovered) {
        List<NetworkEntry> networks = shown();
        if (networks.isEmpty()) {
            GuiText.draw(g, font, PanelModel.tr("network.search.none", query), x + 7, ry + 2, w - 14, GuiPaint.MUTED);
            return;
        }
        NetworkEntry entry = networks.get(index);
        Optional<UUID> current = model.snapshot().network(model.type(), model.face());
        boolean selected = current.isPresent() && current.get().equals(entry.id());
        boolean usable = entry.usable();
        if (hovered && usable) {
            g.fill(x + 1, ry, x + w - 1, ry + DropdownList.ROW, GuiPaint.ROW_SELECTED);
        } else if (selected) {
            g.fill(x + 1, ry, x + w - 1, ry + DropdownList.ROW, GuiPaint.ROW_HOVER);
        }
        if (selected) {
            g.fill(x + 1, ry, x + 2, ry + DropdownList.ROW, GuiPaint.ACCENT);
            check(g, x + w - 11, ry + 4, GuiPaint.ACCENT);
        }
        int color = 0xFF000000 | entry.color();
        if (!usable) {
            color = GuiPaint.mix(color, GuiPaint.PANEL, 0.6f);
        }
        g.fill(x + 7, ry + 2, x + 15, ry + 10, GuiPaint.mix(color, 0xFF000000, 0.35f));
        g.fill(x + 8, ry + 3, x + 14, ry + 9, color);
        int ink = usable ? GuiPaint.FG : GuiPaint.DISABLED;
        int room = w - 19 - 14;
        String name = entry.name();
        int at = folded.isEmpty() ? -1 : hits[index];
        if (at >= 0 && usable && font.width(name) <= room) {
            int end = at + folded.length();
            int tx = x + 19;
            GuiPaint.text(g, font, Component.literal(name.substring(0, at)), tx, ry + 2, ink);
            tx += font.width(name.substring(0, at));
            GuiPaint.text(g, font, Component.literal(name.substring(at, end)), tx, ry + 2, GuiPaint.ACCENT);
            tx += font.width(name.substring(at, end));
            GuiPaint.text(g, font, Component.literal(name.substring(end)), tx, ry + 2, ink);
        } else {
            GuiText.draw(g, font, Component.literal(name), x + 19, ry + 2, room, ink);
        }
    }

    private void renderRedstoneRow(GuiGraphics g, Font font, int x, int ry, int w, int index, boolean hovered) {
        RedstoneMode mode = RedstoneMode.values()[index];
        if (hovered) {
            g.fill(x + 1, ry, x + w - 1, ry + DropdownList.ROW, GuiPaint.ROW_SELECTED);
        }
        if (mode == model.view().redstone()) {
            g.fill(x + 1, ry, x + 2, ry + DropdownList.ROW, GuiPaint.ACCENT);
        }
        GuiText.draw(g, font, PanelModel.redstoneName(mode), x + 6, ry + 2, w - 10, GuiPaint.FG);
    }
}
