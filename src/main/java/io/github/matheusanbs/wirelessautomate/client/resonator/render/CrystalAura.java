package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * A aura de cada cristal vivo: um quadrado de 0,5 bloco de frente para a câmera pelo modo 2 do shader, com a
 * intensidade pela sintonia; fora dela a aura treme, cada cristal com a sua fase. O Opaco não tem aura, salvo o
 * enchimento da recarga: a dele vai do cinza ao coral com o progresso.
 *
 * <p>O parâmetro inteiro do modo 2 leva três campos: a sintonia de 0 a 100 nos 7 bits de baixo, a semente do
 * cristal (o índice da câmara, de 0 a 15) nos 4 seguintes e, de 2048 em diante, o enchimento (0 para o cristal vivo;
 * 1 mais o progresso de 0 a 100 no Opaco em recarga).
 */
final class CrystalAura {
    private static final float SIZE = 1.0f;
    private static final double CRYSTAL_DELAY = 0.6;
    private static final int MODE_AURA = 2;
    private static final int SEED_STEP = 128;
    private static final int FILL_STEP = 2048;
    private static final int SEED_MASK = 15;
    /** O mínimo da intensidade do cristal vivo fora de sintonia e do Opaco que começa a encher. */
    private static final float BASE = 0.4f;
    private static final float BASE_FILL = 0.3f;
    /** Rascunho do desenho, que é só na thread de render. */
    private static final double[] AT = new double[3];

    private CrystalAura() {
    }

    /** O {@code poseStack} já está no centro da câmara. */
    static void render(PoseStack poseStack, MultiBufferSource buffer, ChamberScene scene, long gameTime,
            float partialTick, int tierColor) {
        byte[] chambers = scene.chambers();
        double appear = ChamberScene.on(scene.sinceFormed(gameTime, partialTick), CRYSTAL_DELAY);
        if (appear <= 0) {
            return;
        }
        float tune = Math.clamp(scene.tune(partialTick), 0f, 1f);
        double ticks = gameTime + partialTick;
        int recharging = ChamberCrystals.recharging(chambers, scene.view());
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < chambers.length; i++) {
            if (chambers[i] == 0) {
                continue;
            }
            int fill = 0;
            float strength;
            int tuneParam = Math.round(tune * 100);
            if (ChamberCrystals.isDull(chambers[i])) {
                if (i != recharging) {
                    continue;
                }
                double progress = ChamberCrystals.recharge(i, chambers, ticks, scene.view());
                fill = 1 + (int) Math.round(progress * 100);
                strength = (float) (BASE_FILL + (1 - BASE_FILL) * progress);
                tuneParam = 100;
            } else {
                strength = BASE + (1 - BASE) * tune;
            }
            ChamberCrystals.crystalPos(i, chambers, scene.q(), ticks, scene.view(), AT);
            int param = tuneParam + SEED_STEP * (i & SEED_MASK) + FILL_STEP * fill;
            RibbonQuad.billboard(pose, buffer, (float) AT[0], (float) AT[1], (float) AT[2], SIZE, tierColor,
                    Math.round((float) (255 * appear * strength)), MODE_AURA, param);
        }
    }
}
