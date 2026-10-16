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
            halo(pose, buffer.getBuffer(RenderType.eyes(ATLAS)), 0, 0, 0, 1 + FLASH_GROWTH * t,
                    (float) (Math.max(0, 1 - t / FLASH_SECONDS) * FLASH_ALPHA));
        }
    }

    /** O quad da lente, nas duas faces, no plano da face de dentro do Núcleo, 0,01 para dentro da câmara. */
    private static void lens(PoseStack.Pose pose, VertexConsumer out, Vec3 center, Direction facing, int color,
            int alpha) {
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        // O centro do Núcleo, relativo ao centro da câmara, recuado meio bloco mais a folga para dentro.
        Vec3 at = new Vec3(0.5, 0.5, 0.5).subtract(center).subtract(normal.scale(0.5 + LENS_INSET));
        Direction.Axis axis = facing.getAxis();
        Vec3 u = axis == Direction.Axis.X ? new Vec3(0, 0, LENS_HALF) : new Vec3(LENS_HALF, 0, 0);
        Vec3 v = axis == Direction.Axis.Y ? new Vec3(0, 0, LENS_HALF) : new Vec3(0, LENS_HALF, 0);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        Vec3[] corners = {at.subtract(u).subtract(v), at.add(u).subtract(v), at.add(u).add(v), at.subtract(u).add(v)};
        for (int i = 0; i < 4; i++) {
            Vec3 c = corners[i];
            out.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, b, alpha);
        }
        for (int i = 3; i >= 0; i--) {
            Vec3 c = corners[i];
            out.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, b, alpha);
        }
    }

    /**
     * O halo virado para a câmera, de lado {@code size} blocos, centrado em {@code (x, y, z)}. O {@code eyes} soma
     * a cor sem olhar o alfa, então o alfa vai multiplicado na cor.
     */
    static void halo(PoseStack.Pose pose, VertexConsumer out, double x, double y, double z, double size,
            float alpha) {
        if (alpha <= 0) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(ATLAS).apply(HALO);
        Quaternionf rotation = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        float half = (float) (size / 2);
        float shade = Math.min(1, alpha);
        float[][] corners = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        float[][] uvs = {{sprite.getU1(), sprite.getV1()}, {sprite.getU1(), sprite.getV0()},
            {sprite.getU0(), sprite.getV0()}, {sprite.getU0(), sprite.getV1()}};
        Vector3f corner = new Vector3f();
        for (int side = 0; side < 2; side++) {
            for (int k = 0; k < 4; k++) {
                int i = side == 0 ? k : 3 - k;
                corner.set(corners[i][0] * half, corners[i][1] * half, 0).rotate(rotation);
                out.addVertex(pose, (float) x + corner.x, (float) y + corner.y, (float) z + corner.z)
                        .setColor(shade, shade, shade, 1f).setUv(uvs[i][0], uvs[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(pose, 0, 1, 0);
            }
        }
    }
}
