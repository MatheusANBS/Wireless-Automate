package io.github.matheusanbs.wirelessautomate.client.repairer.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.CrystalGem;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.CrystalMesh;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.CrystalParam;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerView;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Os cristais de pé nos pedestais, um por linha com cristal, pela gema facetada do shader do cristal do Ressonador: o
 * losango no Lapidado e nos Afinados (a faixa dourada ou na cor do tier) e o aglomerado no Cristal de Sinal. O da linha
 * travada (terminou e espera a saída) já está reparado; os outros, rachados. Cada um tem um giro fixo, o do pedestal,
 * para os oito não mostrarem a mesma faceta.
 */
final class RepairerCrystals {
    /** Pixels do modelo por unidade da malha: ~2,7 px de altura. */
    static final float MAIN = 2.6f;
    /** O dourado da faixa do Lapidado, o dos sprites. */
    private static final int GOLD = 0xDDBC5C;
    /** Os tiers por posição, sem copiar o array do enum a cada cristal. */
    private static final RouterTier[] TIERS = RouterTier.values();

    private RepairerCrystals() {
    }

    /** Desenha os cristais e devolve quantos. */
    static int render(PoseStack poseStack, MultiBufferSource buffer, RepairerView view, RepairFrame frame) {
        int drawn = 0;
        for (int line = 0; line < RepairMath.LINES; line++) {
            byte kind = view.kind(line);
            if (kind == RepairerView.NONE) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(RepairerGeometry.pedestalX(frame.quarter, line),
                    RepairerGeometry.PEDESTAL_TOP_Y - CrystalMesh.BOTTOM * MAIN,
                    RepairerGeometry.pedestalZ(frame.quarter, line));
            poseStack.mulPose(Axis.YP.rotationDegrees(-(22.5f + 45f * line + 90f * frame.quarter)));
            poseStack.scale(MAIN, MAIN, MAIN);
            int state = view.blocked(line) ? CrystalParam.LIVE : CrystalParam.CRACKED;
            CrystalGem.render(poseStack.last(), buffer, bandColor(kind), 0, state, line, kind != RepairerView.SIGNAL,
                    0f);
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
