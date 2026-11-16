package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.client.fx.FxRibbon;
import io.github.matheusanbs.wirelessautomate.client.fx.FxSheets;

/**
 * O ponteiro do mostrador do Núcleo, o {@code ponteiro} do filme ({@code partes.js}): uma fita de luz que caça a
 * estação fora da sintonia e para nela em 62%. Não é peça sólida: o filme o desenha como fita, e a spec que o lista
 * entre as peças sólidas cede ao filme.
 */
final class ArmilarPointer {
    /**
     * O {@code z} da face do vidro do mostrador, em px locais. O filme usa 41 (o mostrador do conceito); no jogo a
     * caixa do Núcleo entra 2 px num nicho ({@code desloc["nucleo"] = (0, 0, -2)}), e o modelo
     * {@code armilar/core_*.json} confirma: o vidro é o último elemento, de {@code z} 14 a 15 na casa do Núcleo (que
     * vai de 24 a 40), então a face de fora está em 24 + 15 = 39 = 41 − 2. O ponteiro sobe 0,12 px sobre ela, como no
     * filme.
     */
    static final double Z = 41 + ArmilarSpec.NUCLEO_DESLOC_Z;
    /** O centro do mostrador em y, a largura do curso e a altura do traço, em px (os {@code p0}, {@code larg} e {@code alt} do filme). */
    private static final double CENTRO_Y = 7.5;
    private static final double CURSO = 18;
    private static final double ALTURA = 4.2;
    private static final int POINTS = 5;

    private ArmilarPointer() {
    }

    /** A posição do ponteiro no mostrador, de 0 a 1: caça a estação fora da sintonia e para em 0,62 em sintonia. */
    static double fraction(double t, double u) {
        return Ease.lerp(0.5 + 0.36 * Math.sin(t * 0.7) * Math.cos(t * 0.29), 0.62, u);
    }

    static void draw(ArmilarPass p) {
        double x = (fraction(p.t, p.u) - 0.5) * CURSO;
        FxRibbon.Buffer b = p.local;
        for (int j = 0; j < POINTS; j++) {
            b.pts[j * 3] = (float) x;
            b.pts[j * 3 + 1] = (float) (CENTRO_Y - ALTURA / 2 + ALTURA * j / (POINTS - 1));
            b.pts[j * 3 + 2] = (float) (Z + 0.12);
            b.w[j] = 0.22f;
            b.a[j] = 1f;
        }
        p.ribbon(FxSheets.MIOLO, 0, b, POINTS, ArmilarPass.PONTEIRO, 4, 0, 0);
    }
}
