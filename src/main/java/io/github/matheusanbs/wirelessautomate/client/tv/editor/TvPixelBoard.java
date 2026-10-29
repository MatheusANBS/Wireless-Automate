package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelBrush;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelCanvas;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSelection;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelSession;
import io.github.matheusanbs.wirelessautomate.client.tv.pixel.TvPixelView;
import net.minecraft.client.gui.GuiGraphics;

/**
 * O quadro do editor de pixel art, sem barra de rolagem (decisão 1g da spec da etapa 4). Por quadro desenha só o
 * xadrez e a arte (um quadrilátero cada, da {@link TvPixelTexture}, refeita só quando um pixel muda), as linhas da
 * grade à vista (a cada pixel a partir do zoom {@link #GRID_FROM}, mais forte a cada 8), as linhas dos espelhos, a
 * seleção tracejada e o pincel sob o cursor (um retângulo por linha do pincel). O mouse vira pixel da arte pela
 * {@link TvPixelView}; arrastar com o botão do meio, com Alt ou com Espaço move a vista, e a roda dá zoom no ponto do
 * mouse. Uma arte de outro tamanho (abrir, mudar o tamanho, girar) é enquadrada de novo. Com o papel vegetal ligado,
 * o quadro anterior da animação aparece por baixo, a {@link #ONION_ALPHA}.
 */
final class TvPixelBoard {
    private static final int GRID_FROM = 6;
    private static final int CHECKER = 6;
    private static final int DASH = 4;
    private static final int GRID = 0x2E000000;
    private static final int CURSOR = 0x66FFFFFF;
    private static final int CURSOR_EDGE = 0x99000000;
    private static final float ONION_ALPHA = 0.35f;

    private final TvPixelSession session;
    private final TvPixelTexture texture;
    private TvRect box = TvRect.NONE;
    private int fittedW;
    private int fittedH;
    private boolean panning;
    private double panFromMouseX;
    private double panFromMouseY;
    private int panFromX;
    private int panFromY;

    TvPixelBoard(TvPixelSession session, TvPixelTexture texture) {
        this.session = session;
        this.texture = texture;
    }

    void layout(TvRect box) {
        boolean changed = !box.equals(this.box);
        this.box = box;
        if (changed) {
            fit();
        }
    }

    TvRect box() {
        return box;
    }

    /** Enquadra a arte (a tecla 0 e o botão do rodapé). */
    void fit() {
        TvPixelCanvas canvas = session.canvas();
        fittedW = canvas.width();
        fittedH = canvas.height();
        session.view().fit(box.w(), box.h(), canvas.width(), canvas.height());
    }

    private TvPixelView view() {
        return session.view();
    }

    /** O pixel da arte sob o ponto da tela (pode cair fora da arte). */
    int cellX(double mouseX) {
        return view().cellX(mouseX - box.x());
    }

    int cellY(double mouseY) {
        return view().cellY(mouseY - box.y());
    }

    /** O centro do pixel ({@code x}, {@code y}) na tela (gancho do e2e). */
    int[] cellCenter(int x, int y) {
        int zoom = view().zoom();
        return new int[] {box.x() + view().panX() + x * zoom + zoom / 2, box.y() + view().panY() + y * zoom + zoom / 2};
    }

    boolean panning() {
        return panning;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        TvPixelCanvas canvas = session.canvas();
        if (canvas.width() != fittedW || canvas.height() != fittedH) {
            fit();
        }
        GuiPaint.inset(g, box.x(), box.y(), box.w(), box.h());
        texture.sync(canvas, session.selection());
        int zoom = view().zoom();
        int ax = box.x() + view().panX();
        int ay = box.y() + view().panY();
        int aw = canvas.width() * zoom;
        int ah = canvas.height() * zoom;
        int x0 = Math.max(ax, box.x() + 1);
        int y0 = Math.max(ay, box.y() + 1);
        int x1 = Math.min(ax + aw, box.right() - 1);
        int y1 = Math.min(ay + ah, box.bottom() - 1);
        if (x0 < x1 && y0 < y1) {
            texture.drawChecker(g, x0, y0, x1, y1, ax, ay, CHECKER);
            float u0 = (x0 - ax) / (float) aw;
            float v0 = (y0 - ay) / (float) ah;
            float u1 = (x1 - ax) / (float) aw;
            float v1 = (y1 - ay) / (float) ah;
            int onion = session.frames().onionFrame();
            if (onion >= 0) {
                texture.drawFrame(g, canvas, onion, ONION_ALPHA, x0, y0, x1, y1, u0, v0, u1, v1);
            }
            texture.drawArt(g, x0, y0, x1, y1, u0, v0, u1, v1);
        }
        g.enableScissor(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1);
        if (session.grid() && zoom >= GRID_FROM && x0 < x1 && y0 < y1) {
            grid(g, canvas, ax, ay, zoom, x0, y0, x1, y1);
        }
        int mirror = GuiPaint.alpha(GuiPaint.ACCENT, 0xD9);
        if (session.mirrorH()) {
            int mid = ax + aw / 2;
            g.fill(mid - 1, ay, mid + 1, ay + ah, mirror);
        }
        if (session.mirrorV()) {
            int mid = ay + ah / 2;
            g.fill(ax, mid - 1, ax + aw, mid + 1, mirror);
        }
        TvPixelSelection selection = session.selection();
        if (selection.active()) {
            dashed(g, ax + selection.x() * zoom, ay + selection.y() * zoom, selection.width() * zoom,
                    selection.height() * zoom);
        }
        if (box.contains(mouseX, mouseY) && !panning && session.tool().brushed() && !session.dragging()) {
            cursor(g, cellX(mouseX), cellY(mouseY), ax, ay, zoom);
        }
        g.disableScissor();
    }

