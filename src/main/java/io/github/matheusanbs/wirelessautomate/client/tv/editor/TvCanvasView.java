package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.tv.TvArea;

/**
 * Onde a imagem da Tela fica na miniatura e com que escala: a escala em que ela cabe ({@link #fit}) vezes o nível de
 * zoom ({@link TvZoom}), com o canto da imagem movido pelo zoom centrado no mouse e pelo arrasto. As contas de ponto
 * entre a tela e a imagem também ficam aqui, e só aqui: {@link #screenX} e {@link #screenY} são a única conversão
 * pixel de desenho → pixel de tela, e a grade, as linhas de bloco, as caixas, a alça e a textura da miniatura a usam
 * (a borda direita de uma caixa é {@code screenX(x + largura)}, nunca a origem mais a largura arredondada à parte, que
 * com escala fracionária cai um pixel ao lado da linha da grade). A escala efetiva de cada eixo é a da imagem inteira
 * na tela ({@code imageW / pixelWidth}), para o último pixel de desenho cair exatamente no fim da textura. Só números: nada é alocado nem refeito por quadro; a escala de caber e o
 * lugar são refeitos só quando a área da Tela ou o retângulo mudam.
 */
final class TvCanvasView {
    private TvRect rect = TvRect.NONE;
    private TvArea fittedArea;
    private float fitScale = 1;
    private int level;
    private float scale = 1;
    private int originX;
    private int originY;
    private int imageW;
    private int imageH;
    private int fitW;
    private int fitH;
    private double stepX = 1;
    private double stepY = 1;

    /** O retângulo da miniatura mudou: volta a caber. */
    void layout(TvRect rect) {
        this.rect = rect;
        fittedArea = null;
        level = 0;
    }

    /** Refaz a escala de caber e o lugar, só quando a área da Tela mudou. */
    void fit(TvArea area) {
        if (area.equals(fittedArea)) {
            return;
        }
        fittedArea = area;
        int innerW = Math.max(1, rect.w() - 4);
        int innerH = Math.max(1, rect.h() - 4);
        float fit = Math.min(innerW / (float) area.pixelWidth(), innerH / (float) area.pixelHeight());
        fitScale = fit >= 1 ? (float) Math.floor(fit) : fit;
        fitW = Math.max(1, Math.round(area.pixelWidth() * fitScale));
        fitH = Math.max(1, Math.round(area.pixelHeight() * fitScale));
        level = 0;
        place(rect.centerX(), rect.centerY(), scale);
    }

    /** Volta a caber (o botão "Caber"). */
    void reset() {
        level = 0;
        place(rect.centerX(), rect.centerY(), scale);
    }

    /**
     * Um passo de zoom ({@code dir} positivo aproxima) centrado no ponto da tela; devolve se o nível mudou.
     */
    boolean zoom(int dir, double mouseX, double mouseY) {
        int next = TvZoom.step(level, dir);
        if (next == level) {
            return false;
        }
        level = next;
        place((int) Math.round(mouseX), (int) Math.round(mouseY), scale);
        return true;
    }

    /** Move a imagem {@code dx}, {@code dy} px da tela. */
    void pan(int dx, int dy) {
        originX = TvZoom.clamp(originX + dx, imageW, rect.x() + 2, rect.w() - 4);
        originY = TvZoom.clamp(originY + dy, imageH, rect.y() + 2, rect.h() - 4);
    }

    /** Aplica o nível atual mantendo o ponto de {@code anchor} da tela parado (ou centrando, em "caber"). */
    private void place(int anchorX, int anchorY, float oldScale) {
        float newScale = fitScale * TvZoom.STEPS[level];
        int newW = Math.max(1, Math.round(fittedArea.pixelWidth() * newScale));
        int newH = Math.max(1, Math.round(fittedArea.pixelHeight() * newScale));
        int ox = imageW == 0 ? rect.x() : TvZoom.anchored(anchorX, originX, oldScale, newScale);
        int oy = imageW == 0 ? rect.y() : TvZoom.anchored(anchorY, originY, oldScale, newScale);
        scale = newScale;
        imageW = newW;
        imageH = newH;
        stepX = newW / (double) fittedArea.pixelWidth();
        stepY = newH / (double) fittedArea.pixelHeight();
        originX = TvZoom.clamp(ox, imageW, rect.x() + 2, rect.w() - 4);
        originY = TvZoom.clamp(oy, imageH, rect.y() + 2, rect.h() - 4);
    }

    boolean zoomed() {
        return level > 0;
    }

    /** O fator do zoom ({@code 1} é "caber"). */
    int factor() {
        return TvZoom.STEPS[level];
    }

    /** Pixels da GUI por pixel de desenho. */
    float scale() {
        return scale;
    }

    int x() {
        return originX;
    }

    int y() {
        return originY;
    }

    int width() {
        return imageW;
    }

    int height() {
        return imageH;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= originX && mouseX < originX + imageW && mouseY >= originY && mouseY < originY + imageH
                && rect.contains(mouseX, mouseY);
    }

    /** Pixels de tela por pixel de desenho, no eixo x (o da imagem inteira, não o nominal). */
    double stepX() {
        return stepX;
    }

    double stepY() {
        return stepY;
    }

    double toX(double mouseX) {
        return (mouseX - originX) / stepX;
    }

    double toY(double mouseY) {
        return (mouseY - originY) / stepY;
    }

    /** A coluna de tela do pixel de desenho {@code px} (o começo dele; {@code pixelWidth} cai no fim da imagem). */
    int screenX(int px) {
        return originX + (int) Math.round(px * stepX);
    }

    int screenY(int py) {
        return originY + (int) Math.round(py * stepY);
    }
}
