package io.github.matheusanbs.wirelessautomate.client.dock.render;

/**
 * O que só existe no laço de trabalho: o pacote (caixa de latão com fita de porcelana, com a luz do mundo) que entra
 * pela vigia do lado do último evento, vai ao cristal e volta, e os quatro fragmentos da cor da lente em órbita, com
 * a luz cheia, como o cristal. O pacote do {@code .bbmodel} entra pelo Leste; para os outros lados, o caminho gira
 * em torno do centro do bloco.
 */
final class DockPacket {
    private static final float C = 8;
    /** O pivô do pacote, na vigia do Leste. */
    private static final float PACKET_X = 15.5f;
    private static final float[] BOX = {14.7f, 7.2f, 7.2f, 16.3f, 8.8f, 8.8f};
    private static final float[] BOX_UV = {40, 0, 44, 4};
    private static final float[] TAPE = {14.6f, 7.75f, 7.1f, 16.4f, 8.25f, 8.9f};
    private static final float[] TAPE_UV = {44, 0, 48, 1};
    /** Um fragmento no centro do bloco (losango de 1,7), com um recorte por face. */
    private static final float[] FRAGMENT = {7.15f, 7.15f, 7.15f, 8.85f, 8.85f, 8.85f};
    private static final float[] FRAGMENT_UV = {
        26, 26, 30, 30, 16, 16, 20, 20, 16, 16, 20, 20, 26, 26, 30, 30, 16, 16, 20, 20, 16, 16, 20, 20,
    };
    /** As direções dos quatro fragmentos ({@code sat_0} a {@code sat_3}): +x, −x, +z, −z. */
    private static final float[] FRAGMENT_X = {1, -1, 0, 0};
    private static final float[] FRAGMENT_Z = {0, 0, 1, -1};
    /** O giro em torno do centro que leva o caminho do Leste ao lado ({@code Direction.ordinal()}): y, depois z. */
    private static final double[] SIDE_Y = {0, 0, 90, -90, 180, 0};
    private static final double[] SIDE_Z = {-90, 90, 0, 0, 0, 0};
    /** Abaixo disto a peça não aparece. */
    private static final double HIDDEN = 1e-3;

    private DockPacket() {
    }

    /** Desenha o pacote pelo lado {@code side} e os fragmentos, e devolve quantas caixas. */
    static int render(DockPose pose, int side) {
        int drawn = 0;
        if (pose.packetScale > HIDDEN) {
            DockBoxes.glow(false);
            DockBoxes.reset();
            DockBoxes.rotate(C, C, C, 0, SIDE_Y[side], SIDE_Z[side]);
            DockBoxes.matrix().translate((float) pose.packetOffset, 0, 0);
            DockBoxes.keyframe(PACKET_X, C, C, 0, pose.packetSpin, 0);
            DockBoxes.scale(PACKET_X, C, C, (float) pose.packetScale);
            drawn += DockBoxes.box(BOX, BOX_UV);
            drawn += DockBoxes.box(TAPE, TAPE_UV);
        }
        if (pose.fragmentScale > HIDDEN) {
            float r = (float) pose.fragmentRadius;
            float s = (float) pose.fragmentScale;
            DockBoxes.glow(true);
            for (int k = 0; k < FRAGMENT_X.length; k++) {
                DockBoxes.reset();
                DockBoxes.keyframe(C, C, C, 0, pose.fragmentSpin, 0);
                DockBoxes.matrix().translate(FRAGMENT_X[k] * r, 0, FRAGMENT_Z[k] * r);
                DockBoxes.scale(C, C, C, s);
                DockBoxes.rotate(C, C, C, DockGyro.POINT_X, 0, DockGyro.POINT_Z);
                drawn += DockBoxes.box(FRAGMENT, FRAGMENT_UV);
            }
        }
        return drawn;
    }
}
