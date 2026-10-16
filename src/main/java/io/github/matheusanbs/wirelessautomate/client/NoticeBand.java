package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A faixa do aviso do painel (no painel do nó e no aviso do Tablet sem painel): o texto quebra e a faixa
 * cresce para cima, até {@link #MAX_LINES} linhas, com o Desfazer à direita da primeira. A última linha só
 * é abreviada se o texto passar das três, com o texto inteiro na dica.
 */
public final class NoticeBand {
    public static final int MAX_LINES = 3;
    /** Altura da faixa com uma linha, e a do Desfazer. */
    public static final int LINE_H = 13;

    private NoticeBand() {
    }

    /** Largura do Desfazer (o texto e o recuo), para a área de clique. */
    public static int undoWidth(Font font) {
        return font.width(undoLabel()) + 8;
    }

    /** Altura da faixa para {@code notice} na largura {@code width}, com ou sem o Desfazer. */
    public static int height(Font font, PanelNoticePayload notice, int width, boolean undo) {
        int lines = Math.max(1, GuiText.lineCount(font, notice.message(), textRoom(font, width, undo), MAX_LINES));
        return LINE_H + (lines - 1) * 10;
    }

    /**
     * Desenha a faixa com o topo em {@code y}, por cima do que estiver embaixo dela. {@code undoX} é o x da
     * área do Desfazer, ou −1 sem ele.
     */
    public static void render(GuiGraphics g, Font font, PanelNoticePayload notice, int x, int y, int width, int undoX,
            boolean undoHovered) {
        boolean undo = undoX >= 0;
        g.pose().pushPose();
        g.pose().translate(0, 0, 350);
        g.fill(x, y, x + width, y + height(font, notice, width, undo), GuiPaint.FG);
        GuiText.wrap(g, font, notice.message(), x + 4, y + 3, textRoom(font, width, undo), MAX_LINES, GuiPaint.PANEL);
        if (undo) {
            Component label = undoLabel();
            int lx = undoX + 4;
            GuiPaint.text(g, font, label, lx, y + 3, undoHovered ? GuiPaint.ACCENT : GuiPaint.PANEL);
            g.fill(lx, y + 11, lx + font.width(label) - 1, y + 12, GuiPaint.ACCENT);
        }
        g.pose().popPose();
    }

    private static int textRoom(Font font, int width, boolean undo) {
        return width - 8 - (undo ? undoWidth(font) + 2 : 0);
    }

    private static Component undoLabel() {
        return Component.translatable("gui.wirelessautomate.panel.notice.undo");
    }
}
