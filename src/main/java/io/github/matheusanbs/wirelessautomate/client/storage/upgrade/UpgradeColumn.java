package io.github.matheusanbs.wirelessautomate.client.storage.upgrade;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.item.ChestUpgradeItem;
import io.github.matheusanbs.wirelessautomate.menu.ChestUpgradeSlots;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestUpgrades;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A coluna dos dois slots de upgrade, colada à esquerda do painel do Baú. Os slots são do menu (pegar e pôr como
 * qualquer slot); a coluna desenha o fundo, os slots, a aba de 6 × 18 à esquerda de cada slot ocupado (clicar abre o
 * painel daquele upgrade) e a borda de destaque no slot do painel aberto. O painel aberto é lembrado só enquanto a
 * tela existe: com um upgrade só, abre o dele; ao tirar o do painel aberto, abre o outro (ou nenhum).
 */
final class UpgradeColumn {
    static final int WIDTH = 30;
    static final int HEIGHT = 48;
    /** Distância do topo do painel do Baú ao topo da coluna. */
    static final int TOP = 20;
    private static final int TAB_X = 2;
    private static final int TAB_W = 6;
    private static final int SLOT_X = 9;
    private static final int PAD_Y = 5;
    private static final int SHADOW = 2;

    private final StorageListMenu<?> menu;
    private int x;
    private int y;
    private ChestUpgradeItem.@Nullable Kind open;

    UpgradeColumn(StorageListMenu<?> menu) {
        this.menu = menu;
    }

    void layout(int leftPos, int topPos) {
        x = leftPos - WIDTH;
        y = topPos + TOP;
    }

    /** Canto do item do primeiro slot, relativo ao canto do painel do Baú (o que o {@code place} do menu pede). */
    static int slotItemX() {
        return SLOT_X + 1 - WIDTH;
    }

    static int slotItemY() {
        return TOP + PAD_Y + 1;
    }

    ChestUpgradeItem.@Nullable Kind open() {
        return open;
    }

    private ChestUpgradeItem.@Nullable Kind kindAt(int slot) {
        return menu.slots.get(ChestUpgradeSlots.UPGRADE_FIRST + slot).getItem().getItem() instanceof ChestUpgradeItem item
                ? item.kind() : null;
    }

    /** Acerta o painel aberto pelos upgrades nos slots; devolve se mudou. */
    boolean sync() {
        ChestUpgradeItem.Kind first = kindAt(0);
        ChestUpgradeItem.Kind second = kindAt(1);
        ChestUpgradeItem.Kind next = open;
        if (next == null || (next != first && next != second)) {
            next = first != null ? first : second;
        }
        if (next == open) {
            return false;
        }
        open = next;
        return true;
    }

    private int slotY(int slot) {
        return y + PAD_Y + slot * ChestUpgradeSlots.UPGRADE_STEP;
    }

    private boolean onTab(int slot, double mouseX, double mouseY) {
        int ty = slotY(slot);
        return mouseX >= x + TAB_X && mouseX < x + TAB_X + TAB_W && mouseY >= ty && mouseY < ty + 18;
    }

    private boolean onSlot(int slot, double mouseX, double mouseY) {
        int sy = slotY(slot);
        return mouseX >= x + SLOT_X && mouseX < x + SLOT_X + 18 && mouseY >= sy && mouseY < sy + 18;
    }

    boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + HEIGHT + SHADOW;
    }

    /** Clique na aba de um slot ocupado: abre o painel dele. Devolve se consumiu o clique. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < ChestUpgrades.SLOTS; i++) {
            ChestUpgradeItem.Kind kind = kindAt(i);
            if (kind != null && onTab(i, mouseX, mouseY)) {
                if (button == 0) {
                    open = kind;
                }
                return true;
            }
        }
        return false;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(x + SHADOW, y + HEIGHT, x + WIDTH, y + HEIGHT + SHADOW, GuiPaint.BEVEL_DARK);
        g.fill(x, y, x + WIDTH, y + HEIGHT, GuiPaint.PANEL);
        g.fill(x, y, x + WIDTH, y + 1, GuiPaint.BUTTON_BORDER);
        g.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, GuiPaint.BUTTON_BORDER);
        g.fill(x, y, x + 1, y + HEIGHT, GuiPaint.BUTTON_BORDER);
        for (int i = 0; i < ChestUpgrades.SLOTS; i++) {
            int sy = slotY(i);
            GuiPaint.slot(g, x + SLOT_X, sy);
            ChestUpgradeItem.Kind kind = kindAt(i);
            if (kind == null) {
                continue;
            }
            boolean on = kind == open;
            boolean hovered = onTab(i, mouseX, mouseY);
            GuiPaint.box(g, x + TAB_X, sy, TAB_W, 18, on ? GuiPaint.ACCENT : GuiPaint.BUTTON,
                    on ? GuiPaint.ACCENT : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
            GuiPaint.arrowRight(g, x + TAB_X + 2, sy + 6, on ? GuiPaint.SELECTED_TEXT : GuiPaint.MUTED);
            if (on) {
                GuiPaint.outline(g, x + SLOT_X - 1, sy - 1, 20, 20, GuiPaint.ACCENT);
            }
        }
    }

    /** A dica da aba ou do slot vazio; {@code null} no resto (o slot com item mostra a dica do item). */
    @Nullable Component tooltip(double mouseX, double mouseY) {
        for (int i = 0; i < ChestUpgrades.SLOTS; i++) {
            ChestUpgradeItem.Kind kind = kindAt(i);
            if (kind != null && onTab(i, mouseX, mouseY)) {
                return UpgradePanels.tr(kind == ChestUpgradeItem.Kind.CRAFTING ? "tab.crafting" : "tab.compacting");
            }
            if (kind == null && onSlot(i, mouseX, mouseY) && menu.getCarried().isEmpty()) {
                return UpgradePanels.tr("slot");
            }
        }
        return null;
    }

    /** Centro da aba do slot, em coordenadas da tela (gancho do e2e). */
    int[] tabCenter(int slot) {
        return new int[] {x + TAB_X + TAB_W / 2, slotY(slot) + 9};
    }
}
