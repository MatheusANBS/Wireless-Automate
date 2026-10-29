package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O alinhamento em 9 posições da Imagem (o {@code align} do estilo × o {@code valign}): uma caixa com 3 × 3 casas,
 * cada uma com um ponto, e a escolhida com um bloco cheio no lugar onde a arte fica. A posição é
 * {@code linha × 3 + coluna} (linha 0 em cima, coluna 0 à esquerda). Lida só no refresh.
 */
final class TvAlignGrid extends TvControl {
    private static final int CELL_W = 14;
    private static final int CELL_H = 8;
    static final int W = 3 * CELL_W + 2;
    static final int H = 3 * CELL_H + 2;

    private final IntSupplier get;
    private final IntConsumer set;
    private final Component tip;
    private int current;

    TvAlignGrid(String id, int x, int y, Component tip, IntSupplier get, IntConsumer set) {
        super(id, x, y, W, H);
        this.get = get;
        this.set = set;
        this.tip = tip;
        refresh();
    }

    @Override
    void refresh() {
        current = get.getAsInt();
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        int hover = indexAt(mouseX, mouseY);
        GuiPaint.box(g, ax, ay, w, h, GuiPaint.BUTTON, hover >= 0 ? GuiPaint.BUTTON_HOVER_BORDER
                : GuiPaint.BUTTON_BORDER);
        for (int i = 0; i < 9; i++) {
            int cx = ax + 1 + i % 3 * CELL_W;
            int cy = ay + 1 + i / 3 * CELL_H;
            if (i == current) {
                g.fill(cx + 3, cy + 2, cx + CELL_W - 3, cy + CELL_H - 2, GuiPaint.ACCENT);
            } else {
                int dot = i == hover ? GuiPaint.FG : GuiPaint.MUTED;
                g.fill(cx + CELL_W / 2 - 1, cy + CELL_H / 2 - 1, cx + CELL_W / 2 + 1, cy + CELL_H / 2 + 1, dot);
            }
        }
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) {
            return -1;
        }
        int col = Math.min(2, Math.max(0, (int) (mouseX - ax - 1) / CELL_W));
        int row = Math.min(2, Math.max(0, (int) (mouseY - ay - 1) / CELL_H));
        return row * 3 + col;
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = indexAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        if (index != current) {
            set.accept(index);
        }
        return true;
    }

    @Override
    @Nullable Component tooltip(double mouseX, double mouseY) {
        return contains(mouseX, mouseY) ? tip : null;
    }

    @Override
    int[] chipCenter(int index) {
        return new int[] {ax + 1 + index % 3 * CELL_W + CELL_W / 2, ay + 1 + index / 3 * CELL_H + CELL_H / 2};
    }
}
