package io.github.matheusanbs.wirelessautomate.client.tv;

import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.PREVIEW;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvAreaCheck;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A prévia da aba Tamanho: a parede vista de frente, com uma célula de folga em volta da área, as células da área, o
 * bloco colocado marcado e a célula do problema em vermelho. A geometria é montada só quando a área ou o problema
 * mudam ({@link #update}); o desenho é um fundo, as linhas da grade e uns poucos retângulos, sem um por célula.
 */
final class TvAreaPreview {
    private static final int MAX_CELL = 16;
    private static final int PAD = 4;
    /** A célula do problema só alarga a vista até esta distância da área (uma outra Tela longe não). */
    private static final int REACH = 8;

    private TvArea area = TvArea.SINGLE;
    private boolean bad;
    private boolean hasProblemCell;
    private int problemCol;
    private int problemRow;
    private int colMin;
    private int rowMax;
    private int cols;
    private int rows;
    private int cell;
    /** O canto de cima à esquerda da grade, relativo ao canto da tela. */
    private int gridX;
    private int gridY;

    /** Remonta a geometria para a área pedida e o problema achado nela. */
    void update(TvArea area, TvAreaCheck.Problem problem, BlockPos pos, Direction facing) {
        this.area = area;
        bad = !problem.ok();
        hasProblemCell = false;
        colMin = -area.anchorCol() - 1;
        int colMax = area.width() - area.anchorCol();
        int rowMin = -area.anchorRow() - 1;
        rowMax = area.height() - area.anchorRow();
        BlockPos at = problem.pos();
        if (at != null) {
            Direction right = TvBlock.rightOf(facing);
            int col = (at.getX() - pos.getX()) * right.getStepX() + (at.getZ() - pos.getZ()) * right.getStepZ();
            int row = at.getY() - pos.getY();
            if (col >= colMin - REACH && col <= colMax + REACH && row >= rowMin - REACH && row <= rowMax + REACH) {
                hasProblemCell = true;
                problemCol = col;
                problemRow = row;
                colMin = Math.min(colMin, col);
                colMax = Math.max(colMax, col);
                rowMin = Math.min(rowMin, row);
                rowMax = Math.max(rowMax, row);
            }
        }
        cols = colMax - colMin + 1;
        rows = rowMax - rowMin + 1;
        cell = Math.max(1, Math.min(MAX_CELL, Math.min((PREVIEW.w() - 2 * PAD) / cols, (PREVIEW.h() - 2 * PAD) / rows)));
        gridX = PREVIEW.x() + (PREVIEW.w() - cols * cell) / 2;
        gridY = PREVIEW.y() + (PREVIEW.h() - rows * cell) / 2;
    }

    private int cellX(int col) {
        return gridX + (col - colMin) * cell;
    }

    private int cellY(int row) {
        return gridY + (rowMax - row) * cell;
    }

    void render(GuiGraphics g, int left, int top) {
        GuiPaint.inset(g, left + PREVIEW.x(), top + PREVIEW.y(), PREVIEW.w(), PREVIEW.h());
        int x0 = left + gridX;
        int y0 = top + gridY;
        int w = cols * cell;
        int h = rows * cell;
        g.fill(x0, y0, x0 + w, y0 + h, GuiPaint.VIEW_PAPER);
        if (cell >= 4) {
            for (int i = 0; i <= cols; i++) {
                g.fill(x0 + i * cell, y0, x0 + i * cell + 1, y0 + h, GuiPaint.VIEW_GRID);
            }
            for (int i = 0; i <= rows; i++) {
                g.fill(x0, y0 + i * cell, x0 + w, y0 + i * cell + 1, GuiPaint.VIEW_GRID);
            }
        }
        int color = bad ? GuiPaint.DANGER : GuiPaint.ACCENT;
        int ax = left + cellX(-area.anchorCol());
        int ay = top + cellY(area.height() - 1 - area.anchorRow());
        int aw = area.width() * cell;
        int ah = area.height() * cell;
        g.fill(ax, ay, ax + aw, ay + ah, GuiPaint.alpha(color, 64));
        if (hasProblemCell) {
            int px = left + cellX(problemCol);
            int py = top + cellY(problemRow);
            g.fill(px, py, px + cell, py + cell, GuiPaint.DANGER);
        }
        int bx = left + cellX(0);
        int by = top + cellY(0);
        int inset = cell >= 6 ? 2 : 0;
        g.fill(bx + inset, by + inset, bx + cell - inset, by + cell - inset, GuiPaint.RING);
        GuiPaint.outline(g, ax - 1, ay - 1, aw + 2, ah + 2, color);
        if (cell >= 4) {
            GuiPaint.outline(g, ax, ay, aw, ah, color);
        }
    }
}
