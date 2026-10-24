package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * Os tentáculos de plasma: de cada cristal vivo, uma fita da ponta dele até a esfera pelo modo 2 do shader das fitas,
 * como o filamento de um globo de plasma até o dedo no vidro. É a corrente que o cristal entrega à esfera; o raio do
 * {@link FlowConduit} é a que sai dela. O Opaco não tem tentáculo; o que recarrega ganha um invertido, mais fino e na
 * cor do catalisador, com a corrente correndo da esfera para o cristal enquanto ele enche (o bit 14 do parâmetro). A fita se vira para a câmera como a do raio, sem objeto novo por quadro; só se desenha
 * dentro de {@link FlowConduit#RANGE_SQ}.
 */
final class PlasmaTendrils {
    /** A altura da ponta do cristal acima do lugar dele: o ápice da malha na escala do desenho. */
    private static final float TIP = 0.22f;
    /** Onde o tentáculo termina, a partir do centro: um pouco dentro da esfera de plasma (0,34). */
    private static final float GRIP = 0.30f;
    private static final float WIDTH = 0.60f;
    private static final double DELAY = 1.2;
    private static final int MODE_TENDRIL = 2;
    private static final int TUNE_STEPS = 15;
    private static final int SEED_SHIFT = 4;
    private static final int SEED_MASK = 7;
    private static final int LENGTH_SHIFT = 7;
    private static final int REVERSE_BIT = 1 << 14;
    /** Rascunho do desenho, que é só na thread de render. */
    private static final double[] AT = new double[3];

    private PlasmaTendrils() {
    }

    /** O {@code poseStack} já está no centro da câmara. */
    static void render(PoseStack poseStack, MultiBufferSource buffer, ChamberScene scene, long gameTime,
            float partialTick, int tierColor) {
        double appear = ChamberScene.on(scene.sinceFormed(gameTime, partialTick), DELAY);
        if (appear <= 0) {
            return;
        }
        byte[] chambers = scene.chambers();
        ChamberView view = scene.view();
        double ticks = gameTime + partialTick;
        int recharging = ChamberCrystals.recharging(chambers, view);
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = scene.worldCenter();
        float cx = (float) (eye.x - center.x);
        float cy = (float) (eye.y - center.y);
        float cz = (float) (eye.z - center.z);
        int tune = Math.round(Math.clamp(scene.tune(partialTick), 0f, 1f) * TUNE_STEPS);
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < chambers.length; i++) {
            if (chambers[i] == 0) {
                continue;
            }
            boolean reverse = ChamberCrystals.isDull(chambers[i]);
            if (reverse && (recharging != i || ChamberCrystals.recharge(i, chambers, ticks, view) >= 1)) {
                continue;
            }
            int alpha = (int) Math.round(255 * appear);
            if (alpha <= 0) {
                continue;
            }
            ChamberCrystals.crystalPos(i, chambers, ticks, view, AT);
            float ax = (float) AT[0];
            float ay = (float) AT[1] + TIP;
            float az = (float) AT[2];
            float length = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            if (length <= GRIP) {
                continue;
            }
            float k = GRIP / length;
            float bx = ax * k;
            float by = ay * k;
            float bz = az * k;
            int tenths = Math.clamp(Math.round((length - GRIP) * 10f), 1, 127);
            int param = tune | ((i & SEED_MASK) << SEED_SHIFT) | (tenths << LENGTH_SHIFT)
                    | (reverse ? REVERSE_BIT : 0);
            RibbonQuad.strip(pose, buffer, ax, ay, az, bx, by, bz, cx, cy, cz, WIDTH, tierColor, alpha, MODE_TENDRIL,
                    param);
        }
    }
}
