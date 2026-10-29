package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * O GIF da parede de Telas tocando ({@code gif/tela-NNN.png}, montado pelo {@code scripts/curseforge/gerar_gif.py}).
 * As animações da Tela andam pelo relógio do cliente ({@code Util.getMillis}), não pelos ticks, e uma captura da
 * vitrine leva perto de um segundo: em tempo real os quadros sairiam salteados. Então o relógio do jogo vira um
 * relógio virtual ({@code Util.timeSource}) que esta classe segura em cada instante do roteiro: com ele parado, os
 * ticks do cliente não andam, o quadro seguinte desenha a Tela naquele instante e a captura sai no fim dele
 * ({@link RenderFrameEvent.Post}). As outras threads (o servidor integrado, a rede) veem o relógio parado no começo da
 * gravação: o servidor não dá nenhum tick, então o painel fica nos números do começo (nem o guardado nem as taxas
 * mudam) e o último quadro emenda no primeiro. Depois do último quadro o relógio volta a correr, contínuo a partir do
 * instante seguro (nunca volta atrás).
 *
 * <p>O laço é o da pixel art (12 quadros a 8 q/s, 1,5 s), e o GIF de cor livre tem os mesmos 1,5 s: {@value #FRAMES}
 * quadros a cada 62,5 ms, a 31 ms do começo de cada um, com o zero num múltiplo do laço (o mesmo zero dos relógios da
 * Tela).
 */
final class E2eShowcaseTvGif {
    static final int FRAMES = 24;
    private static final long LOOP_MS = 1500;
    private static final long OFFSET_NS = 31_000_000L;
    /** Quadros desenhados com o relógio no instante novo antes da captura: o redesenho da Tela e a troca do GIF. */
    private static final int SETTLE_FRAMES = 3;

    /** O instante virtual seguro, em ns, ou -1 com o relógio correndo. */
    private static volatile long held = -1;
    /** Com o relógio correndo, o virtual é {@code System.nanoTime() - offset}. */
    private static volatile long offset;
    /** O instante que as threads fora a do jogo veem durante a gravação, ou -1 fora dela. */
    private static volatile long others = -1;
    private static Thread render;
    private static boolean installed;
    private static boolean recording;
    private static boolean done;
    private static int frame;
    private static int settle;
    private static long baseNs;

    private E2eShowcaseTvGif() {
    }

    /** O passo da câmera (centrada nas três Telas, um pouco mais perto que a foto parada) e o da gravação. */
    static void steps(List<Step> list, double frontZ, double midY) {
        list.add(E2eShowcase.showLook("Tela (GIF): câmera na parede", 1.0, 1.4, 9.0, 1.0, midY, frontZ, false));
        list.add(DevEndToEnd.wait("Tela (GIF): assentar", 40));
        list.add(new Step("Tela (GIF): gravar " + FRAMES + " quadros com o relógio virtual", 300_000,
                E2eShowcaseTvGif::start, () -> done, () -> "quadro " + frame + " de " + FRAMES));
    }

    private static void start() throws IOException {
        clearFrames();
        Minecraft.getInstance().mouseHandler.releaseMouse();
        if (!installed) {
            installed = true;
            offset = 0;
            Util.timeSource = E2eShowcaseTvGif::now;
            NeoForge.EVENT_BUS.addListener(E2eShowcaseTvGif::onFrame);
        }
        render = Thread.currentThread();
        others = now();
        long nowMs = Util.getMillis();
        baseNs = (nowMs / LOOP_MS + 2) * LOOP_MS * 1_000_000L;
        frame = 0;
        done = false;
        hold(target(0));
        settle = SETTLE_FRAMES;
        recording = true;
        WirelessAutomate.LOGGER.info("vitrine: GIF da parede com o relógio virtual, {} quadros", FRAMES);
    }

    private static long now() {
        long o = others;
        if (o >= 0 && Thread.currentThread() != render) {
            return o;
        }
        long h = held;
        return h >= 0 ? h : System.nanoTime() - offset;
    }

    private static long target(int index) {
        return baseNs + LOOP_MS * 1_000_000L * index / FRAMES + OFFSET_NS;
    }

    private static void hold(long virtualNs) {
        held = Math.max(virtualNs, now());
    }

    /** Solta o relógio, que segue do instante seguro. */
    private static void release() {
        long h = held;
        offset = System.nanoTime() - h;
        held = -1;
        others = -1;
    }

    private static void onFrame(RenderFrameEvent.Post event) {
        if (!recording) {
            return;
        }
        if (settle > 0) {
            settle--;
            return;
        }
        capture(Minecraft.getInstance(), String.format("gif/tela-%03d", frame));
        frame++;
        if (frame >= FRAMES) {
            recording = false;
            release();
            done = true;
            return;
        }
        hold(target(frame));
        settle = SETTLE_FRAMES;
    }

    /** Apaga os quadros de uma rodada anterior, para o script não misturar. */
    private static void clearFrames() throws IOException {
        Path dir = Path.of(SHOWCASE, "gif");
        Files.createDirectories(dir);
        try (DirectoryStream<Path> old = Files.newDirectoryStream(dir, "tela-*.png")) {
            for (Path path : old) {
                Files.delete(path);
            }
        }
    }
}
