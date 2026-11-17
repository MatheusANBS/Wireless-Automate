package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;

/**
 * Leva o referencial local da Armilar (px; origem no centro do chão, +x leste, +z sul, y para cima) ao mundo, pela
 * mesma regra do {@link ResonatorLayout} ({@code toWorld}: a frente é a do Núcleo). Lógica pura.
 */
public final class ArmilarFrame {
    private double cx;
    private double cy;
    private double cz;
    private int exx;
    private int exz;
    private int ezx;
    private int ezz;
    private float yaw;

    /** A frente é a do Núcleo; {@code origin} é o canto mínimo da caixa (o {@code ChamberView.origin}). */
    public void set(ResonatorLayout.Face front, int[] origin) {
        cx = origin[0] + 2.5;
        cy = origin[1];
        cz = origin[2] + 2.5;
        ResonatorLayout.Face east = ResonatorLayout.toWorld(front, ResonatorLayout.Face.EAST);
        ResonatorLayout.Face south = ResonatorLayout.toWorld(front, ResonatorLayout.Face.SOUTH);
        exx = east.dx();
        exz = east.dz();
        ezx = south.dx();
        ezz = south.dz();
        yaw = (float) Math.atan2(-exz, exx);
    }

    /** O giro em y, em radianos, para {@code Axis.YP.rotation}. */
    public float yaw() {
        return yaw;
    }

    public double centerX() {
        return cx;
    }

    public double centerY() {
        return cy;
    }

    public double centerZ() {
        return cz;
    }

    /** O ponto local (px; x leste, z sul) no mundo, em blocos. */
    public void toWorld(double lx, double ly, double lz, double[] out) {
        dirToWorld(lx, ly, lz, out);
        out[0] += cx;
        out[1] += cy;
        out[2] += cz;
    }

    /** O vetor local (px) como vetor do mundo, em blocos. */
    public void dirToWorld(double lx, double ly, double lz, double[] out) {
        double bx = lx / 16;
        double bz = lz / 16;
        out[0] = bx * exx + bz * ezx;
        out[1] = ly / 16;
        out[2] = bx * exz + bz * ezz;
    }
}
