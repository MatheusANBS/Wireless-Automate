package io.github.matheusanbs.wirelessautomate.client.repairer.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.CrystalMesh;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.RibbonParam;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.RibbonQuad;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * O feixe mágico do emissor até o rachado de cada linha que anda, pelo modo 1 do {@code resonator_ribbon}: três fios de
 * plasma em espiral em volta do eixo, com contas de luz correndo para o cristal, numa fita virada para a câmera, da
 * borda da esfera do emissor ao meio do cristal; a semente da linha dá a cada feixe o seu desenho. Acende de 1,0 a
 * 1,3 s, abre, engrossa e vibra na convergência e corta no clarão, pela {@link RepairTimeline}.
 */
final class RepairerBeams {
    /** A largura de base da fita, em pixels do modelo: a espiral ocupa pouco mais da metade, o resto é o brilho. */
    private static final float WIDTH = 2.4f;
    private static final int COLOR = 0xFF9664;
    private static final double MIN_BEAM = 0.02;
    /** Onde o feixe bate no cristal, acima da origem da malha, em unidades da malha. */
    private static final float HIT = 0.06f;

    private RepairerBeams() {
    }

    /** Desenha os feixes e devolve quantos. */
    static int render(PoseStack.Pose pose, MultiBufferSource buffer, RepairFrame frame) {
        int drawn = 0;
        float ex = RepairerGeometry.CENTER;
        float ey = RepairerGeometry.EMITTER_Y;
        float ez = RepairerGeometry.CENTER;
        for (int line = 0; line < RepairMath.LINES; line++) {
            double s = frame.seconds[line];
            double beam = s < 0 ? 0 : RepairTimeline.beam(s);
            if (beam <= MIN_BEAM) {
                continue;
            }
            float bx = RepairerGeometry.pedestalX(frame.quarter, line);
            float by = RepairerGeometry.PEDESTAL_TOP_Y + (HIT - CrystalMesh.BOTTOM) * RepairerCrystals.MAIN;
            float bz = RepairerGeometry.pedestalZ(frame.quarter, line);
            float dx = bx - ex;
            float dy = by - ey;
            float dz = bz - ez;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float k = RepairerGeometry.EMITTER_RADIUS / length;
            float ax = ex + dx * k;
            float ay = ey + dy * k;
            float az = ez + dz * k;
            float width = (float) (WIDTH * RepairTimeline.beamWidth(s));
            int param = RibbonParam.weave(2 * line + 1, RepairTimeline.beamSurge(s), 0.7 * length * (1 - k) / width);
            if (RibbonQuad.strip(pose, buffer, ax, ay, az, bx, by, bz, frame.ex, frame.ey, frame.ez, width, COLOR,
                    (int) Math.round(255 * beam), RibbonParam.MODE_WEAVE, param)) {
                drawn++;
            }
        }
        return drawn;
    }
}
