package io.github.matheusanbs.wirelessautomate.client.dock.render;

/**
 * O giroscópio no miolo da Doca: os três anéis quadrados (latão deitado, porcelana em pé no plano xy, grafite claro
 * no plano yz), cada um em quatro caixas, com a luz do mundo, e o cristal (um cubo de 3 em pé na ponta, no recorte da
 * lente do tier), com a luz cheia: brilha no escuro na cor do tier. As caixas e os recortes são os do
 * {@code .bbmodel}, com o x e o z somados de 8 (o bloco de 0 a 16); todos giram em torno do centro do bloco.
 */
final class DockGyro {
    private static final float C = 8;
    /** A inclinação que põe o cubo do cristal e dos fragmentos em pé na ponta (a rotação do elemento no Blockbench). */
    static final double POINT_X = 45;
    static final double POINT_Z = 35.264;

    private static final float[][] BRASS = {
        {4.8f, 7.65f, 10.5f, 11.2f, 8.35f, 11.2f},
        {4.8f, 7.65f, 4.8f, 11.2f, 8.35f, 5.5f},
        {4.8f, 7.65f, 5.5f, 5.5f, 8.35f, 10.5f},
        {10.5f, 7.65f, 5.5f, 11.2f, 8.35f, 10.5f},
    };
    private static final float[][] PORCELAIN = {
        {3.5f, 12f, 7.65f, 12.5f, 12.5f, 8.35f},
        {3.5f, 3.5f, 7.65f, 12.5f, 4f, 8.35f},
        {3.5f, 4f, 7.65f, 4f, 12f, 8.35f},
        {12f, 4f, 7.65f, 12.5f, 12f, 8.35f},
    };
    private static final float[][] GRAPHITE = {
        {7.65f, 11.3f, 4.1f, 8.35f, 11.9f, 11.9f},
        {7.65f, 4.1f, 4.1f, 8.35f, 4.7f, 11.9f},
        {7.65f, 4.7f, 4.1f, 8.35f, 11.3f, 4.7f},
        {7.65f, 4.7f, 11.3f, 8.35f, 11.3f, 11.9f},
    };
    /** Os recortes dos lados compridos e dos curtos de cada anel. */
    private static final float[][] BRASS_UV = {{0, 16, 6, 17}, {0, 16, 5, 17}};
    private static final float[][] PORCELAIN_UV = {{0, 20, 9, 21}, {0, 20, 8, 21}};
    private static final float[][] GRAPHITE_UV = {{0, 24, 8, 25}, {0, 24, 7, 25}};
    private static final float[] CRYSTAL = {6.5f, 6.5f, 6.5f, 9.5f, 9.5f, 9.5f};
    /** O cristal tem um recorte por face (baixo, cima, norte, sul, oeste, leste). */
    static final float[] CRYSTAL_UV = {
        24, 24, 32, 32, 16, 16, 24, 24, 16, 16, 24, 24, 24, 24, 32, 32, 16, 24, 24, 32, 24, 16, 32, 24,
    };

    private DockGyro() {
    }

    /** Desenha os anéis e o cristal na pose do quadro e devolve quantas caixas. */
    static int render(DockPose pose) {
        DockBoxes.glow(false);
        DockBoxes.reset();
        DockBoxes.keyframe(C, C, C, pose.brassTilt, pose.brassSpin, 0);
        int drawn = ring(BRASS, BRASS_UV);
        DockBoxes.reset();
        DockBoxes.keyframe(C, C, C, 0, pose.porcelainSpin, 0);
        drawn += ring(PORCELAIN, PORCELAIN_UV);
        DockBoxes.reset();
        DockBoxes.keyframe(C, C, C, pose.graphiteSpin, 0, 0);
        drawn += ring(GRAPHITE, GRAPHITE_UV);
        DockBoxes.glow(true);
        DockBoxes.reset().translate(0, (float) pose.crystalLift, 0);
        DockBoxes.keyframe(C, C, C, 0, pose.crystalSpin, 0);
        DockBoxes.scale(C, C, C, (float) pose.crystalScale);
        DockBoxes.rotate(C, C, C, POINT_X, 0, POINT_Z);
        return drawn + DockBoxes.box(CRYSTAL, CRYSTAL_UV);
    }

    private static int ring(float[][] boxes, float[][] uv) {
        for (int i = 0; i < boxes.length; i++) {
            DockBoxes.box(boxes[i], uv[i / 2]);
        }
        return boxes.length;
    }
}
