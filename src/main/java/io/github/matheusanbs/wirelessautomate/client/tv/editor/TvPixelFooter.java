package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelCanvas;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelHistory;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSelection;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSession;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O rodapé do quadro do editor de pixel art. Na primeira linha: desfazer e refazer, o pincel (‹ tamanho ›, a forma
 * quadrada ou redonda), a grade e, à direita, Enquadrar. Na segunda: o pixel e a cor sob o mouse e o tamanho da
 * seleção à esquerda, e o zoom e os passos do desfazer à direita. Os textos só são refeitos quando o número que
 * mostram muda; a linha de estado larga uma parte se não couber, em vez de cortar.
 */
final class TvPixelFooter {
    /** Um botão do rodapé. */
    enum Button { UNDO, REDO, SMALLER, LARGER, SHAPE, GRID, FIT }

    private static final Button[] ALL = Button.values();
    private static final int GAP = 2;
    private static final int GROUP = 6;
    private static final int SIZE_W = 26;

    private final TvPixelSession session;
    private final TvRect[] rects = new TvRect[ALL.length];
    private final Component[] tips = new Component[ALL.length];
    private Font font;
    private TvRect row = TvRect.NONE;
    private TvRect status = TvRect.NONE;
    private GuiText.Fitted gridLabel;
    private int shownBrush = -1;
    private Component brushText = Component.empty();
    private int brushWidth;
    private long shownStatus = Long.MIN_VALUE;
    private GuiText.Fitted statusText;
    private int shownZoom = -1;
    private int shownSteps = -1;
    private Component zoomText = Component.empty();
    private int zoomWidth;

    TvPixelFooter(TvPixelSession session) {
        this.session = session;
    }

    void layout(Font font, TvRect row, TvRect status) {
        this.font = font;
        this.row = row;
        this.status = status;
        int x = row.x();
        int s = TvIcons.SIZE;
        rects[Button.UNDO.ordinal()] = new TvRect(x, row.y(), s, s);
        rects[Button.REDO.ordinal()] = new TvRect(x + s + GAP, row.y(), s, s);
        x += 2 * s + GAP + GROUP;
        rects[Button.SMALLER.ordinal()] = new TvRect(x, row.y(), s, s);
        rects[Button.LARGER.ordinal()] = new TvRect(x + s + SIZE_W, row.y(), s, s);
        rects[Button.SHAPE.ordinal()] = new TvRect(x + 2 * s + SIZE_W + GAP, row.y(), s, s);
        x += 3 * s + SIZE_W + GAP + GROUP;
        Component grid = TvEditorText.key("pixel.grid");
        rects[Button.GRID.ordinal()] = new TvRect(x, row.y(), Math.min(9 + 4 + font.width(grid),
                row.right() - s - GROUP - x), s);
        gridLabel = GuiText.fit(font, grid, rects[Button.GRID.ordinal()].w() - 13);
        rects[Button.FIT.ordinal()] = new TvRect(row.right() - s, row.y(), s, s);
        for (Button button : ALL) {
            tips[button.ordinal()] = TvEditorText.key("pixel.footer." + button.name().toLowerCase(Locale.ROOT));
        }
        shownBrush = -1;
        shownStatus = Long.MIN_VALUE;
        shownZoom = -1;
    }

    private boolean enabled(Button button) {
        return switch (button) {
            case UNDO -> session.history().canUndo();
            case REDO -> session.history().canRedo();
            case SMALLER -> session.brush() > 1;
            case LARGER -> session.brush() < 8;
            default -> true;
        };
    }

