package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.router.PanelLayout.Kind;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * As listas que abrem no painel do nó, uma por vez, numa {@link DropdownList}: a da linha Rede (com
 * título, as redes do seletor e "Sem rede"), a do redstone e a da linha Editando ({@link TargetLine}). A
 * escolha volta ao painel pela {@link Choice}, que manda o pedido.
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
    }

    /** Abre a lista {@code popup} rolada até {@code scroll}. */
    void open(Popup popup, int scroll) {
        this.popup = popup;
        list.setScroll(scroll);
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
        boolean titled = popup == Popup.NETWORK;
        if (anchor == null) {
            list.unplace(count, titled);
            return;
        }
        list.place(anchor.x(), anchor.y(), anchor.h(), wantedWidth(font, anchor), layout.bodyX, layout.bodyW, count,
                titled);
    }

    /** Linhas da lista aberta (sem o título). */
    private int count() {
        return switch (popup) {
            case NETWORK -> model.snapshot().networks().size() + 1;
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
                popup = Popup.NONE;
            }
            return;
        }
        switch (popup) {
            case NETWORK -> {
                List<NetworkEntry> networks = model.snapshot().networks();
                // uma rede que o jogador não pode usar fica na lista só para mostrar o nome: sem clique
                if (row >= networks.size() || networks.get(row).usable()) {
                    choice.network(row < networks.size() ? Optional.of(networks.get(row).id()) : Optional.empty());
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

    // ------------------------------------------------------------------ desenho

    /** A lista aberta, por cima de tudo. */
    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (popup == Popup.NONE || !list.placed()) {
            return;
        }
        Component title = popup == Popup.NETWORK ? networkListTitle() : null;
        list.render(g, font, title, mouseX, mouseY, switch (popup) {
            case NETWORK -> this::renderNetworkRow;
            case REDSTONE -> this::renderRedstoneRow;
            case TARGET -> targetLine::renderRow;
            case NONE -> (gg, f, x, ry, w, index, hovered) -> {
            };
        });
    }

    private void renderNetworkRow(GuiGraphics g, Font font, int x, int ry, int w, int index, boolean hovered) {
        List<NetworkEntry> networks = model.snapshot().networks();
        Optional<UUID> current = model.snapshot().network(model.type(), model.face());
        boolean none = index >= networks.size();
        NetworkEntry entry = none ? null : networks.get(index);
        boolean selected = none ? current.isEmpty() : current.isPresent() && current.get().equals(entry.id());
        boolean usable = none || entry.usable();
        if (hovered && usable) {
            g.fill(x + 1, ry, x + w - 1, ry + DropdownList.ROW, GuiPaint.ROW_SELECTED);
        }
        if (selected) {
            g.fill(x + 1, ry, x + 2, ry + DropdownList.ROW, GuiPaint.ACCENT);
        }
        int color = none ? GuiPaint.MUTED
                : usable ? 0xFF000000 | entry.color() : GuiPaint.mix(0xFF000000 | entry.color(), GuiPaint.PANEL, 0.6f);
        if (none) {
            GuiPaint.outline(g, x + 6, ry + 3, 5, 5, color);
        } else {
            GuiPaint.eye(g, x + 6, ry + 3, color);
        }
        Component label = none ? PanelModel.tr("network.none") : Component.literal(entry.name());
        GuiText.draw(g, font, label, x + 15, ry + 2, w - 20, none ? GuiPaint.MUTED : usable ? GuiPaint.FG : GuiPaint.DISABLED);
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
