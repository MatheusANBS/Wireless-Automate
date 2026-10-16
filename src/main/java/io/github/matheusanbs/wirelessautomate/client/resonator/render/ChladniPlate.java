package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * A placa de Chladni no chão da câmara: dois quads aditivos no mesmo lugar, a figura (3, 2) mais forte com a
 * sintonia e o ruído mais forte longe dela. As duas texturas animam pelo atlas, sem textura dinâmica.
 */
final class ChladniPlate {
    private static final ResourceLocation FIGURE = WirelessAutomate.id("block/resonator_chladni");
    private static final ResourceLocation NOISE = WirelessAutomate.id("block/resonator_chladni_noise");
    private static final float HALF = 1.45f;
    /** O chão da câmara fica em {@code −1,5} do centro; a placa, um pouco acima. */
    private static final float FLOOR_Y = -1.48f;
    private static final double DELAY = 0.3;
    private static final float[][] CORNERS = {{-HALF, -HALF}, {-HALF, HALF}, {HALF, HALF}, {HALF, -HALF}};

    private ChladniPlate() {
    }

    static void render(PoseStack poseStack, MultiBufferSource buffer, ChamberScene scene, float partialTick) {
        double lit = ChamberScene.on(scene.sinceFormed(scene.lastUsed, partialTick), DELAY);
        if (lit <= 0) {
            return;
        }
        double q = scene.q();
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer out = buffer.getBuffer(ChamberRenderTypes.glow());
        quad(pose, out, FIGURE, (float) (q * lit));
        quad(pose, out, NOISE, (float) ((1 - q) * lit));
    }

    /** Um quad deitado; o tipo de desenho não descarta o verso. */
    private static void quad(PoseStack.Pose pose, VertexConsumer out, ResourceLocation texture, float alpha) {
        if (alpha <= 0.002f) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(ChamberLens.ATLAS).apply(texture);
        float strength = Math.min(1, alpha);
        for (int i = 0; i < 4; i++) {
            // Os cantos de CORNERS em ordem: (U0, V0), (U0, V1), (U1, V1), (U1, V0).
            float u = i < 2 ? sprite.getU0() : sprite.getU1();
            float v = i == 1 || i == 2 ? sprite.getV1() : sprite.getV0();
            out.addVertex(pose, CORNERS[i][0], FLOOR_Y, CORNERS[i][1]).setColor(1f, 1f, 1f, strength)
                    .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
        }
    }
}
