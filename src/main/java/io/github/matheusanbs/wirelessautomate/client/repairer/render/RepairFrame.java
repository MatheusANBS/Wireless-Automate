package io.github.matheusanbs.wirelessautomate.client.repairer.render;

import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;

/**
 * O que um quadro do Reparador precisa, montado uma vez no começo do {@code render} e lido pelas partes do desenho.
 * Há uma só ({@link #INSTANCE}), reusada: o render é só na thread de render, um bloco por vez, e assim nada é alocado
 * por quadro. Posições em pixels do modelo, a partir do canto do bloco.
 */
final class RepairFrame {
    static final RepairFrame INSTANCE = new RepairFrame();

    /** Os segundos (0 a 7) de cada linha que anda, ou −1 nas outras. */
    final double[] seconds = new double[RepairMath.LINES];
    boolean lit;
    /** Alguma linha anda. */
    boolean running;
    /** O brilho do emissor, na escala da {@link RepairTimeline} (1 = trabalhando). */
    double emitter;
    /** O relógio do mundo em segundos, o mesmo do {@code GameTime × 1200} dos shaders. */
    double time;
    /** O quarto de volta do facing (norte 0, leste 1, sul 2, oeste 3). */
    int quarter;
    /** A câmera relativa ao canto do bloco, em pixels. */
    float ex;
    float ey;
    float ez;
    /** O quadrado da distância da câmera ao centro do bloco, em blocos. */
    double distanceSq;

    private RepairFrame() {
    }
}
