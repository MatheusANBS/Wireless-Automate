package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld;
import java.util.UUID;
import java.util.function.DoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * A câmera é o jogador voando (sem mão nem interface): {@link #saltar} o leva de verdade (servidor e cliente, para
 * carregar os chunks) e {@link #mover} o conduz por uma curva do tempo, sem passar pelo tick: a cada quadro, antes do
 * desenho, a pose da curva no instante de agora vai para o cliente com a posição e o ângulo antigos iguais aos novos,
 * então o desenho não interpola nada e o deslocamento é uniforme mesmo quando os ticks chegam em rajada.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
final class Camera {
    /** A altura dos olhos do jogador em pé. */
    private static final double OLHOS = 1.62;
    private static Integer campoAntes;
    private static DoubleFunction<double[][]> curva;
    private static long inicio;
    private static long duracao;

    private Camera() {
    }

    /** Leva a câmera ({@code olho}) até lá olhando para {@code alvo}, nos dois lados. */
    static void saltar(double[] olho, double[] alvo) throws Exception {
        curva = null;
        LocalPlayer jogador = Minecraft.getInstance().player;
        float[] angulos = angulos(olho, alvo);
        UUID id = jogador.getUUID();
        E2eWorld.onServer(servidor -> {
            ServerPlayer p = servidor.getPlayerList().getPlayer(id);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(p.serverLevel(), olho[0], olho[1] - OLHOS, olho[2], angulos[0], angulos[1]);
            return null;
        });
        olhar(olho, alvo);
        Minecraft.getInstance().options.hideGui = true;
    }

    /** Põe a câmera em {@code olho} olhando {@code alvo}, só no cliente, sem interpolação com a pose anterior. */
    static void olhar(double[] olho, double[] alvo) {
        LocalPlayer jogador = Minecraft.getInstance().player;
        float[] angulos = angulos(olho, alvo);
        jogador.setPos(olho[0], olho[1] - OLHOS, olho[2]);
        jogador.xo = jogador.xOld = olho[0];
        jogador.yo = jogador.yOld = olho[1] - OLHOS;
        jogador.zo = jogador.zOld = olho[2];
        jogador.setYRot(angulos[0]);
        jogador.setXRot(angulos[1]);
        jogador.yRotO = angulos[0];
        jogador.xRotO = angulos[1];
        jogador.setYHeadRot(angulos[0]);
        jogador.yHeadRotO = angulos[0];
        jogador.setDeltaMovement(0, 0, 0);
    }

    /** Começa a conduzir a câmera por {@code curva(t)} (t de 0 a 1 em {@code ms}, devolve {olho, alvo}); não espera. */
    static Step mover(long ms, DoubleFunction<double[][]> f) {
        return Passos.agir("câmera em movimento", () -> {
            curva = f;
            inicio = System.nanoTime();
            duracao = ms;
        });
    }

    @SubscribeEvent
    static void aoDesenhar(RenderFrameEvent.Pre evento) {
        if (curva != null && Minecraft.getInstance().player != null) {
            double[][] pose = curva.apply(Math.min(1, (System.nanoTime() - inicio) / 1e6 / duracao));
            olhar(pose[0], pose[1]);
        }
    }

    /** Muda o campo de visão (graus) sem gravar as opções; {@link #restaurarCampo()} devolve o de antes. */
    static void campo(int graus) {
        var opcao = Minecraft.getInstance().options.fov();
        if (campoAntes == null) {
            campoAntes = opcao.get();
        }
        opcao.set(graus);
    }

    static void restaurarCampo() {
        if (campoAntes != null) {
            Minecraft.getInstance().options.fov().set(campoAntes);
            campoAntes = null;
        }
    }

    /** A câmera chegou a {@code olho} (depois de um {@link #saltar}). */
    static boolean em(double[] olho) {
        return Minecraft.getInstance().player.getEyePosition().distanceToSqr(olho[0], olho[1], olho[2]) < 0.25;
    }

    /** Um ponto à distância {@code d} de {@code alvo}, no rumo {@code yaw} (graus, 0 = sul) e elevação {@code pitch}. */
    static double[] orbita(double[] alvo, double d, double yaw, double pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new double[] {alvo[0] + d * Math.cos(p) * Math.sin(y), alvo[1] + d * Math.sin(p),
                alvo[2] + d * Math.cos(p) * Math.cos(y)};
    }

    /** A mistura de {@code a} a {@code b} em {@code t} (de 0 a 1), ponto a ponto. */
    static double[] mistura(double[] a, double[] b, double t) {
        return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** A pose entre {@code de} e {@code para} (cada uma {olho, alvo}) em {@code t}, de começo e fim suaves. */
    static double[][] entre(double[][] de, double[][] para, double t) {
        double s = Passos.suave(t);
        return new double[][] {mistura(de[0], para[0], s), mistura(de[1], para[1], s)};
    }

    private static float[] angulos(double[] olho, double[] alvo) {
        double dx = alvo[0] - olho[0];
        double dy = alvo[1] - olho[1];
        double dz = alvo[2] - olho[2];
        return new float[] {(float) Math.toDegrees(Math.atan2(-dx, dz)),
                (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }
}
