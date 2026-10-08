package io.github.matheusanbs.wirelessautomate.client;

import org.jetbrains.annotations.Nullable;

/**
 * Redimensionar uma tela pela borda direita, pela de baixo ou pela alça do canto, crescendo em torno
 * do centro: a borda arrastada segue o mouse e a oposta se move igual, espelhada, então o painel
 * continua centralizado. Guarda o estado de um arraste; cada tela continua aplicando o próprio
 * arredondamento e limites ao tamanho pedido ({@link #wantedWidth}, {@link #wantedHeight}) e só
 * mexe no eixo da borda pega ({@link #changesWidth}, {@link #changesHeight}). O desenho da alça e do
 * destaque da borda fica em {@link ResizeGrip}.
 *
 * <p>Lógica pura, sem classes do Minecraft (testada por JUnit). Uso típico:
 * <pre>{@code
 * mouseClicked:  if (button == 0 && handle.begin(mx, my, leftPos, topPos, w, h)) return true;
 * mouseDragged:  if (handle.dragging()) { ... handle.wantedWidth(mx) ...; return true; }
 * mouseReleased: if (handle.dragging()) { handle.end(); return true; }
 * resize:        handle.end();
 * }</pre>
 */
public final class ResizeHandle {

    /** O que o mouse pegou: a borda direita (largura), a de baixo (altura) ou o canto (as duas). */
    public enum Edge {
        WIDTH, HEIGHT, BOTH
    }

    private final int grip;
    private final int edge;
    private @Nullable Edge resizing;
    /** Centro do painel no começo do arrasto: ele não se mexe enquanto o tamanho muda. */
    private double centerX;
    private double centerY;
    /** Distância do mouse até a borda arrastada no começo, para a borda não pular para o cursor. */
    private double grabX;
    private double grabY;

    /**
     * @param grip lado, em px, da alça quadrada do canto (dentro do painel)
     * @param edge espessura, em px, das faixas da borda direita e de baixo que redimensionam
     */
    public ResizeHandle(int grip, int edge) {
        this.grip = grip;
        this.edge = edge;
    }

    /**
     * O que o mouse redimensiona ali, ou {@code null}: a alça do canto (ganha das bordas), a borda
     * direita ou a de baixo. Fora do painel é sempre {@code null}.
     */
    public @Nullable Edge at(double mouseX, double mouseY, int left, int top, int width, int height) {
        double right = left + width;
        double bottom = top + height;
        if (mouseX < left || mouseY < top || mouseX >= right || mouseY >= bottom) {
            return null;
        }
        if (mouseX >= right - grip && mouseY >= bottom - grip) {
            return Edge.BOTH;
        }
        if (mouseX >= right - edge) {
            return Edge.WIDTH;
        }
        if (mouseY >= bottom - edge) {
            return Edge.HEIGHT;
        }
        return null;
    }

    /** Começa um arrasto se o mouse está numa borda ou na alça; devolve se pegou. */
    public boolean begin(double mouseX, double mouseY, int left, int top, int width, int height) {
        Edge hit = at(mouseX, mouseY, left, top, width, height);
        if (hit == null) {
            return false;
        }
        resizing = hit;
        centerX = left + width / 2.0;
        centerY = top + height / 2.0;
        grabX = left + width - mouseX;
        grabY = top + height - mouseY;
        return true;
    }

    public boolean dragging() {
        return resizing != null;
    }

    /** A borda pega no arrasto atual, ou {@code null} sem arrasto. */
    public @Nullable Edge edge() {
        return resizing;
    }

    /** Termina o arrasto (soltar o botão, ou a janela do jogo mudou de tamanho). */
    public void end() {
        resizing = null;
    }

    /** A borda a destacar: a do arrasto, se houver, senão a sob o mouse. */
    public @Nullable Edge hover(double mouseX, double mouseY, int left, int top, int width, int height) {
        return resizing != null ? resizing : at(mouseX, mouseY, left, top, width, height);
    }

    /** O arrasto atual muda a largura (borda direita ou canto). */
    public boolean changesWidth() {
        return resizing != null && resizing != Edge.HEIGHT;
    }

    /** O arrasto atual muda a altura (borda de baixo ou canto). */
    public boolean changesHeight() {
        return resizing != null && resizing != Edge.WIDTH;
    }

    /** Largura que o mouse pede, ainda sem arredondar nem limitar, com o painel centrado. */
    public double wantedWidth(double mouseX) {
        return 2 * (mouseX + grabX - centerX);
    }

    /** Altura que o mouse pede, ainda sem arredondar nem limitar, com o painel centrado. */
    public double wantedHeight(double mouseY) {
        return 2 * (mouseY + grabY - centerY);
    }
}
