package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;

/**
 * Os quadrados do core shader das fitas: a fita entre dois pontos virada para a câmera e o quadrado de frente para
 * ela. O contrato dos vértices é o do {@code resonator_ribbon}: cor do tier no RGB, intensidade no alfa, o canto
 * (ou a posição ao longo e através da fita) no UV0 e o modo e o parâmetro no UV2. Tudo em {@code float}, sem objeto
 * novo por quadro.
 */
final class RibbonQuad {
    private static final float[][] CORNERS = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};

    private RibbonQuad() {
    }

    /**
     * A fita de {@code a} a {@code b} (relativos ao centro da câmara) com {@code width} de largura, u de 0 a 1 ao
     * longo dela. {@code cx, cy, cz} é a câmera relativa ao mesmo centro. O lado é a direção × (meio − câmera); sem
     * lado (de frente para a câmera) não desenha e devolve {@code false}.
     */
    static boolean strip(PoseStack.Pose pose, MultiBufferSource buffer, float ax, float ay, float az, float bx,
            float by, float bz, float cx, float cy, float cz, float width, int rgb, int alpha, int mode, int param) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float mx = (ax + bx) * 0.5f - cx;
        float my = (ay + by) * 0.5f - cy;
        float mz = (az + bz) * 0.5f - cz;
        float sx = dy * mz - dz * my;
        float sy = dz * mx - dx * mz;
        float sz = dx * my - dy * mx;
        float side = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (side < 1e-6f) {
            return false;
        }
        float half = width * 0.5f / side;
        sx *= half;
        sy *= half;
        sz *= half;
        VertexConsumer out = buffer.getBuffer(ChamberRenderTypes.ribbon());
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        vertex(out, pose, ax - sx, ay - sy, az - sz, r, g, b, alpha, 0, 0, mode, param);
        vertex(out, pose, ax + sx, ay + sy, az + sz, r, g, b, alpha, 0, 1, mode, param);
        vertex(out, pose, bx + sx, by + sy, bz + sz, r, g, b, alpha, 1, 1, mode, param);
        vertex(out, pose, bx - sx, by - sy, bz - sz, r, g, b, alpha, 1, 0, mode, param);
        return true;
    }

    /** O quadrado de lado {@code size} centrado em {@code (x, y, z)}, de frente para a câmera; o UV0 vai de 0 a 1. */
    static void billboard(PoseStack.Pose pose, MultiBufferSource buffer, float x, float y, float z, float size,
            int rgb, int alpha, int mode, int param) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float h = size * 0.5f;
        VertexConsumer out = buffer.getBuffer(ChamberRenderTypes.ribbon());
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        for (float[] corner : CORNERS) {
            float u = corner[0] * 2 - 1;
            float v = corner[1] * 2 - 1;
            vertex(out, pose, x + (left.x * u + up.x * v) * h, y + (left.y * u + up.y * v) * h,
                    z + (left.z * u + up.z * v) * h, r, g, b, alpha, corner[0], corner[1], mode, param);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, int r, int g,
            int b, int alpha, float u, float v, int mode, int param) {
        out.addVertex(pose, x, y, z).setColor(r, g, b, alpha).setUv(u, v).setUv2(mode, param);
    }
}
