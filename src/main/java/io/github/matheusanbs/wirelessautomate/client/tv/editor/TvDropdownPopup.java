package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A lista aberta de um {@link TvDropdown}, por cima de tudo, no lugar do {@link TvDropdownPlace}. Abre com a opção
 * escolhida à vista e marcada; o mouse e as setas realçam uma linha, o clique ou o Enter escolhem, Home e End vão ao
 * começo e ao fim. A roda só rola a lista (com a barra fina quando há mais opções que linhas), nunca escolhe. O Esc e o
 * clique fora quem fecha é o editor, pelo {@link TvPopups}. Os textos são medidos uma vez, ao abrir: por quadro, só o
 * desenho.
 */
final class TvDropdownPopup implements TvPopup {
    static final int ROW_H = 11;
    static final int MAX_ROWS = 12;
    /** O espaço da barra de rolagem à direita, quando a lista rola. */
    private static final int BAR_W = 4;
    private static final int TEXT_X = 5;
    private static final int DETAIL_GAP = 8;
    private static final int SHADOW = 0x50000000;

    private final TvDropdown<?> owner;
    private final Font font;
    private final GuiText.Fitted[] names;
    private final GuiText.Fitted @Nullable [] details;
    private final int[] detailWidths;
    private final int selected;
    private final IntConsumer choose;
    private final TvDropdownPlace place;
    private int top;
    private int active;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    /**
     * {@code details} é um texto à direita de cada linha (a prévia da fonte), ou {@code null}; {@code choose} recebe o
     * índice da opção escolhida.
     */
    TvDropdownPopup(TvDropdown<?> owner, Font font, TvRect anchor, TvRect bounds, Component[] labels,
            Component @Nullable [] details, int selected, IntConsumer choose) {
        this.owner = owner;
        this.font = font;
        this.selected = selected;
        this.choose = choose;
        int count = labels.length;
        int[] widths = new int[count];
        this.detailWidths = new int[count];
        int widest = 0;
        for (int i = 0; i < count; i++) {
            widths[i] = font.width(labels[i]);
            detailWidths[i] = details == null ? 0 : font.width(details[i]);
            widest = Math.max(widest, widths[i] + (details == null ? 0 : DETAIL_GAP + detailWidths[i]));
        }
        boolean rolls = count > MAX_ROWS;
        place = TvDropdownPlace.of(anchor, bounds, count, ROW_H, MAX_ROWS, widest + 2 * TEXT_X
                + (rolls ? BAR_W : 0));
        boolean scrolled = count > place.rows();
        int textW = place.w() - 2 * TEXT_X - (scrolled ? BAR_W : 0);
        names = new GuiText.Fitted[count];
        this.details = details == null ? null : new GuiText.Fitted[count];
        for (int i = 0; i < count; i++) {
            int detailW = details == null ? 0 : Math.min(detailWidths[i], textW / 2);
            names[i] = GuiText.fit(font, labels[i], textW - (details == null ? 0 : detailW + DETAIL_GAP));
            if (details != null) {
                this.details[i] = GuiText.fit(font, details[i], detailW);
                detailWidths[i] = font.width(this.details[i].shown());
            }
        }
        active = Math.max(0, selected);
        top = clampTop(active - place.rows() / 2);
    }

    TvDropdown<?> owner() {
        return owner;
    }

    private int count() {
        return names.length;
    }

    private int clampTop(int wanted) {
        return Math.max(0, Math.min(wanted, count() - place.rows()));
    }

    /** Rola o mínimo para a linha {@code index} ficar à vista. */
    private void reveal(int index) {
        if (index < top) {
            top = index;
        } else if (index >= top + place.rows()) {
            top = clampTop(index - place.rows() + 1);
        }
    }

