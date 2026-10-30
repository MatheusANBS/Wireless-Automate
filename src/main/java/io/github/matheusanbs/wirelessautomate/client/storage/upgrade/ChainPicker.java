package io.github.matheusanbs.wirelessautomate.client.storage.upgrade;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * O seletor de formas de uma cadeia: uma grade de slots de 18, até 6 por linha, com todas as formas e o alvo atual em
 * destaque, aberta por cima das outras linhas da lista. Só desenha e diz qual forma está sob o mouse; quem abre, fecha
 * e manda a escolha é o {@link CompactingPanelView}.
 */
final class ChainPicker {
    static final int COLS = 6;
    private static final int PAD = 3;
    /** Acima dos ícones da lista (desenhados a 150) e abaixo das dicas (400). */
    private static final float Z = 300;

    private final ItemStack[] stacks;
    private final @Nullable String[] corners;
    private final int target;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    /**
     * @param anchorX canto esquerdo desejado
     * @param below topo desejado (logo abaixo da linha)
     * @param above base desejada quando não cabe embaixo (o topo da linha)
     * @param right limite direito
     * @param bottom limite de baixo
     */
    ChainPicker(ItemStack[] stacks, @Nullable String[] corners, int target, int anchorX, int below, int above,
            int right, int bottom) {
        this.stacks = stacks;
        this.corners = corners;
        this.target = target;
        int cols = Math.min(COLS, stacks.length);
        int lines = (stacks.length + COLS - 1) / COLS;
        this.width = cols * 18 + 2 * PAD;
        this.height = lines * 18 + 2 * PAD;
        this.x = Math.min(anchorX, right - width);
        this.y = below + height <= bottom ? below : above - height;
    }

    boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** A forma sob o mouse, ou −1. */
    int levelAt(double mouseX, double mouseY) {
        if (!isInside(mouseX, mouseY)) {
            return -1;
        }
        int col = (int) (mouseX - x - PAD) / 18;
        int line = (int) (mouseY - y - PAD) / 18;
        if (mouseX < x + PAD || mouseY < y + PAD || col >= COLS) {
            return -1;
        }
        int level = line * COLS + col;
        return level < stacks.length ? level : -1;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0, 0, Z);
        g.fill(x + 2, y + height, x + width + 2, y + height + 2, GuiPaint.BEVEL_DARK);
        g.fill(x + width, y + 2, x + width + 2, y + height, GuiPaint.BEVEL_DARK);
        GuiPaint.box(g, x, y, width, height, GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
        int hovered = levelAt(mouseX, mouseY);
        for (int level = 0; level < stacks.length; level++) {
            int sx = cellX(level);
            int sy = cellY(level);
            GuiPaint.slot(g, sx, sy);
            g.renderItem(stacks[level], sx + 1, sy + 1);
            g.renderItemDecorations(font, stacks[level], sx + 1, sy + 1, corners[level]);
            if (level == target) {
                GuiPaint.outline(g, sx, sy, 18, 18, GuiPaint.ACCENT);
            }
            if (level == hovered) {
                AbstractContainerScreen.renderSlotHighlight(g, sx + 1, sy + 1, 0);
            }
        }
        g.pose().popPose();
    }

    private int cellX(int level) {
        return x + PAD + (level % COLS) * 18;
    }

    private int cellY(int level) {
        return y + PAD + (level / COLS) * 18;
    }

    /** Centro da forma {@code level}, em coordenadas da tela (gancho do e2e). */
    int[] cellCenter(int level) {
        return new int[] {cellX(level) + 9, cellY(level) + 9};
    }
}
