package io.github.matheusanbs.wirelessautomate.client.repairer.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.PlasmaCore;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * As luzes do Reparador desenhadas pelo BER. O emissor é o plasma do Núcleo do Ressonador ({@link PlasmaCore}) em
 * miniatura, pendurado no centro do vidro: o meio lado do quad deixa a esfera do shader com ~1,6 px de raio, a largura
 * do {@code emitter_glow} do modelo, e o brilho da {@link RepairTimeline} vira o apagamento do shader.
 */
final class RepairerLights {
    private static final float EMITTER_HALF = 6.9f;
    /** O coral do emissor no mockup aprovado. */
    private static final int EMITTER_COLOR = 0xF0603E;

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
