package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * O conduto de luz: para cada Porta de Energia que está entregando (faixa de vazão maior que zero), uma fita do
 * plasma até a face de dentro da Porta, desenhada pelo core shader das fitas (modo 0). A largura e a velocidade
 * seguem a faixa e a intensidade, a faixa suavizada. A fita se vira para a câmera: o lado é o produto vetorial da
 * direção pela linha até a câmera, em {@code float}, sem objeto novo por quadro. Só se desenha dentro de
 * {@link #RANGE_SQ}.
 */
final class FlowConduit {
    /** O quadrado da distância da câmera ao centro além da qual o conduto some. */
    static final double RANGE_SQ = 40 * 40;
    /** Onde a fita começa: a borda do plasma, a partir do centro. */
    private static final float START = 0.35f;
    private static final float MIN_WIDTH = 0.08f;
    private static final float MAX_WIDTH = 0.3f;
    private static final float VISIBLE = 0.05f;
    private static final int MODE_CONDUIT = 0;

    private FlowConduit() {
    }

    /** O {@code poseStack} já está no centro da câmara. */
    static void render(PoseStack poseStack, MultiBufferSource buffer, ChamberScene scene, float partialTick,
            int tierColor) {
        List<Vec3> ports = scene.energyInner();
        VertexConsumer out = null;
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = scene.worldCenter();
        float cx = (float) (eye.x - center.x);
        float cy = (float) (eye.y - center.y);
        float cz = (float) (eye.z - center.z);
        int r = (tierColor >> 16) & 0xFF;
        int g = (tierColor >> 8) & 0xFF;
        int b = tierColor & 0xFF;
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < ports.size(); i++) {
            float flow = scene.flow(i, partialTick);
            if (flow < VISIBLE) {
                continue;
            }
            Vec3 port = ports.get(i);
            float bx = (float) (port.x - center.x);
            float by = (float) (port.y - center.y);
            float bz = (float) (port.z - center.z);
            float length = (float) Math.sqrt(bx * bx + by * by + bz * bz);
            if (length < 1e-4f) {
                continue;
            }
            float k = START / length;
            float ax = bx * k;
            float ay = by * k;
            float az = bz * k;
            float dx = bx - ax;
            float dy = by - ay;
            float dz = bz - az;
            float mx = (ax + bx) * 0.5f - cx;
            float my = (ay + by) * 0.5f - cy;
            float mz = (az + bz) * 0.5f - cz;
            // O lado: direção × (meio − câmera), normalizado; sem lado (de frente para a câmera) não há o que desenhar.
            float sx = dy * mz - dz * my;
            float sy = dz * mx - dx * mz;
            float sz = dx * my - dy * mx;
            float side = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (side < 1e-6f) {
                continue;
            }
            int band = Math.clamp(Math.round(flow), 1, 7);
            float half = (MIN_WIDTH + (MAX_WIDTH - MIN_WIDTH) * (band - 1) / 6f) * 0.5f / side;
            sx *= half;
            sy *= half;
            sz *= half;
            int alpha = Math.round(255 * Math.min(1f, flow / 7f));
            if (out == null) {
                out = buffer.getBuffer(ChamberRenderTypes.ribbon());
            }
            vertex(out, pose, ax - sx, ay - sy, az - sz, r, g, b, alpha, 0, 0, band);
            vertex(out, pose, ax + sx, ay + sy, az + sz, r, g, b, alpha, 0, 1, band);
            vertex(out, pose, bx + sx, by + sy, bz + sz, r, g, b, alpha, 1, 1, band);
            vertex(out, pose, bx - sx, by - sy, bz - sz, r, g, b, alpha, 1, 0, band);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, int r, int g,
            int b, int alpha, float u, float v, int band) {
        out.addVertex(pose, x, y, z).setColor(r, g, b, alpha).setUv(u, v).setUv2(MODE_CONDUIT, band);
    }
}
