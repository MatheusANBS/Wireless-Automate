package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A lente do Núcleo vista de dentro (um quad aditivo na cor do tier, colado na face de dentro do Núcleo e
 * respirando) e o clarão ao sintonizar (o halo no centro, crescendo e sumindo em 0,9 s). Guarda também o desenho
 * do halo, que a cabeça da figura usa.
 */
final class ChamberLens {
    static final ResourceLocation ATLAS = InventoryMenu.BLOCK_ATLAS;
    private static final ResourceLocation HALO = WirelessAutomate.id("block/resonator_halo");
    private static final float LENS_HALF = 0.3f;
    private static final double LENS_INSET = 0.01;
    private static final double FLASH_SECONDS = 0.9;
    private static final double FLASH_GROWTH = 2.2;
    private static final float FLASH_ALPHA = 0.9f;
    private static final float[][] HALO_CORNERS = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
    /** Rascunho do halo, que é desenhado só na thread de render. */
    private static final Vector3f CORNER = new Vector3f();

    private ChamberLens() {
    }

    /** {@code facing} é o do Núcleo montado, para fora da estrutura; a lente fica na face oposta. */
    static void render(PoseStack poseStack, MultiBufferSource buffer, ChamberScene scene, long gameTime,
            float partialTick, int tierColor, Direction facing) {
        PoseStack.Pose pose = poseStack.last();
        double alpha = ChamberScene.on(scene.sinceFormed(gameTime, partialTick), 0)
                * (0.3 + 0.4 * scene.q() + 0.12 * scene.breath(gameTime, partialTick));
        if (alpha > 0) {
            lens(pose, buffer.getBuffer(RenderType.lightning()), scene.center(), facing, tierColor,
                    (int) Math.round(255 * Math.min(1, alpha)));
        }
        double t = scene.sinceTuned(gameTime, partialTick);
        if (t < FLASH_SECONDS) {
            halo(pose, buffer.getBuffer(ChamberRenderTypes.glow()), 0, 0, 0, 1 + FLASH_GROWTH * t,
                    (float) (Math.max(0, 1 - t / FLASH_SECONDS) * FLASH_ALPHA));
        }
    }

    /** O quad da lente, nas duas faces, no plano da face de dentro do Núcleo, 0,01 para dentro da câmara. */
    private static void lens(PoseStack.Pose pose, VertexConsumer out, Vec3 center, Direction facing, int color,
            int alpha) {
        // O centro do Núcleo, relativo ao centro da câmara, recuado meio bloco mais a folga para dentro.
        double back = 0.5 + LENS_INSET;
        double ax = 0.5 - center.x - facing.getStepX() * back;
        double ay = 0.5 - center.y - facing.getStepY() * back;
        double az = 0.5 - center.z - facing.getStepZ() * back;
        Direction.Axis axis = facing.getAxis();
        // Os dois eixos do quad: u em z na face x e em x nas outras; v em z na face y e em y nas outras.
        double ux = axis == Direction.Axis.X ? 0 : LENS_HALF;
        double uz = axis == Direction.Axis.X ? LENS_HALF : 0;
        double vy = axis == Direction.Axis.Y ? 0 : LENS_HALF;
        double vz = axis == Direction.Axis.Y ? LENS_HALF : 0;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        for (int k = 0; k < 8; k++) {
            // Os cantos -u-v, +u-v, +u+v, -u+v, e de volta na ordem contrária para o verso.
            int i = k < 4 ? k : 7 - k;
            int su = i == 1 || i == 2 ? 1 : -1;
            int sv = i >= 2 ? 1 : -1;
            out.addVertex(pose, (float) (ax + su * ux), (float) (ay + sv * vy), (float) (az + su * uz + sv * vz))
                    .setColor(r, g, b, alpha);
        }
    }

    /** O halo virado para a câmera, de lado {@code size} blocos, centrado em {@code (x, y, z)}. */
    static void halo(PoseStack.Pose pose, VertexConsumer out, double x, double y, double z, double size,
            float alpha) {
        if (alpha <= 0) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(ATLAS).apply(HALO);
        Quaternionf rotation = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        float half = (float) (size / 2);
        float strength = Math.min(1, alpha);
        Vector3f corner = CORNER;
        for (int i = 0; i < 4; i++) {
            // Os cantos de HALO_CORNERS em ordem: (U1, V1), (U1, V0), (U0, V0), (U0, V1).
            float u = i < 2 ? sprite.getU1() : sprite.getU0();
            float v = i == 1 || i == 2 ? sprite.getV0() : sprite.getV1();
            corner.set(HALO_CORNERS[i][0] * half, HALO_CORNERS[i][1] * half, 0).rotate(rotation);
            out.addVertex(pose, (float) x + corner.x, (float) y + corner.y, (float) z + corner.z)
                    .setColor(1f, 1f, 1f, strength).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(pose, 0, 1, 0);
        }
    }
}
