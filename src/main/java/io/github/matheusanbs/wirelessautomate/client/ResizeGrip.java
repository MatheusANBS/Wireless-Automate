package io.github.matheusanbs.wirelessautomate.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Desenho da alça de redimensionar no canto de baixo à direita e do destaque da borda, para as telas
 * que usam {@link ResizeHandle}. A alça e o destaque acendem na cor {@code lit} da tela com o mouse em
 * cima ou arrastando ({@code hover} não nulo, ver {@link ResizeHandle#hover}); parados, a alça fica na
 * cor da borda dos botões. Há duas variações, cada uma igual à que as telas já desenhavam.
 */
public final class ResizeGrip {

    private ResizeGrip() {
    }

    /**
     * Roteador e Baú: três riscos pontilhados na diagonal (2, 3 e 4 pontos); pela alça
     * do canto as duas bordas acendem.
     */
    public static void renderDotted(GuiGraphics g, int left, int top, int width, int height,
            ResizeHandle.@Nullable Edge hover, int lit) {
        int color = hover != null ? lit : GuiPaint.BUTTON_HOVER_BORDER;
        int gx = left + width - 4;
        int gy = top + height - 4;
        for (int i = 0; i < 3; i++) {
            int d = 2 + i * 2;
            for (int k = 0; k <= d; k += 2) {
                g.fill(gx - d + k, gy - k, gx - d + k + 1, gy - k + 1, color);
            }
        }
        if (hover == ResizeHandle.Edge.WIDTH || hover == ResizeHandle.Edge.BOTH) {
            renderRightEdge(g, left, top, width, height, color);
        }
        if (hover == ResizeHandle.Edge.HEIGHT || hover == ResizeHandle.Edge.BOTH) {
            renderBottomEdge(g, left, top, width, height, color);
        }
    }

    /**
     * Filtro: três riscos cheios na diagonal (1, 3 e 5 pixels); só a borda pega acende, a alça do
     * canto não acende borda nenhuma.
     */
    public static void renderSolid(GuiGraphics g, int left, int top, int width, int height,
            ResizeHandle.@Nullable Edge hover, int lit) {
        int color = hover != null ? lit : GuiPaint.BUTTON_HOVER_BORDER;
        int gx = left + width - 4;
        int gy = top + height - 4;
        for (int d = 0; d <= 4; d += 2) {
            for (int k = 0; k <= d; k++) {
                g.fill(gx - d + k, gy - k, gx - d + k + 1, gy - k + 1, color);
            }
        }
        if (hover == ResizeHandle.Edge.WIDTH) {
            renderRightEdge(g, left, top, width, height, color);
        } else if (hover == ResizeHandle.Edge.HEIGHT) {
            renderBottomEdge(g, left, top, width, height, color);
        }
    }

    private static void renderRightEdge(GuiGraphics g, int left, int top, int width, int height, int color) {
        g.fill(left + width - 3, top + 3, left + width - 2, top + height - 3, color);
    }

    private static void renderBottomEdge(GuiGraphics g, int left, int top, int width, int height, int color) {
        g.fill(left + 3, top + height - 3, left + width - 3, top + height - 2, color);
    }
}
