package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Os cristais em anel na câmara, pelo modelo do item, e os feixes de cada cristal vivo até a cabeça da figura. Em
 * sintonia sobem e descem juntos; fora dela, cada um no seu ritmo e balançando. O Opaco fica caído e cinza (o
 * modelo Opaco do item) e volta à altura com a recarga; só o primeiro Opaco recarrega, como no servidor.
 */
final class ChamberCrystals {
    private static final double RADIUS = 0.95;
    private static final double RING_SPEED = 0.08;
    private static final double BOB = 0.12;
    private static final double BOB_SECONDS = 2.4;
    private static final double BASE_Y = -0.15;
    private static final double DULL_DROP = 0.35;
    private static final double DULL_TILT = 0.5;
    private static final double SWAY = 0.25;
    private static final float SCALE = 0.55f;
    private static final double CRYSTAL_DELAY = 0.6;
    private static final double BEAM_DELAY = 1.2;
    private static final double PULSE_SECONDS = 1.6;
    private static final float BEAM_HALF = 0.009f;
    private static final double TICK_SECONDS = 0.05;
    private static final byte EMPTY = 0;
    private static final byte DULL_SIGNAL = 3;
    /** Uma pilha por código da câmara: vazia, Cristal, Lapidado e os dois Opacos. */
    private static ItemStack[] stacks;

    private ChamberCrystals() {
    }

    static void render(PoseStack poseStack, MultiBufferSource buffer, ChamberScene scene, long gameTime,
            float partialTick, ClientLevel level) {
        byte[] chambers = scene.chambers();
        double since = scene.sinceFormed(gameTime, partialTick);
        float scale = (float) (SCALE * ChamberScene.on(since, CRYSTAL_DELAY));
        if (scale <= 0) {
            return;
        }
        ItemStack[] items = stacks();
        double ticks = gameTime + partialTick;
        double t = ticks * TICK_SECONDS;
        double q = scene.q();
        ChamberView view = scene.view();
        for (int i = 0; i < chambers.length; i++) {
            if (chambers[i] == EMPTY) {
                continue;
            }
            boolean dull = isDull(chambers[i]);
            double[] at = crystalPos(i, chambers, q, ticks, view);
            double seed = seed(i);
            double fallen = 1 - smoothstep(recharge(i, chambers, ticks, view));
            poseStack.pushPose();
            poseStack.translate(at[0], at[1], at[2]);
            poseStack.mulPose(Axis.YP.rotation((float) (scene.spin(dull, partialTick) + seed)));
            poseStack.mulPose(Axis.ZP.rotation((float) (dull ? DULL_TILT * fallen
                    : Math.sin(3 * t + seed) * SWAY * (1 - q))));
            poseStack.scale(scale, scale, scale);
            Minecraft.getInstance().getItemRenderer().renderStatic(items[chambers[i]], ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, buffer, level, 0);
            poseStack.popPose();
        }
        beams(poseStack.last(), buffer, scene, chambers, since, ticks, partialTick);
    }

    /** Os feixes dos cristais vivos até a cabeça, só com a sintonia acima da metade. */
    private static void beams(PoseStack.Pose pose, MultiBufferSource buffer, ChamberScene scene, byte[] chambers,
            double since, double ticks, float partialTick) {
        double q = scene.q();
        double strength = ChamberScene.on(since, BEAM_DELAY) * Math.max(0, q - 0.5) * 2;
        if (strength <= 0) {
            return;
        }
        double t = ticks * TICK_SECONDS;
        double[] head = scene.headPoint(partialTick);
        float[] to = {(float) head[0], (float) head[1], (float) head[2]};
        float[] from = new float[3];
        VertexConsumer out = null;
        for (int i = 0; i < chambers.length; i++) {
            if (chambers[i] == EMPTY || isDull(chambers[i])) {
                continue;
            }
            double pulse = 0.5 + 0.5 * Math.sin(2 * Math.PI * t / PULSE_SECONDS + (q > 0.9 ? 0 : seed(i)));
            int alpha = (int) Math.round(255 * strength * (0.5 + 0.5 * pulse) * 0.7);
            if (alpha <= 0) {
                continue;
            }
            double[] at = crystalPos(i, chambers, q, ticks, scene.view());
            from[0] = (float) at[0];
            from[1] = (float) at[1];
            from[2] = (float) at[2];
            if (out == null) {
                out = buffer.getBuffer(RenderType.lightning());
            }
            LissajousTrace.segment(pose, out, from, to, BEAM_HALF, LissajousTrace.CORAL_LIGHT, alpha, alpha);
        }
    }

    /**
     * Onde fica o cristal da câmara {@code i}, relativo ao centro, no tempo {@code ticks} (com a fração do tick):
     * no anel, girando devagar, subindo e descendo com a sintonia; o Opaco mais baixo até a recarga terminar.
     */
    static double[] crystalPos(int i, byte[] chambers, double q, double ticks, ChamberView view) {
        double t = ticks * TICK_SECONDS;
        double a = 2 * Math.PI * i / chambers.length + RING_SPEED * t + Math.PI / 4;
        double phase = seed(i) * (1 - q);
        double bob = Math.sin(2 * Math.PI * t / BOB_SECONDS + phase) * BOB * q;
        double rise = isDull(chambers[i]) ? -DULL_DROP * (1 - smoothstep(recharge(i, chambers, ticks, view))) : 0;
        return new double[] {Math.cos(a) * RADIUS, bob + rise + BASE_Y, Math.sin(a) * RADIUS};
    }

    /** A câmara do Opaco que recarrega agora (o primeiro, como no servidor), ou -1. */
    static int recharging(byte[] chambers, ChamberView view) {
        if (view.rechargeStart() < 0) {
            return -1;
        }
        for (int i = 0; i < chambers.length; i++) {
            if (isDull(chambers[i])) {
                return i;
            }
        }
        return -1;
    }

    /** De 0 a 1, o quanto a recarga da câmara {@code i} já andou; 0 se ela não recarrega. */
    private static double recharge(int i, byte[] chambers, double ticks, ChamberView view) {
        if (recharging(chambers, view) != i) {
            return 0;
        }
        return Math.clamp((ticks - view.rechargeStart()) / view.rechargeTicks(), 0.0, 1.0);
    }

    static boolean isDull(byte code) {
        return code >= DULL_SIGNAL;
    }

    /** O desencontro fixo de cada câmara (o ângulo áureo), para os cristais não andarem juntos fora de sintonia. */
    static double seed(int i) {
        return (0.7 + i * 2.399963) % (2 * Math.PI);
    }

    static double smoothstep(double x) {
        double c = Math.clamp(x, 0.0, 1.0);
        return c * c * (3 - 2 * c);
    }

    private static ItemStack[] stacks() {
        if (stacks == null) {
            ItemStack signal = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
            ItemStack cut = new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get());
            ItemStack dullSignal = signal.copy();
            SignalCrystalItem.setCharge(dullSignal, 0);
            ItemStack dullCut = cut.copy();
            SignalCrystalItem.setCharge(dullCut, 0);
            stacks = new ItemStack[] {ItemStack.EMPTY, signal, cut, dullSignal, dullCut};
        }
        return stacks;
    }
}
