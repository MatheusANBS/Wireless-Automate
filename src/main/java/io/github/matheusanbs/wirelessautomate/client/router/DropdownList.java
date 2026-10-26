package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Uma lista suspensa aberta sob um controle: a caixa, uma faixa opcional no topo e outra no rodapé (o título, a
 * busca e a linha fixa da lista de redes; quem a usa desenha, pelo {@link BandPainter}), até {@link #MAX_ROWS}
 * linhas com rolagem pela roda e as setas de que há mais. Abre embaixo do controle e, se não couber na janela,
 * em cima dele. Não sabe o que as linhas são: quem a usa diz quantas são, a largura pedida e desenha cada linha
 * ({@link RowPainter}).
 */
final class DropdownList {
    static final int ROW = 12;
    static final int MAX_ROWS = 8;

    /** Desenha a linha {@code index} (já com a rolagem) no topo {@code ry}. */
    @FunctionalInterface
    interface RowPainter {
        void paint(GuiGraphics g, Font font, int x, int ry, int w, int index, boolean hovered);
    }

    /** Desenha uma faixa (topo ou rodapé) da caixa: {@code (x, y)} é o canto dela e {@code w} a largura da caixa. */
    @FunctionalInterface
    interface BandPainter {
        void paint(GuiGraphics g, Font font, int x, int y, int w);
    }

    private int scroll;
    private boolean placed;
    private int x;
    private int y;
    private int width;
    private int count;
    private int topH;
    private int bottomH;

    /** Rolagem do topo, em linhas. */
    int scroll() {
        return scroll;
    }

    void setScroll(int scroll) {
        this.scroll = scroll;
    }

    /** Sem o controle na tela: a lista fica sem área (nem clique nem desenho). */
    void unplace(int count, int topH, int bottomH) {
        this.placed = false;
        this.x = 0;
        this.y = 0;
        this.width = 0;
        this.count = count;
        this.topH = topH;
        this.bottomH = bottomH;
        this.scroll = clamp(scroll);
    }

    /**
     * Sob o controle ({@code ax}, {@code ay}, altura {@code ah}): a largura pedida, até a do espaço
     * ({@code areaX}, {@code areaW}), e presa à borda direita dele.
     */
    void place(int ax, int ay, int ah, int wanted, int areaX, int areaW, int count, int topH, int bottomH) {
        this.placed = true;
        this.count = count;
        this.topH = topH;
        this.bottomH = bottomH;
        this.scroll = clamp(scroll);
        this.width = Math.min(wanted, areaW);
        this.x = Math.min(ax, areaX + areaW - width);
        // abaixo do controle; se não couber na janela, acima dele
        int below = ay + ah + 1;
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        this.y = below + height() <= screenH - 2 ? below : Math.max(2, ay - 1 - height());
    }

    /** A rolagem presa ao que cabe, para uma lista que encolheu (a busca filtrou). */
    private int clamp(int value) {
        return Math.max(0, Math.min(value, count - rows()));
    }

    boolean placed() {
        return placed;
    }

    /** Linhas visíveis. */
    int rows() {
        return Math.min(MAX_ROWS, count);
    }

    int count() {
        return count;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    int width() {
        return width;
    }

    int height() {
        return topH + rows() * ROW + bottomH + 4;
    }

    int firstRowY() {
        return y + 2 + topH;
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height();
    }

    /** Índice da linha sob o mouse (com a rolagem), ou -1. */
    int rowAt(double mx, double my) {
        if (!contains(mx, my)) {
            return -1;
        }
        int row = (int) Math.floor((my - firstRowY()) / ROW);
        if (row < 0 || row >= rows()) {
            return -1;
        }
        return row + scroll;
    }

    /** Centro da linha {@code row}, ou {@code null} se ela está fora da vista. */
    int @Nullable [] rowCenter(int row) {
        int visible = row - scroll;
        if (visible < 0 || visible >= rows()) {
            return null;
        }
        return new int[] {x + width / 2, firstRowY() + visible * ROW + ROW / 2};
    }

    /** Rola um passo da roda, se o mouse está na lista. */
    void scroll(double mx, double my, double scrollY) {
        if (contains(mx, my)) {
            scroll = Math.max(0, Math.min(scroll - (int) Math.signum(scrollY), count - rows()));
        }
    }

    /** A caixa, as faixas (se houver), as linhas visíveis e as setas, por cima de tudo. */
    void render(GuiGraphics g, Font font, @Nullable BandPainter top, @Nullable BandPainter bottom, int mouseX,
            int mouseY, RowPainter painter) {
        int rows = rows();
        int hovered = rowAt(mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        GuiPaint.box(g, x, y, width, height(), GuiPaint.PANEL, GuiPaint.BUTTON_BORDER);
        if (top != null) {
            top.paint(g, font, x, y + 2, width);
        }
        int first = firstRowY();
        for (int i = 0; i < rows; i++) {
            int index = i + scroll;
            painter.paint(g, font, x, first + i * ROW, width, index, index == hovered);
        }
        if (bottom != null) {
            bottom.paint(g, font, x, first + rows * ROW, width);
        }
        if (scroll > 0) {
            GuiPaint.text(g, font, Component.literal("▲"), x + width - 9, first,
                    GuiPaint.MUTED);
        }
        if (scroll + rows < count) {
            GuiPaint.text(g, font, Component.literal("▼"), x + width - 9,
                    first + (rows - 1) * ROW + 2, GuiPaint.MUTED);
        }
        g.pose().popPose();
    }
}