    /** A linha sob o mouse, ou -1 fora das linhas. */
    private int rowAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) {
            return -1;
        }
        int row = (int) Math.floor((mouseY - place.y() - TvDropdownPlace.PAD) / ROW_H);
        return row >= 0 && row < place.rows() && top + row < count() ? top + row : -1;
    }

    @Override
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= place.x() && mouseX < place.x() + place.w() && mouseY >= place.y()
                && mouseY < place.y() + place.h();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            int hovered = rowAt(mouseX, mouseY);
            if (hovered >= 0) {
                active = hovered;
            }
        }
        int x = place.x();
        int y = place.y();
        int w = place.w();
        int h = place.h();
        boolean scrolled = count() > place.rows();
        int rowRight = x + w - 1 - (scrolled ? BAR_W - 1 : 0);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        // a sombra à direita e embaixo; aberta para cima, embaixo é o controle, que fica sem ela
        g.fill(x + w, y + 1, x + w + 1, y + h + (place.above() ? 0 : 1), SHADOW);
        if (!place.above()) {
            g.fill(x + 1, y + h, x + w, y + h + 1, SHADOW);
        }
        GuiPaint.box(g, x, y, w, h, GuiPaint.BUTTON, GuiPaint.BUTTON_HOVER_BORDER);
        for (int r = 0; r < place.rows() && top + r < count(); r++) {
            int i = top + r;
            int ry = y + TvDropdownPlace.PAD + r * ROW_H;
            if (i == active) {
                g.fill(x + 1, ry, rowRight, ry + ROW_H, GuiPaint.ROW_HOVER);
            }
            // a marca da escolhida vai por cima do realce, que é opaco
            if (i == selected) {
                g.fill(x + 1, ry, rowRight, ry + ROW_H, GuiPaint.alpha(GuiPaint.ACCENT, 0x38));
                g.fill(x + 1, ry, x + 3, ry + ROW_H, GuiPaint.ACCENT);
            }
            GuiText.draw(g, font, names[i], x + TEXT_X, ry + 2, GuiPaint.FG);
            if (details != null) {
                GuiText.draw(g, font, details[i], rowRight - TEXT_X + 1 - detailWidths[i], ry + 2, GuiPaint.MUTED);
            }
        }
        if (scrolled) {
            int track = h - 2 * TvDropdownPlace.PAD;
            int thumb = Math.max(6, track * place.rows() / count());
            int thumbY = y + TvDropdownPlace.PAD + (track - thumb) * top / (count() - place.rows());
            g.fill(x + w - 3, thumbY, x + w - 1, thumbY + thumb, GuiPaint.MUTED);
        }
        g.pose().popPose();
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (button == 0 && row >= 0) {
            choose.accept(row);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        top = clampTop(top - (int) Math.signum(delta));
        return true;
    }

    /** As setas, Page Up e Page Down, Home e End andam pela lista; o Enter escolhe. O Esc passa (o editor fecha). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int next = switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> active - 1;
            case GLFW.GLFW_KEY_DOWN -> active + 1;
            case GLFW.GLFW_KEY_PAGE_UP -> active - place.rows();
            case GLFW.GLFW_KEY_PAGE_DOWN -> active + place.rows();
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> count() - 1;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                choose.accept(active);
                yield Integer.MIN_VALUE;
            }
            default -> Integer.MAX_VALUE;
        };
        if (next == Integer.MAX_VALUE) {
            return false;
        }
        if (next != Integer.MIN_VALUE) {
            active = Math.max(0, Math.min(count() - 1, next));
            reveal(active);
        }
        return true;
    }

    // ------------------------------------------------------------------ ganchos do e2e

    /** O centro da linha {@code index}, rolando a lista até ela. */
    int[] rowCenter(int index) {
        reveal(index);
        return new int[] {place.x() + place.w() / 2, place.y() + TvDropdownPlace.PAD + (index - top) * ROW_H
                + ROW_H / 2};
    }

    /** O índice da opção com o texto {@code text}, ou -1. */
    int indexOf(String text) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].full().getString().equals(text)) {
                return i;
            }
        }
        return -1;
    }

    int selected() {
        return selected;
    }

    int active() {
        return active;
    }

    int size() {
        return count();
    }

    TvDropdownPlace place() {
        return place;
    }
}
