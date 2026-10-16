package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * O traço da figura de Lissajous: cada segmento vira dois quads cruzados e aditivos (um no plano vertical do
 * segmento, outro deitado), mais fortes perto da cabeça; na cabeça, o halo virado para a câmera e um miolo
 * icosaédrico. Os quads saem nas duas faces, porque o {@code lightning} descarta o verso.
 */
final class LissajousTrace {
    private static final float HALF_WIDTH = 0.012f;
    static final int CORAL_LIGHT = 0xFFFFB39E;
    private static final int PORCELAIN = 0xFFFFFBF3;
    private static final double HALO_SIZE = 0.55;
    private static final float CORE_RADIUS = 0.05f;
    private static final double CORE_MIN_Q = 0.03;
    private static final double TRACE_DELAY = 0.9;
    private static final float[][] ICO_VERTICES;
    private static final int[][] ICO_FACES = {
        {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6},
        {7, 1, 8}, {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10},
        {8, 6, 7}, {9, 8, 1}};

    static {
        float t = (float) ((1 + Math.sqrt(5)) / 2);
        float[][] raw = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t},
            {0, 1, -t}, {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        ICO_VERTICES = new float[raw.length][];
        for (int i = 0; i < raw.length; i++) {
            float length = (float) Math.sqrt(raw[i][0] * raw[i][0] + raw[i][1] * raw[i][1] + raw[i][2] * raw[i][2]);
            ICO_VERTICES[i] = new float[] {raw[i][0] / length * CORE_RADIUS, raw[i][1] / length * CORE_RADIUS,
                raw[i][2] / length * CORE_RADIUS};
        }
    }

    private LissajousTrace() {
    }

    static void render(PoseStack poseStack, MultiBufferSource buffer, ChamberScene scene, float partialTick,
            int tierColor) {
        double q = scene.q();
        double lit = ChamberScene.on(scene.sinceFormed(scene.lastUsed, partialTick), TRACE_DELAY);
        if (lit <= 0) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        int color = GuiPaint.mix(CORAL_LIGHT, tierColor, (float) (0.35 * q));
        double alpha = lit * (0.12 + 0.7 * q) * Math.min(1, 4 * q + 0.05);
        drawTrail(pose, buffer.getBuffer(RenderType.lightning()), scene, partialTick, color, alpha);

        double[] head = scene.headPoint(partialTick);
        ChamberLens.halo(pose, buffer.getBuffer(ChamberRenderTypes.glow()), head[0], head[1], head[2],
                HALO_SIZE, (float) (lit * (0.35 + 0.55 * q)));
        if (q > CORE_MIN_Q) {
            int core = GuiPaint.mix(PORCELAIN, CORAL_LIGHT, (float) (1 - q));
            drawCore(pose, buffer.getBuffer(RenderType.lightning()), head, core, (int) Math.round(255 * lit));
        }
    }

    private static void drawTrail(PoseStack.Pose pose, VertexConsumer out, ChamberScene scene, float partialTick,
            int color, double alpha) {
        double[] now = scene.current();
        double[] before = scene.previous();
        int last = LissajousCurve.POINTS - 1;
        float[] a = new float[3];
        float[] b = new float[3];
        point(before, now, 0, partialTick, a);
        for (int i = 0; i < last; i++) {
            point(before, now, i + 1, partialTick, b);
            int alphaA = (int) Math.round(255 * alpha * i / last);
            int alphaB = (int) Math.round(255 * alpha * (i + 1) / last);
            if (alphaB > 0) {
                segment(pose, out, a, b, HALF_WIDTH, color, alphaA, alphaB);
            }
            float[] swap = a;
            a = b;
            b = swap;
        }
    }

    private static void point(double[] before, double[] now, int i, float partialTick, float[] out) {
        int at = i * 3;
        for (int k = 0; k < 3; k++) {
            out[k] = (float) (before[at + k] + (now[at + k] - before[at + k]) * partialTick);
        }
    }

    /**
     * Dois quads cruzados ao longo de {@code a → b}, de meia largura {@code half}: um pelo plano vertical do
     * segmento e um deitado nele. Os feixes dos cristais usam o mesmo desenho.
     */
    static void segment(PoseStack.Pose pose, VertexConsumer out, float[] a, float[] b, float half, int color,
            int alphaA, int alphaB) {
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float dz = b[2] - a[2];
        // O lado horizontal: d × (0, 1, 0); num segmento vertical, o eixo x.
        float sx = -dz;
        float sz = dx;
        float sideLength = (float) Math.sqrt(sx * sx + sz * sz);
        if (sideLength < 1e-6f) {
            sx = 1;
            sz = 0;
        } else {
            sx /= sideLength;
            sz /= sideLength;
        }
        // O de cima: lado × d, que fica no plano vertical do segmento.
        float ux = -sz * dy;
        float uy = sz * dx - sx * dz;
        float uz = sx * dy;
        float upLength = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (upLength < 1e-6f) {
            ux = 0;
            uy = 1;
            uz = 0;
        } else {
            ux /= upLength;
            uy /= upLength;
            uz /= upLength;
        }
        ribbon(pose, out, a, b, ux * half, uy * half, uz * half, color, alphaA, alphaB);
        ribbon(pose, out, a, b, sx * half, 0, sz * half, color, alphaA, alphaB);
    }

    private static void ribbon(PoseStack.Pose pose, VertexConsumer out, float[] a, float[] b, float ox, float oy,
            float oz, int color, int alphaA, int alphaB) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int bl = color & 0xFF;
        out.addVertex(pose, a[0] - ox, a[1] - oy, a[2] - oz).setColor(r, g, bl, alphaA);
        out.addVertex(pose, a[0] + ox, a[1] + oy, a[2] + oz).setColor(r, g, bl, alphaA);
        out.addVertex(pose, b[0] + ox, b[1] + oy, b[2] + oz).setColor(r, g, bl, alphaB);
        out.addVertex(pose, b[0] - ox, b[1] - oy, b[2] - oz).setColor(r, g, bl, alphaB);
        out.addVertex(pose, b[0] - ox, b[1] - oy, b[2] - oz).setColor(r, g, bl, alphaB);
        out.addVertex(pose, b[0] + ox, b[1] + oy, b[2] + oz).setColor(r, g, bl, alphaB);
        out.addVertex(pose, a[0] + ox, a[1] + oy, a[2] + oz).setColor(r, g, bl, alphaA);
        out.addVertex(pose, a[0] - ox, a[1] - oy, a[2] - oz).setColor(r, g, bl, alphaA);
    }

    /** O icosaedro da cabeça, um triângulo por quad (o último vértice repetido), com a frente para fora. */
    private static void drawCore(PoseStack.Pose pose, VertexConsumer out, double[] head, int color, int alpha) {
        float hx = (float) head[0];
        float hy = (float) head[1];
        float hz = (float) head[2];
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        for (int[] face : ICO_FACES) {
            float[] p0 = ICO_VERTICES[face[0]];
            float[] p1 = ICO_VERTICES[face[1]];
            float[] p2 = ICO_VERTICES[face[2]];
            out.addVertex(pose, hx + p0[0], hy + p0[1], hz + p0[2]).setColor(r, g, b, alpha);
            out.addVertex(pose, hx + p1[0], hy + p1[1], hz + p1[2]).setColor(r, g, b, alpha);
            out.addVertex(pose, hx + p2[0], hy + p2[1], hz + p2[2]).setColor(r, g, b, alpha);
            out.addVertex(pose, hx + p2[0], hy + p2[1], hz + p2[2]).setColor(r, g, b, alpha);
        }
    }
}
