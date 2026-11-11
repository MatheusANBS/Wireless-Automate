package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.dock.render.DockClock;
import io.github.matheusanbs.wirelessautomate.dock.DockWork;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A gravação dos dois laços da Doca quadro a quadro ({@code doca-gif-<laço>-NNN}), com o tempo avançando um tick por
 * quadro, e depois 12 s de trabalho contínuo ({@code doca-gif-continuo-NNN}): o tempo solto, pelo mesmo caminho do
 * jogo ({@code DockWork}), com um evento a cada 100 ticks (o intervalo do servidor), aplicado no cliente quando o tempo
 * chega nele. O primeiro chega 20 ticks antes do começo do primeiro laço gravado e os outros no meio dos laços, com o
 * lado alternado: os dois laços saem inteiros, e o segundo com o pacote no lado do evento do meio do primeiro. A pose da Doca vem parada de cada quadro pelo {@link DockClock}, mas o circuito dos painéis é textura
 * animada, que anda pelos ticks do cliente: em tempo real uma captura leva vários ticks e o circuito sairia salteado.
 * Então o relógio do jogo vira um relógio virtual ({@code Util.timeSource}), como no {@link E2eShowcaseTvGif}: seguro
 * em cada instante, 50 ms depois do anterior, e o quadro sai no fim do desenho ({@link RenderFrameEvent.Post}). Cada
 * quadro é um tick: os 120 de um laço são 6 s e duas voltas do circuito (3 s), e o GIF emenda. As outras threads (o
 * servidor integrado) veem o relógio parado no começo. No fim o relógio volta a correr a partir do instante seguro.
 */
final class E2eDockGif {
    /** Os quadros de cada laço: 6 s a 20 por segundo. */
    static final int FRAMES = 120;
    /** Os quadros do trabalho contínuo: dois laços. */
    static final int CONTINUOUS = 2 * FRAMES;
    private static final int[] SIDES = {Direction.EAST.ordinal(), Direction.NORTH.ordinal()};
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
    private static Supplier<DockWork> work;
    /** Os ticks dados desde o começo (um por quadro, também os sem captura) e, no contínuo, o começo do 1º laço. */
    private static int ticks;
    private static long loopAt = -1;
    private static int nextEvent;

    private E2eDockGif() {
    }

    /**
     * O passo que grava os dois laços e o trabalho contínuo, com a câmera no ângulo {@code ang} (reaplicado a cada
     * quadro) e o relógio de trabalho da Doca no cliente.
     */
    static Step record(float[] ang, Supplier<DockWork> dock) {
        return new Step("Doca GIF: gravar " + total() + " quadros com o relógio virtual", 900_000, () -> {
            angle = ang;
            work = dock;
            start();
        }, () -> done, () -> "quadro " + frame + " de " + total());
    }

    private static int total() {
        return 2 * FRAMES + CONTINUOUS;
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
        ticks = 0;
        loopAt = -1;
        nextEvent = 0;
        done = false;
        pose(0);
        hold(baseNs + OFFSET_NS);
        settle = SETTLE_FRAMES;
        recording = true;
        WirelessAutomate.LOGGER.info("Doca: GIF com o relógio virtual, {} quadros", total());
    }

    private static void pose(int index) {
        if (index >= 2 * FRAMES) {
            DockClock.release();
            return;
        }
        DockClock.workForTests = index >= FRAMES;
        DockClock.freezeForTests = (index % FRAMES) * 0.05;
    }

    private static String name(int index) {
        if (index >= 2 * FRAMES) {
            return String.format(Locale.ROOT, "doca-gif-continuo-%03d", index - 2 * FRAMES);
        }
        return String.format(Locale.ROOT, "doca-gif-%s-%03d", index >= FRAMES ? "trabalho" : "repouso",
                index % FRAMES);
    }

    /**
     * No trabalho contínuo: marca o começo do primeiro laço gravado (o seguinte a 40 ticks de agora), dá os eventos
     * que chegaram ({@code começo − 20 + 100 × n}) e diz se o quadro de agora é gravado (do começo em diante).
     */
    private static boolean continuous() {
        long now = Minecraft.getInstance().level.getGameTime();
        if (loopAt < 0) {
            loopAt = DockWork.loopStart(now + 40) + DockWork.LOOP_TICKS;
        }
        long at = loopAt - 20 + (long) nextEvent * DockWork.EVENT_INTERVAL;
        if (now >= at && at < loopAt + CONTINUOUS) {
            work.get().event(at, SIDES[nextEvent % 2]);
            WirelessAutomate.LOGGER.info("Doca GIF: evento {} no tick {} (laço de {})", nextEvent, at,
                    DockWork.loopStart(at));
            nextEvent++;
        }
        return now >= loopAt;
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
        if (frame < 2 * FRAMES || continuous()) {
            capture(Minecraft.getInstance(), name(frame));
            frame++;
        }
        if (frame >= total()) {
            release();
            done = true;
            return;
        }
        pose(frame);
        ticks++;
        hold(baseNs + ticks * TICK_NS + OFFSET_NS);
        settle = SETTLE_FRAMES;
    }
}
