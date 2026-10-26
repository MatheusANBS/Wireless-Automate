package io.github.matheusanbs.wirelessautomate.client.repairer.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.PlasmaCore;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.RibbonParam;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.RibbonQuad;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * As luzes do Reparador desenhadas pelo BER. O emissor é o plasma do Núcleo do Ressonador ({@link PlasmaCore}) em
 * miniatura, pendurado logo abaixo do {@code emitter_glow} do modelo: o núcleo de forja do mockup é pequeno perto dos
 * cristais (um terço da altura deles, com o brilho em volta), por isso a esfera tem o raio de
 * {@link RepairerGeometry#EMITTER_RADIUS}, e o brilho da {@link RepairTimeline} vira o apagamento do shader.
 */
final class RepairerLights {
    /** O raio da esfera do shader do plasma em frações do meio lado do quad ({@code SPHERE} do .fsh). */
    private static final float SPHERE = 0.234f;
    private static final float EMITTER_HALF = RepairerGeometry.EMITTER_RADIUS / SPHERE;
    /** O laranja quente do núcleo de forja do mockup, mais claro que o coral para a borda do plasma brilhar. */
    private static final int EMITTER_COLOR = 0xFF8A5C;
    private static final int RUNES = 8;
    private static final float RUNE_HALF = 1.6f;
    private static final float DOT_HALF = 0.8f;
    private static final int RUNE_COLOR = 0xFF825A;
    /** O brilho redondo (sem anel) das runas e dos pedestais, largo para encher a peça como as do mockup. */
    private static final int GLOW_DISC = RibbonParam.disc(0.8, false, 0);
    private static final double MIN_LIGHT = 0.01;
    private static final int BURST_COLOR = 0xFFBE78;
    /** O alfa máximo da onda: aditiva, estoura em branco logo acima disso. */
    private static final double SHOCK_ALPHA = 70;
    /** O meio lado do quad da onda, em pixels do modelo: o raio final dela é 3. */
    private static final float SHOCK_HALF = 3.6f;
    private static final float SHOCK_WIDTH = 0.5f;

    private RepairerLights() {
    }

    /** O emissor, só com o Reparador aceso; devolve quantos quads desenhou. */
    static int emitter(PoseStack poseStack, MultiBufferSource buffer, RepairFrame frame) {
        if (!frame.lit) {
            return 0;
        }
        double gain = RepairTimeline.clamp(frame.emitter / RepairTimeline.PEAK);
        poseStack.pushPose();
        poseStack.translate(RepairerGeometry.CENTER, RepairerGeometry.EMITTER_Y, RepairerGeometry.CENTER);
        PlasmaCore.billboard(poseStack, buffer, EMITTER_HALF, EMITTER_COLOR, 255,
                PlasmaCore.detail(frame.distanceSq), (int) Math.round(1000 * (1 - gain)));
        poseStack.popPose();
        return 1;
    }

    /**
     * As fitas, só com linha andando: as 8 runas e os 8 pontos do chão (fracos enquanto há reparo, com a onda de luz
     * da puxada correndo do pedestal de cada linha) e os feixes do {@link RepairerBeams}. Devolve quantos quads desenhou.
     */
    static int ribbons(PoseStack.Pose pose, MultiBufferSource buffer, RepairFrame frame) {
        if (!frame.running) {
            return 0;
        }
        int drawn = 0;
        for (int i = 0; i < RUNES; i++) {
            drawn += disc(pose, buffer, RepairerGeometry.runeX(frame.quarter, i), RepairerGeometry.RUNE_Y,
                    RepairerGeometry.runeZ(frame.quarter, i), RUNE_HALF, RUNE_COLOR, floorLight(frame, i, true));
            drawn += disc(pose, buffer, RepairerGeometry.dotX(frame.quarter, i), RepairerGeometry.DOT_Y,
                    RepairerGeometry.dotZ(frame.quarter, i), DOT_HALF, RUNE_COLOR, floorLight(frame, i, false));
        }
        return drawn + RepairerBeams.render(pose, buffer, frame) + burst(pose, buffer, frame);
    }

    /**
     * O fim do reparo, por linha que anda entre 6,3 e 7 s: a onda de choque no chão em volta do pedestal (um anel
     * deitado que abre e some). Devolve quantos quads desenhou.
     */
    private static int burst(PoseStack.Pose pose, MultiBufferSource buffer, RepairFrame frame) {
        int drawn = 0;
        for (int line = 0; line < RepairMath.LINES; line++) {
            double s = frame.seconds[line];
            if (s < RepairTimeline.FLASH) {
                continue;
            }
            int shock = (int) Math.round(SHOCK_ALPHA * RepairTimeline.shockAlpha(s));
            if (shock >= 1) {
                RibbonQuad.flat(pose, buffer, RepairerGeometry.pedestalX(frame.quarter, line), RepairerGeometry.FLOOR_Y,
                        RepairerGeometry.pedestalZ(frame.quarter, line), SHOCK_HALF, BURST_COLOR, shock,
                        RibbonParam.MODE_DISC, RibbonParam.disc(RepairTimeline.shockRadius(s) / SHOCK_HALF, true,
                                SHOCK_WIDTH / SHOCK_HALF));
                drawn++;
            }
        }
        return drawn;
    }

    /** A luz da runa (ou do ponto) {@code i}: a de trabalho, mais a onda de cada linha que anda, até 1. */
    private static double floorLight(RepairFrame frame, int i, boolean rune) {
        double light = RepairTimeline.RUNES_WORKING;
        for (int line = 0; line < RepairMath.LINES; line++) {
            double s = frame.seconds[line];
            if (s >= 0) {
                double angle = rune ? RepairerGeometry.runeDistance(i, line) : RepairerGeometry.dotDistance(i, line);
                light = Math.max(light, RepairTimeline.RUNES_WORKING + RepairTimeline.runeWave(s, angle));
            }
        }
        return Math.min(1, light);
    }

    private static int disc(PoseStack.Pose pose, MultiBufferSource buffer, float x, float y, float z, float half,
            int rgb, double intensity) {
        if (intensity <= MIN_LIGHT) {
            return 0;
        }
        RibbonQuad.flat(pose, buffer, x, y, z, half, rgb, (int) Math.round(255 * Math.min(1, intensity)),
                RibbonParam.MODE_DISC, GLOW_DISC);
        return 1;
    }
}
