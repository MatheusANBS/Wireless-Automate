package io.github.matheusanbs.wirelessautomate.client.repairer.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.CrystalGem;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.CrystalMesh;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.CrystalParam;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerView;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Quaternionf;

/**
 * Os cristais de pé nos pedestais, um por linha com cristal, pela gema facetada do shader do cristal do Ressonador: o
 * losango no Lapidado e nos Afinados (a faixa dourada ou na cor do tier) e o aglomerado no Cristal de Sinal. O da linha
 * travada (terminou e espera a saída) já está reparado; o da linha que anda fecha as rachaduras pelo canal de efeito
 * do shader até a convergência e dali em diante já é o reparado; o que espera fica rachado, com as rachaduras acesas
 * se há energia. Cada um tem um giro fixo, o do pedestal, para os oito não mostrarem a mesma faceta.
 */
final class RepairerCrystals {
    /** Pixels do modelo por unidade da malha: ~2,7 px de altura. */
    static final float MAIN = 2.6f;
    /** O dourado da faixa do Lapidado, o dos sprites. */
    private static final int GOLD = 0xDDBC5C;
    /** Os tiers por posição, sem copiar o array do enum a cada cristal. */
    private static final RouterTier[] TIERS = RouterTier.values();
    /** A origem do cristal que {@link #center} calcula, rascunho do desenho. */
    private static final float[] CENTER = new float[3];
    /** A subida do reparado na saída, em pixels do modelo. */
    private static final double EXIT_RISE = 1.5;
    /** O clarão do próprio cristal no pico: o teto de quanto ele vai ao creme, para não estourar em branco. */
    private static final double GLOW_MAX = 0.25;
    private static final Quaternionf SPIN = new Quaternionf();

    private RepairerCrystals() {
    }

    /**
     * A origem da malha do cristal da linha, em pixels do modelo: o pedestal, com a subida da saída do reparado; {@code s} negativo é o cristal parado. O fim do feixe também a usa.
     */
    static void center(RepairFrame frame, int line, double s, float[] out) {
        float x = RepairerGeometry.pedestalX(frame.quarter, line);
        float y = RepairerGeometry.PEDESTAL_TOP_Y;
        float z = RepairerGeometry.pedestalZ(frame.quarter, line);
        if (s >= 0) {
            y += (float) (EXIT_RISE * RepairTimeline.exit(s));
        }
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }

    /** Desenha os cristais e devolve quantos. */
    static int render(PoseStack poseStack, MultiBufferSource buffer, RepairerView view, RepairFrame frame) {
        int drawn = 0;
        for (int line = 0; line < RepairMath.LINES; line++) {
            byte kind = view.kind(line);
            if (kind == RepairerView.NONE) {
                continue;
            }
            double s = frame.seconds[line];
            boolean moving = s >= 0 && !view.blocked(line);
            center(frame, line, moving ? s : -1, CENTER);
            double leave = moving ? RepairTimeline.exit(s) : 0;
            float scale = (float) (MAIN * (1 - leave));
            poseStack.pushPose();
            poseStack.translate(CENTER[0], CENTER[1] - CrystalMesh.BOTTOM * scale, CENTER[2]);
            poseStack.mulPose(SPIN.rotationY((float) Math.toRadians(
                    -(22.5 + 45.0 * line + 90.0 * frame.quarter) + 720 * leave)));
            poseStack.scale(scale, scale, scale);
            int state;
            float fx;
            int glow = 0;
            if (view.blocked(line) || s >= RepairTimeline.CONVERGE) {
                state = CrystalParam.LIVE;
                fx = 0f;
                glow = moving ? (int) Math.round(255 * GLOW_MAX * RepairTimeline.crystalGlow(s)) : 0;
            } else {
                state = CrystalParam.CRACKED;
                fx = CrystalParam.repairing(s < 0 ? 0 : RepairTimeline.cracks(s), s >= 0 || frame.lit);
            }
            CrystalGem.render(poseStack.last(), buffer, bandColor(kind), glow, state, line,
                    kind != RepairerView.SIGNAL, fx);
            poseStack.popPose();
            drawn++;
        }
        return drawn;
    }

    /** A faixa do losango: o dourado do Lapidado ou a cor do tier do Afinado; o aglomerado não tem faixa. */
    private static int bandColor(byte kind) {
        if (kind >= RepairerView.TUNED) {
            int tier = kind - RepairerView.TUNED;
            return tier < TIERS.length ? GuiPaint.tierColor(TIERS[tier]) : GOLD;
        }
        return GOLD;
    }
}
