package io.github.matheusanbs.wirelessautomate.client.tv.editor;

/**
 * A peça Imagem que nasce de uma arte solta na miniatura do editor: a escala inteira e a caixa. A escala é a maior até
 * {@link #DROP_MAX_SCALE} em que a arte cabe inteira na Tela (uma arte de 16 px vira um bloco de 32 px de desenho, sem
 * tomar a Tela grande inteira); se nem em 1× ela cabe, a Imagem nasce esticada (escala 0) na maior caixa com a
 * proporção da arte. A caixa fica com o centro no ponto onde soltou e presa dentro da área. Pura, com JUnit.
 */
final class TvImageFit {
    /** A maior escala de uma Imagem nova; a aba Estilo vai até a {@code TvPartStyle.MAX_SCALE}. */
    static final int DROP_MAX_SCALE = 2;

    private TvImageFit() {
    }

    /** A escala da arte {@code w} × {@code h} numa área {@code areaW} × {@code areaH}: 1 a {@link #DROP_MAX_SCALE}, ou 0. */
    static int scale(int w, int h, int areaW, int areaH) {
        return Math.min(DROP_MAX_SCALE, Math.min(areaW / w, areaH / h));
    }

    /**
     * A caixa {x, y, largura, altura} da arte na {@code scale} (0 estica, mantendo a proporção) com o centro em
     * ({@code cx}, {@code cy}), empurrada para dentro da área.
     */
    static int[] box(int w, int h, int scale, int areaW, int areaH, int cx, int cy) {
        int bw;
        int bh;
        if (scale > 0) {
            bw = w * scale;
            bh = h * scale;
        } else {
            bw = Math.max(1, Math.min(areaW, areaH * w / h));
            bh = Math.max(1, Math.min(areaH, areaW * h / w));
        }
        bw = Math.min(bw, areaW);
        bh = Math.min(bh, areaH);
        int x = Math.max(0, Math.min(areaW - bw, cx - bw / 2));
        int y = Math.max(0, Math.min(areaH - bh, cy - bh / 2));
        return new int[] {x, y, bw, bh};
    }
}
