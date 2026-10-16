package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * As faíscas da câmara, do centro até a Porta de Energia, desenhadas no passe do renderer (que aparece atrás do
 * Vidro): um rastro curto e fino em zigue-zague, aditivo, e na ponta o halo dourado com um miolo quase branco,
 * virados para a câmera. Cada faísca acende nos primeiros ticks, apaga nos últimos e cintila por tick, pela semente
 * dela; o caminho e o tempo vêm do {@link ChamberParticles}.
 */
final class ChamberSparks {
    private static final int GOLD = 0xFFD042;
    private static final int CORE = 0xFFF6D8;
    private static final double HALO_SIZE = 0.2;
    private static final double CORE_SIZE = 0.07;
    private static final float TRAIL_HALF = 0.009f;
    private static final int TRAIL_POINTS = 3;
    private static final double TRAIL_STEP = 0.055;
    private static final float ZIGZAG = 0.045f;
    private static final double FADE_IN_TICKS = 3;
    private static final double FADE_OUT_TICKS = 6;
    /** Rascunhos do desenho, que é só na thread de render: nada alocado por quadro. */
    private static final float[] HEAD = new float[3];
    private static final float[] FROM = new float[3];
    private static final float[] TO = new float[3];

    private ChamberSparks() {
    }

    static void render(PoseStack poseStack, MultiBufferSource buffer, ChamberScene scene, float partialTick) {
        ChamberParticles.Spark[] sparks = scene.sparks();
        long tick = scene.lastUsed;
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer trail = null;
        for (ChamberParticles.Spark spark : sparks) {
            double alpha = alpha(spark, partialTick, tick);
            if (alpha <= 0) {
                continue;
            }
            if (trail == null) {
                trail = buffer.getBuffer(RenderType.lightning());
            }
            drawTrail(pose, trail, spark, spark.at(partialTick), tick, alpha);
        }
        VertexConsumer glow = null;
        for (ChamberParticles.Spark spark : sparks) {
            double alpha = alpha(spark, partialTick, tick);
            if (alpha <= 0) {
                continue;
            }
            if (glow == null) {
                glow = buffer.getBuffer(ChamberRenderTypes.glow());
            }
            double flicker = flicker(spark.seed, tick);
            spark.point(spark.at(partialTick), HEAD);
            ChamberLens.halo(pose, glow, HEAD[0], HEAD[1], HEAD[2], HALO_SIZE * (0.8 + 0.4 * flicker), GOLD,
                    (float) (alpha * 0.95));
            ChamberLens.halo(pose, glow, HEAD[0], HEAD[1], HEAD[2], CORE_SIZE, CORE, (float) alpha);
        }
    }

    /** O alfa da faísca agora: 0 se morta, subindo nos primeiros ticks, caindo nos últimos e cintilando. */
    private static double alpha(ChamberParticles.Spark spark, float partialTick, long tick) {
        if (!spark.alive) {
            return 0;
        }
        double age = spark.at(partialTick) * ChamberParticles.SPARK_TICKS;
        double fade = Math.min(1, age / FADE_IN_TICKS)
                * Math.min(1, (ChamberParticles.SPARK_TICKS - age) / FADE_OUT_TICKS);
        return Math.max(0, fade) * (0.55 + 0.45 * flicker(spark.seed, tick));
    }

    /**
     * O rastro: a ponta e {@code TRAIL_POINTS} pontos para trás no caminho, cada um desviado para um lado e para o
     * outro (mais longe da ponta, mais desvio), com o alfa caindo até zero na cauda.
     */
    private static void drawTrail(PoseStack.Pose pose, VertexConsumer out, ChamberParticles.Spark spark, double at,
            long tick, double alpha) {
        spark.point(at, FROM);
        int alphaA = (int) Math.round(255 * alpha * 0.85);
        for (int k = 1; k <= TRAIL_POINTS; k++) {
            spark.point(Math.max(0, at - k * TRAIL_STEP), TO);
            float sign = k % 2 == 1 ? 1 : -1;
            float reach = ZIGZAG * k / TRAIL_POINTS * (0.6f + 0.8f * (float) flicker(spark.seed + k, tick));
            float tilt = (float) flicker(spark.seed - k, tick) * 2 - 1;
            for (int c = 0; c < 3; c++) {
                TO[c] += sign * reach * (spark.side[c] + tilt * spark.up[c]);
            }
            int alphaB = (int) Math.round(255 * alpha * 0.85 * (TRAIL_POINTS - k) / TRAIL_POINTS);
            LissajousTrace.segment(pose, out, FROM, TO, TRAIL_HALF, GOLD, alphaA, alphaB);
            System.arraycopy(TO, 0, FROM, 0, 3);
            alphaA = alphaB;
        }
    }

    /** Um número de 0 a 1 fixo por semente e tick: a cintilação sem {@code Random} por quadro. */
    private static double flicker(int seed, long tick) {
        int h = seed * 0x9E3779B1 ^ (int) tick * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / (double) (1 << 24);
    }
}
