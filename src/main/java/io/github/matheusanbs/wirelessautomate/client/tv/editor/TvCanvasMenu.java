package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * O menu do clique direito numa peça da miniatura: as camadas (Trazer para frente, Subir, Descer, Enviar para trás),
 * Esconder, Agrupar ou Desagrupar, Duplicar e Remover, em linhas separadas por filetes. Abre no ponto do clique, preso
 * à janela do editor; o clique numa linha devolve o item e fecha, e o de fora só fecha. Os textos são medidos no
 * {@link #layout}; por quadro, só o desenho. Quem usa executa o item pelo {@link TvEditorDoc}.
 */
final class TvCanvasMenu {
    /** O que cada linha faz. */
    enum Item { FRONT, UP, DOWN, BACK, HIDE, GROUP, UNGROUP, DUPLICATE, REMOVE }

    private static final String[] KEYS = {"menu.front", "menu.up", "menu.down", "menu.back", "menu.hide",
        "group_selection", "ungroup", "duplicate", "remove"};
    private static final int ROW_H = 12;
    private static final int SEP = 3;
    private static final int PAD_X = 6;

    private final TvEditorDoc doc;
    private final GuiText.Fitted[] labels = new GuiText.Fitted[Item.values().length];
    private final Item[] shown = new Item[Item.values().length - 1];
    private Font font;
    private TvRect bounds = TvRect.NONE;
    private int width;
    private boolean open;
    private int x;
    private int y;
    private int height;
    private int count;

    TvCanvasMenu(TvEditorDoc doc) {
        this.doc = doc;
    }

    void layout(Font font, TvRect bounds) {
        this.font = font;
        this.bounds = bounds;
        int widest = 0;
        for (Item item : Item.values()) {
            GuiText.Fitted label = GuiText.fit(font, TvEditorText.key(KEYS[item.ordinal()]), 200);
            labels[item.ordinal()] = label;
            widest = Math.max(widest, font.width(label.shown()));
        }
        width = widest + 2 * PAD_X;
        open = false;
    }

    boolean isOpen() {
        return open;
    }

    void close() {
        open = false;
    }

    /** Abre no ponto, com as linhas que valem para a escolha de agora (Agrupar ou Desagrupar, uma só). */
    void open(double mouseX, double mouseY) {
        count = 0;
        for (Item item : Item.values()) {
            if (item == Item.GROUP && doc.canUngroup() || item == Item.UNGROUP && !doc.canUngroup()) {
                continue;
            }
            shown[count++] = item;
        }
        height = count * ROW_H + 4 + 2 * SEP;
        x = Math.max(bounds.x(), Math.min((int) mouseX, bounds.right() - width));
        y = Math.max(bounds.y(), Math.min((int) mouseY, bounds.bottom() - height));
        open = true;
    }

    boolean contains(double mouseX, double mouseY) {
        return open && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** O lugar da linha {@code k} (de cima para baixo), com os filetes entre os blocos. */
    private int rowY(int k) {
        int extra = (k >= 4 ? SEP : 0) + (k >= count - 2 ? SEP : 0);
        return y + 2 + k * ROW_H + extra;
    }

    private boolean enabled(Item item) {
        return item != Item.GROUP || doc.canGroup();
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        GuiPaint.box(g, x, y, width, height, GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
        for (int k = 0; k < count; k++) {
            Item item = shown[k];
            int rowY = rowY(k);
            if (k == 4 || k == count - 2) {
                g.fill(x + 3, rowY - SEP + 1, x + width - 3, rowY - SEP + 2, GuiPaint.LINE);
            }
            boolean on = enabled(item);
            boolean hovered = on && mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hovered) {
                g.fill(x + 1, rowY, x + width - 1, rowY + ROW_H, GuiPaint.ROW_HOVER);
            }
            int color = !on ? GuiPaint.DISABLED : item == Item.REMOVE ? GuiPaint.DANGER : GuiPaint.FG;
            GuiText.draw(g, font, labels[item.ordinal()], x + PAD_X, rowY + 2, color);
        }
    }

    /** O item da linha sob o mouse, se vale, ou {@code null}. */
    @Nullable Item itemAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) {
            return null;
        }
        for (int k = 0; k < count; k++) {
            int rowY = rowY(k);
            if (mouseY >= rowY && mouseY < rowY + ROW_H) {
                return enabled(shown[k]) ? shown[k] : null;
            }
        }
        return null;
    }

    /** O centro da linha de um item (gancho do e2e), ou {@code null} fechado ou sem ele. */
    int @Nullable [] center(Item item) {
        for (int k = 0; k < count; k++) {
            if (open && shown[k] == item) {
                return new int[] {x + width / 2, rowY(k) + ROW_H / 2};
            }
        }
        return null;
    }
}
