package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * A aura de cada cristal vivo: um quadrado de 1 bloco de frente para a câmera pelo modo 2 do shader, com a
 * intensidade pela sintonia; fora dela a aura treme, cada cristal com a sua fase. O quadrado é maior que o sprite do
 * cristal (0,55): com 0,5 o brilho ficava atrás dele e não aparecia. O Opaco não tem aura, nem em recarga.
 *
 * <p>O parâmetro inteiro do modo 2 leva dois campos, e o UV2 do vértice é um {@code short}, então o total fica abaixo
 * de 32768: a sintonia de 0 a 100 nos 7 bits de baixo e a semente do cristal (o índice da câmara, de 0 a 15) nos 4
 * seguintes.
 */
final class CrystalAura {
    private static final float SIZE = 1.0f;
    private static final double CRYSTAL_DELAY = 0.6;
    private static final int MODE_AURA = 2;
    private static final int SEED_STEP = 128;
    private static final int SEED_MASK = 15;
    /** O mínimo da intensidade do cristal vivo fora de sintonia. */
    private static final float BASE = 0.4f;
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
        int tuneParam = Math.round(tune * 100);
        double ticks = gameTime + partialTick;
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < chambers.length; i++) {
            if (chambers[i] == 0) {
                continue;
            }
            if (ChamberCrystals.isDull(chambers[i])) {
                continue;
            }
            float strength = BASE + (1 - BASE) * tune;
            ChamberCrystals.crystalPos(i, chambers, scene.q(), ticks, scene.view(), AT);
            int param = tuneParam + SEED_STEP * (i & SEED_MASK);
            RibbonQuad.billboard(pose, buffer, (float) AT[0], (float) AT[1], (float) AT[2], SIZE, tierColor,
                    Math.round((float) (255 * appear * strength)), MODE_AURA, param);
        }
    }
}
