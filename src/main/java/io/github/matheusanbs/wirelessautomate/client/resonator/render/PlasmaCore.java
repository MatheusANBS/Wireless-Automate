package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * O Núcleo de plasma: um quad virado para a câmera no centro da câmara, desenhado pelo core shader do mod. Cada
 * vértice leva a cor do tier no RGB e a sintonia no alfa, o canto do quad no UV0 e, no UV2 (inteiros, os mesmos nos
 * quatro), no y, o nível de detalhe, que cai com a distância (os dois bits de baixo), mais o valor 4 (terceiro bit)
 * com o buffer cheio; o x é o apagamento da luz em milésimos (0 no Núcleo).
 */
public final class PlasmaCore {
    /** O bit do UV2.y que diz ao shader que o buffer está cheio; os dois de baixo são o detalhe. */
    private static final int FULL_BIT = 4;
    private static final float HALF = 1.45f;
    private static final double NEAR_SQ = 12 * 12;
    private static final double FAR_SQ = 32 * 32;
    private static final float[][] CORNERS = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};

    private PlasmaCore() {
    }

    /** O nível de detalhe do shader pelo quadrado da distância da câmera ao centro: 2 perto, 1 no meio, 0 longe. */
    public static int detail(double distanceSq) {
        return distanceSq < NEAR_SQ ? 2 : distanceSq < FAR_SQ ? 1 : 0;
    }

    /** O {@code poseStack} já está no centro da câmara; {@code distanceSq} é a distância da câmera a ele, ao quadrado. */
    static void render(PoseStack poseStack, MultiBufferSource buffer, ChamberScene scene, float partialTick,
            int tierColor, double distanceSq) {
        int alpha = Math.round(255 * Math.clamp(scene.tune(partialTick), 0f, 1f));
        int detail = detail(distanceSq) | (scene.full() ? FULL_BIT : 0);
        billboard(poseStack, buffer, HALF, tierColor, alpha, detail, 0);
    }

    /**
     * O quad virado para a câmera, de meio lado {@code half}, no centro do {@code poseStack}. {@code detailBits} é o
     * UV2.y (o detalhe e o bit de buffer cheio) e {@code dimPermille} o UV2.x: o apagamento em milésimos, 0 com a luz
     * inteira e 1000 apagado (de 0 a 1000).
     */
    public static void billboard(PoseStack poseStack, MultiBufferSource buffer, float half, int rgb, int alpha,
            int detailBits, int dimPermille) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int dim = Math.clamp(dimPermille, 0, 1000);
        poseStack.pushPose();
        poseStack.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer out = buffer.getBuffer(ChamberRenderTypes.plasma());
        for (int i = 0; i < 4; i++) {
            float u = CORNERS[i][0];
            float v = CORNERS[i][1];
            out.addVertex(pose, (u * 2 - 1) * half, (v * 2 - 1) * half, 0).setColor(r, g, b, alpha).setUv(u, v)
                    .setUv2(dim, detailBits);
        }
        poseStack.popPose();
    }
}