    void render(GuiGraphics g, int mouseX, int mouseY, int cellX, int cellY) {
        refreshTexts(cellX, cellY);
        TvEditorPaint.iconButton(g, rects[0], TvIcons.Icon.UNDO, rects[0].contains(mouseX, mouseY), enabled(Button.UNDO));
        TvEditorPaint.iconButton(g, rects[1], TvIcons.Icon.REDO, rects[1].contains(mouseX, mouseY), enabled(Button.REDO));
        TvRect smaller = rects[Button.SMALLER.ordinal()];
        TvRect larger = rects[Button.LARGER.ordinal()];
        TvEditorPaint.iconButton(g, smaller, TvIcons.Icon.STEP_LEFT, smaller.contains(mouseX, mouseY),
                enabled(Button.SMALLER));
        TvEditorPaint.iconButton(g, larger, TvIcons.Icon.STEP_RIGHT, larger.contains(mouseX, mouseY),
                enabled(Button.LARGER));
        GuiPaint.text(g, font, brushText, smaller.right() + (SIZE_W - brushWidth) / 2, row.y() + 4, GuiPaint.FG);
        TvRect shape = rects[Button.SHAPE.ordinal()];
        TvEditorPaint.iconButton(g, shape, session.round() ? TvIcons.Icon.PIXEL_BRUSH_ROUND
                : TvIcons.Icon.PIXEL_BRUSH_SQUARE, shape.contains(mouseX, mouseY), true);
        TvRect grid = rects[Button.GRID.ordinal()];
        GuiPaint.checkbox(g, grid.x(), grid.y() + 3, session.grid(), GuiPaint.ACCENT);
        GuiText.draw(g, font, gridLabel, grid.x() + 13, grid.y() + 4,
                grid.contains(mouseX, mouseY) ? GuiPaint.FG : GuiPaint.MUTED);
        TvRect fit = rects[Button.FIT.ordinal()];
        TvEditorPaint.iconButton(g, fit, TvIcons.Icon.FIT, fit.contains(mouseX, mouseY), true);
        GuiPaint.text(g, font, zoomText, status.right() - zoomWidth, status.y() + 1, GuiPaint.MUTED);
        GuiText.draw(g, font, statusText, status.x(), status.y() + 1, GuiPaint.FG);
    }

    /** Refaz os textos que mudaram: o pincel, o estado sob o mouse e o zoom com os passos. */
    private void refreshTexts(int cellX, int cellY) {
        if (shownBrush != session.brush()) {
            shownBrush = session.brush();
            brushText = TvEditorText.key("pixel.brush", shownBrush);
            brushWidth = font.width(brushText);
        }
        int steps = session.history().size();
        if (shownZoom != session.view().zoom() || shownSteps != steps) {
            shownZoom = session.view().zoom();
            shownSteps = steps;
            zoomText = TvEditorText.key("pixel.zoom", shownZoom, steps, TvPixelHistory.MAX);
            zoomWidth = font.width(zoomText);
            shownStatus = Long.MIN_VALUE;
        }
        TvPixelCanvas canvas = session.canvas();
        TvPixelSelection selection = session.selection();
        boolean inside = canvas.inside(cellX, cellY);
        int index = inside ? canvas.get(cellX, cellY) : -1;
        long key = inside ? ((long) cellX << 48 | (long) cellY << 40 | (long) index << 32) : -1L << 32;
        key ^= selection.active() ? (selection.width() << 8 | selection.height()) + 1 : 0;
        if (key == shownStatus && statusText != null) {
            return;
        }
        shownStatus = key;
        Component cell = inside ? TvEditorText.key("pixel.status.cell", cellX, cellY, index) : null;
        Component size = selection.active()
                ? TvEditorText.key("pixel.status.selection", selection.width(), selection.height()) : null;
        int room = Math.max(0, status.w() - zoomWidth - GROUP);
        Component chosen = cell != null && size != null ? TvEditorText.key("pixel.status.both", cell, size)
                : cell != null ? cell : size != null ? size : Component.literal("—");
        if (font.width(chosen) > room && cell != null && size != null) {
            chosen = session.dragging() ? size : cell;
        }
        statusText = GuiText.fit(font, chosen, room);
    }

    /** O botão sob o mouse que vale agora, ou {@code null}. */
    @Nullable Button clicked(double mouseX, double mouseY) {
        for (Button button : ALL) {
            if (rects[button.ordinal()].contains(mouseX, mouseY)) {
                return enabled(button) ? button : null;
            }
        }
        return null;
    }

    boolean contains(double mouseX, double mouseY) {
        return row.contains(mouseX, mouseY);
    }

    @Nullable Component tooltip(double mouseX, double mouseY) {
        for (Button button : ALL) {
            if (button != Button.GRID && rects[button.ordinal()].contains(mouseX, mouseY)) {
                return tips[button.ordinal()];
            }
        }
        return null;
    }

    int[] center(Button button) {
        return rects[button.ordinal()].center();
    }
}
