package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Um controle da coluna de propriedades: uma caixa ({@code x}, {@code y}, {@code w}, {@code h}) no conteúdo, que o
 * painel rola, e o lugar dela na tela ({@code ax}, {@code ay}), refeito pelo {@link #place} quando o painel rola ou
 * muda. Desenha-se, responde ao mouse (em coordenadas da tela) e, se for um campo de texto, às teclas. Os valores vêm
 * de fornecedores lidos só no {@link #refresh}, que o painel chama quando o rascunho muda, nunca por quadro.
 */
abstract class TvControl {
    final String id;
    final int x;
    int y;
    final int w;
    final int h;
    int ax;
    int ay;

    TvControl(String id, int x, int y, int w, int h) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /** O canto do conteúdo na tela, já rolado: soma o canto do controle. */
    void place(int originX, int originY) {
        ax = originX + x;
        ay = originY + y;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= ax && mouseX < ax + w && mouseY >= ay && mouseY < ay + h;
    }

    abstract void render(GuiGraphics g, int mouseX, int mouseY);

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    boolean charTyped(char c, int modifiers) {
        return false;
    }

    /** Se o controle está recebendo o teclado (um campo de texto com o foco). */
    boolean wantsKeys() {
        return false;
    }

    /** Perde o foco do teclado. */
    void blur() {
    }

    /** O rascunho mudou: lê os valores de novo. */
    void refresh() {
    }

    int centerX() {
        return ax + w / 2;
    }

    int centerY() {
        return ay + h / 2;
    }
}