    private void grid(GuiGraphics g, TvPixelCanvas canvas, int ax, int ay, int zoom, int x0, int y0, int x1, int y1) {
        int strong = GuiPaint.alpha(GuiPaint.ACCENT, 0x73);
        for (int x = Math.max(1, (x0 - ax) / zoom); x < canvas.width(); x++) {
            int px = ax + x * zoom;
            if (px >= x1) {
                break;
            }
            g.fill(px, y0, px + 1, y1, x % 8 == 0 ? strong : GRID);
        }
        for (int y = Math.max(1, (y0 - ay) / zoom); y < canvas.height(); y++) {
            int py = ay + y * zoom;
            if (py >= y1) {
                break;
            }
            g.fill(x0, py, x1, py + 1, y % 8 == 0 ? strong : GRID);
        }
    }

    /** A caixa da seleção: branca por baixo e o tracejado coral por cima, só a parte à vista. */
    private void dashed(GuiGraphics g, int x, int y, int w, int h) {
        GuiPaint.outline(g, x, y, w, h, 0xFFFFFFFF);
        int from = Math.max(x, box.x());
        int to = Math.min(x + w, box.right());
        for (int px = from - Math.floorMod(from - x, 2 * DASH); px < to; px += 2 * DASH) {
            g.fill(px, y, px + DASH, y + 1, GuiPaint.ACCENT);
            g.fill(px, y + h - 1, px + DASH, y + h, GuiPaint.ACCENT);
        }
        from = Math.max(y, box.y());
        to = Math.min(y + h, box.bottom());
        for (int py = from - Math.floorMod(from - y, 2 * DASH); py < to; py += 2 * DASH) {
            g.fill(x, py, x + 1, py + DASH, GuiPaint.ACCENT);
            g.fill(x + w - 1, py, x + w, py + DASH, GuiPaint.ACCENT);
        }
    }

    /** O pincel sob o cursor e nos espelhos: uma faixa por linha do pincel (as linhas dele são contínuas). */
    private void cursor(GuiGraphics g, int cx, int cy, int ax, int ay, int zoom) {
        int[] offsets = TvPixelBrush.offsets(session.brush(), session.round());
        TvPixelCanvas canvas = session.canvas();
        int w = canvas.width();
        int h = canvas.height();
        int i = 0;
        while (i < offsets.length) {
            int dy = offsets[i + 1];
            int left = offsets[i];
            int right = left;
            i += 2;
            while (i < offsets.length && offsets[i + 1] == dy && offsets[i] == right + 1) {
                right = offsets[i];
                i += 2;
            }
            int y = cy + dy;
            run(g, ax, ay, zoom, cx + left, cx + right, y);
            if (session.mirrorH()) {
                run(g, ax, ay, zoom, w - 1 - (cx + right), w - 1 - (cx + left), y);
            }
            if (session.mirrorV()) {
                run(g, ax, ay, zoom, cx + left, cx + right, h - 1 - y);
            }
            if (session.mirrorH() && session.mirrorV()) {
                run(g, ax, ay, zoom, w - 1 - (cx + right), w - 1 - (cx + left), h - 1 - y);
            }
        }
        int size = session.brush() * zoom;
        int shift = (session.brush() - 1) / 2;
        GuiPaint.outline(g, ax + (cx - shift) * zoom - 1, ay + (cy - shift) * zoom - 1, size + 2, size + 2,
                CURSOR_EDGE);
    }

    private static void run(GuiGraphics g, int ax, int ay, int zoom, int x0, int x1, int y) {
        g.fill(ax + x0 * zoom, ay + y * zoom, ax + (x1 + 1) * zoom, ay + (y + 1) * zoom, CURSOR);
    }

    // ------------------------------------------------------------------ mouse

    /** Começa a mover a vista (o botão do meio, ou Alt, ou Espaço). */
    void beginPan(double mouseX, double mouseY) {
        panning = true;
        panFromMouseX = mouseX;
        panFromMouseY = mouseY;
        panFromX = view().panX();
        panFromY = view().panY();
    }

    /** O mouse andou com o botão apertado: move a vista, se ela está sendo arrastada. */
    boolean dragPan(double mouseX, double mouseY) {
        if (!panning) {
            return false;
        }
        view().panTo(panFromX + (int) Math.round(mouseX - panFromMouseX),
                panFromY + (int) Math.round(mouseY - panFromMouseY));
        return true;
    }

    boolean endPan() {
        boolean was = panning;
        panning = false;
        return was;
    }

    /** A roda sobre o quadro: zoom no ponto do mouse. */
    boolean scrolled(double mouseX, double mouseY, double delta) {
        if (!box.contains(mouseX, mouseY) || delta == 0) {
            return false;
        }
        view().zoomAt(mouseX - box.x(), mouseY - box.y(), delta > 0 ? 1 : -1);
        return true;
    }

    /** {@code +} e {@code −}: zoom no centro do quadro. */
    void zoomCenter(int dir) {
        view().zoomAt(box.w() / 2.0, box.h() / 2.0, dir);
    }

    /** O pixel da arte no canto de cima à esquerda da vista, onde o colar sem seleção põe o pedaço. */
    int viewCornerX() {
        return view().cellX(0);
    }

    int viewCornerY() {
        return view().cellY(0);
    }
}
