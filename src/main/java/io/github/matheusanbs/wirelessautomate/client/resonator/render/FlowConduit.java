package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
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
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = scene.worldCenter();
        float cx = (float) (eye.x - center.x);
        float cy = (float) (eye.y - center.y);
        float cz = (float) (eye.z - center.z);
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
            int band = Math.clamp(Math.round(flow), 1, 7);
            float width = MIN_WIDTH + (MAX_WIDTH - MIN_WIDTH) * (band - 1) / 6f;
            int alpha = Math.round(255 * Math.min(1f, flow / 7f));
            RibbonQuad.strip(pose, buffer, ax, ay, az, bx, by, bz, cx, cy, cz, width, tierColor, alpha, MODE_CONDUIT,
                    band);
        }
    }
}
