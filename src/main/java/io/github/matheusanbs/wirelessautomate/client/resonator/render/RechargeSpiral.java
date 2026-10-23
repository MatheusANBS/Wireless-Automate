package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * A recarga: uma espiral de catalisador (a fita modo 1 do shader) da face de dentro da Porta de Cristais mais perto
 * até o Opaco em recarga, e, quando ela termina, o anel (modo 3) que abre no cristal por 0,7 s. Sem recarga e sem
 * anel em curso não se desenha nada. A fita se vira para a câmera pelo {@link RibbonQuad}.
 */
final class RechargeSpiral {
    private static final float WIDTH = 0.25f;
    private static final float RING_SIZE = 1.0f;
    private static final int MODE_SPIRAL = 1;
    private static final int MODE_RING = 3;
    private static final int FULL = 255;
    /** Rascunho do desenho, que é só na thread de render. */
    private static final double[] AT = new double[3];

    private RechargeSpiral() {
    }

    /** O {@code poseStack} já está no centro da câmara. */
    static void render(PoseStack poseStack, MultiBufferSource buffer, ChamberScene scene, long gameTime,
            float partialTick, int tierColor) {
        byte[] chambers = scene.chambers();
        double ticks = gameTime + partialTick;
        PoseStack.Pose pose = poseStack.last();
        int recharging = ChamberCrystals.recharging(chambers, scene.view());
        if (recharging >= 0 && ChamberCrystals.recharge(recharging, chambers, ticks, scene.view()) < 1) {
            spiral(pose, buffer, scene, recharging, ticks, tierColor);
        }
        for (int i = 0; i < chambers.length; i++) {
            float ring = scene.ring(i, gameTime, partialTick);
            if (ring >= 0 && ring < 1) {
                ChamberCrystals.crystalPos(i, chambers, ticks, scene.view(), AT);
                RibbonQuad.billboard(pose, buffer, (float) AT[0], (float) AT[1], (float) AT[2], RING_SIZE, tierColor,
                        FULL, MODE_RING, Math.round(ring * 100));
            }
        }
    }

    private static void spiral(PoseStack.Pose pose, MultiBufferSource buffer, ChamberScene scene, int chamber,
            double ticks, int tierColor) {
        Vec3 port = scene.nearestCrystalPort(chamber);
        if (port == null) {
            return;
        }
        ChamberCrystals.crystalPos(chamber, scene.chambers(), ticks, scene.view(), AT);
        float bx = (float) AT[0];
        float by = (float) AT[1];
        float bz = (float) AT[2];
        Vec3 center = scene.worldCenter();
        float ax = (float) (port.x - center.x);
        float ay = (float) (port.y - center.y);
        float az = (float) (port.z - center.z);
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        RibbonQuad.strip(pose, buffer, ax, ay, az, bx, by, bz, (float) (eye.x - center.x), (float) (eye.y - center.y),
                (float) (eye.z - center.z), WIDTH, tierColor, FULL, MODE_SPIRAL, 0);
    }
}
