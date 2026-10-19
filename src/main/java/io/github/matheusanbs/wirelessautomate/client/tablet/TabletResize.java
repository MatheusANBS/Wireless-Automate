package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.DEFAULT_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.DEFAULT_W;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.MIN_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.MIN_W;

import io.github.matheusanbs.wirelessautomate.client.ResizeHandle;
import org.jetbrains.annotations.Nullable;

/**
 * O tamanho da tela do Tablet: o lembrado na sessão, os limites pela janela e o arrasto da borda e da alça do
 * canto ({@link ResizeHandle}), em torno do centro. Quem refaz os widgets no tamanho novo é a tela.
 */
final class TabletResize {
    /** Alça do canto (lado, em px) e espessura das bordas que redimensionam. */
    private static final int GRIP = 7;
    private static final int EDGE = 3;
    private static int savedW = DEFAULT_W;
    private static int savedH = DEFAULT_H;

    private final TabletLayout layout;
    private final ResizeHandle handle = new ResizeHandle(GRIP, EDGE);

    TabletResize(TabletLayout layout) {
        this.layout = layout;
    }

    /** A largura a usar no {@code init}: a lembrada, dentro da janela de largura {@code screenW}. */
    static int width(int screenW) {
        return Math.max(MIN_W, Math.min(savedW, maxW(screenW)));
    }

    static int height(int screenH) {
        return Math.max(MIN_H, Math.min(savedH, maxH(screenH)));
    }

    private static int maxW(int screenW) {
        return Math.max(MIN_W, screenW - 8);
    }

    private static int maxH(int screenH) {
        return Math.max(MIN_H, screenH - 8);
    }

    /** Lembra o tamanho (o do e2e, como se a borda tivesse sido arrastada). */
    static void remember(int w, int h) {
        savedW = w;
        savedH = h;
    }

    boolean begin(double mouseX, double mouseY) {
        return handle.begin(mouseX, mouseY, layout.left, layout.top, layout.w, layout.h);
    }

    boolean dragging() {
        return handle.dragging();
    }

    void end() {
        handle.end();
    }

    @Nullable ResizeHandle.Edge hover(double mouseX, double mouseY) {
        return handle.hover(mouseX, mouseY, layout.left, layout.top, layout.w, layout.h);
    }

    /**
     * Redimensiona em torno do centro, como o roteador: a borda arrastada segue o mouse e a oposta se
     * move igual, então o painel continua centralizado. Largura e altura sempre pares. Devolve se o tamanho
     * mudou (aí ele já está lembrado e a tela refaz os widgets).
     */
    boolean drag(double mouseX, double mouseY, int screenW, int screenH) {
        int newW = layout.w;
        int newH = layout.h;
        if (handle.changesWidth()) {
            double wanted = handle.wantedWidth(mouseX);
            newW = Math.max(MIN_W, Math.min(maxW(screenW), (int) Math.round(wanted / 2) * 2));
        }
        if (handle.changesHeight()) {
            double wanted = handle.wantedHeight(mouseY);
            newH = Math.max(MIN_H, Math.min(maxH(screenH), (int) Math.round(wanted / 2) * 2));
        }
        if (newW != layout.w || newH != layout.h) {
            remember(newW, newH);
            return true;
        }
        return false;
    }
}
