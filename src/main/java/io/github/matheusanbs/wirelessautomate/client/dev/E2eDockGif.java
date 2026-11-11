package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.dock.render.DockClock;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A gravação dos dois laços da Doca quadro a quadro ({@code doca-gif-<laço>-NNN}), com o tempo avançando um tick por
 * quadro. A pose da Doca vem parada de cada quadro pelo {@link DockClock}, mas o circuito dos painéis é textura
 * animada, que anda pelos ticks do cliente: em tempo real uma captura leva vários ticks e o circuito sairia salteado.
 * Então o relógio do jogo vira um relógio virtual ({@code Util.timeSource}), como no {@link E2eShowcaseTvGif}: seguro
 * em cada instante, 50 ms depois do anterior, e o quadro sai no fim do desenho ({@link RenderFrameEvent.Post}). Cada
 * quadro é um tick: os 120 de um laço são 6 s e duas voltas do circuito (3 s), e o GIF emenda. As outras threads (o
 * servidor integrado) veem o relógio parado no começo. No fim o relógio volta a correr a partir do instante seguro.
 */
final class E2eDockGif {
    /** Os quadros de cada laço: 6 s a 20 por segundo. */
    static final int FRAMES = 120;
    private static final long TICK_NS = 50_000_000L;
    /** O meio do tick: o quadro desenha com o tick novo já dado. */
    private static final long OFFSET_NS = 25_000_000L;
    private static final int SETTLE_FRAMES = 3;

    private static volatile long held = -1;
    private static volatile long offset;
    private static volatile long others = -1;
    private static Thread render;
    private static boolean installed;
    private static boolean recording;
    private static boolean done;
    private static int frame;
    private static int settle;
    private static long baseNs;
    private static float[] angle;

    private E2eDockGif() {
    }

    /** O passo que grava os dois laços, com a câmera no ângulo {@code ang} (reaplicado a cada quadro). */
    static Step record(float[] ang) {
        return new Step("Doca GIF: gravar " + 2 * FRAMES + " quadros com o relógio virtual", 600_000, () -> {
            angle = ang;
            start();
        }, () -> done, () -> "quadro " + frame + " de " + 2 * FRAMES);
    }

    private static void start() {
        Minecraft.getInstance().mouseHandler.releaseMouse();
        if (!installed) {
            installed = true;
            offset = 0;
            Util.timeSource = E2eDockGif::now;
            NeoForge.EVENT_BUS.addListener(E2eDockGif::onFrame);
        }
        render = Thread.currentThread();
        others = now();
        baseNs = (now() / TICK_NS + 2) * TICK_NS;
        frame = 0;
        done = false;
        pose(0);
        hold(baseNs + OFFSET_NS);
        settle = SETTLE_FRAMES;
        recording = true;
        WirelessAutomate.LOGGER.info("Doca: GIF com o relógio virtual, {} quadros", 2 * FRAMES);
    }

    private static void pose(int index) {
        DockClock.workForTests = index >= FRAMES;
        DockClock.freezeForTests = (index % FRAMES) * 0.05;
    }

    private static String name(int index) {
        return String.format(Locale.ROOT, "doca-gif-%s-%03d", index >= FRAMES ? "trabalho" : "repouso",
                index % FRAMES);
    }

    private static long now() {
        long o = others;
        if (o >= 0 && Thread.currentThread() != render) {
            return o;
        }
        long h = held;
        return h >= 0 ? h : System.nanoTime() - offset;
    }

    private static void hold(long virtualNs) {
        held = Math.max(virtualNs, now());
    }

    /** Solta o relógio, que segue do instante seguro. */
    static void release() {
        if (held < 0) {
            return;
        }
        long h = held;
        offset = System.nanoTime() - h;
        held = -1;
        others = -1;
        recording = false;
    }

    private static void onFrame(RenderFrameEvent.Post event) {
        if (!recording) {
            return;
        }
        E2eRepairerSteps.look(angle);
        if (settle > 0) {
            settle--;
            return;
        }
        capture(Minecraft.getInstance(), name(frame));
        frame++;
        if (frame >= 2 * FRAMES) {
            release();
            done = true;
            return;
        }
        pose(frame);
        hold(baseNs + frame * TICK_NS + OFFSET_NS);
        settle = SETTLE_FRAMES;
    }
}
