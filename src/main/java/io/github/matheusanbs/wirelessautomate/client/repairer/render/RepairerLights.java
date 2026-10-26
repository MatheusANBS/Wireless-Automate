package io.github.matheusanbs.wirelessautomate.client.repairer.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.PlasmaCore;
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
}
