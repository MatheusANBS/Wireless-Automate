package io.github.matheusanbs.wirelessautomate.client.dock.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.matheusanbs.wirelessautomate.dock.DockWork;
import io.github.matheusanbs.wirelessautomate.dock.WorkFeed;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * A pose da Doca contra os keyframes do {@code docs/preview/doca-de-padroes.bbmodel} (cada amostra das duas
 * animações), os laços que emendam e a rampa sem pulo de ângulo.
 */
class DockPoseTest {
    private static final Path BBMODEL = Path.of("docs/preview/doca-de-padroes.bbmodel");
    private static final double EPS = 2e-3;
    private static final DockPose POSE = DockPose.INSTANCE;

    @Test
    void bateComOsKeyframesDoTrabalho() throws IOException {
        JsonObject anim = animation("animation.doca.trabalhando");
        check(anim, "nucleo", "scale", 0, s -> DockTimeline.crystalScaleWork(s));
        check(anim, "nucleo", "position", 1, s -> DockTimeline.crystalLift(s, 1));
        check(anim, "anel_h", "rotation", 0, s -> DockTimeline.brassTilt(s, 1));
        check(anim, "pacote", "position", 0, DockTimeline::packetOffset);
        check(anim, "pacote", "scale", 0, DockTimeline::packetScale);
        check(anim, "sat_0", "position", 0, s -> DockTimeline.fragmentRadius(s) - DockTimeline.FRAGMENT_ORBIT);
        check(anim, "sat_2", "position", 2, s -> DockTimeline.fragmentRadius(s) - DockTimeline.FRAGMENT_ORBIT);
        for (String sat : new String[] {"sat_0", "sat_1", "sat_2", "sat_3"}) {
            check(anim, sat, "scale", 0, DockTimeline::fragmentScale);
        }
        spin(anim, "anel_h", 1, true, () -> POSE.brassSpin);
        spin(anim, "anel_v", 1, true, () -> POSE.porcelainSpin);
        spin(anim, "anel_z", 0, true, () -> POSE.graphiteSpin);
        spin(anim, "nucleo", 1, true, () -> POSE.crystalSpin);
        spin(anim, "pacote", 1, true, () -> POSE.packetSpin);
        spin(anim, "satelites", 1, true, () -> POSE.fragmentSpin);
    }

    @Test
    void bateComOsKeyframesDoRepouso() throws IOException {
        JsonObject anim = animation("animation.doca.idle");
        check(anim, "nucleo", "scale", 0, DockTimeline::crystalScaleRest);
        check(anim, "nucleo", "position", 1, s -> DockTimeline.crystalLift(s, 0));
        check(anim, "anel_h", "rotation", 0, s -> DockTimeline.brassTilt(s, 0));
        spin(anim, "anel_h", 1, false, () -> POSE.brassSpin);
        spin(anim, "anel_v", 1, false, () -> POSE.porcelainSpin);
        spin(anim, "anel_z", 0, false, () -> POSE.graphiteSpin);
        spin(anim, "nucleo", 1, false, () -> POSE.crystalSpin);
        POSE.fillAt(2, false);
        assertEquals(0, POSE.packetScale, 0);
        assertEquals(0, POSE.fragmentScale, 0);
    }

    @Test
    void osLacosEmendam() {
        // Um evento a cada 100 ticks de 1010 a 1610, aplicado quando o tempo chega nele: o laço de 960 é de repouso (o
        // evento de 1010 chega no meio dele), os de 1080 a 1800 de trabalho (o de 1610 vale até 1910), o de 1920 em
        // diante de repouso.
        DockWork work = new DockWork();
        WorkFeed feed = WorkFeed.every(1010, 100, 1610, 2, 3);
        for (long boundary = 960; boundary <= 2040; boundary += DockWork.LOOP_TICKS) {
            feed.advance(work, boundary - 1e-6);
            double[] before = pose(work, boundary - 1e-6);
            feed.advance(work, boundary + 1e-6);
            double[] after = pose(work, boundary + 1e-6);
            for (int i = 0; i < before.length; i++) {
                assertEquals(0, wrap(after[i] - before[i]), 1e-3, "o campo " + i + " pula no fim do laço " + boundary);
            }
        }
        POSE.fillAt(0, true);
        double[] start = snapshot();
        POSE.fillAt(6 - 1e-9, true);
        double[] end = snapshot();
        for (int i = 0; i < start.length; i++) {
            assertEquals(0, wrap(end[i] - start[i]), 1e-3, "o campo " + i + " não emenda no laço de trabalho");
        }
    }

    @Test
    void aRampaNaoPulaOAngulo() {
        // Os eventos chegam quando o tempo passa por eles, como no cliente: três rajadas de um a cada 100 ticks (o
        // intervalo do servidor, menor que o laço, então eventos no meio dos laços e dois no mesmo laço), a segunda no
        // meio da descida da primeira e a terceira depois de um repouso.
        DockWork work = new DockWork();
        WorkFeed feed = new WorkFeed(
                new long[] {1010, 1110, 1210, 1310, 1625, 1725, 2400, 2500},
                new int[] {2, 3, 2, 3, 4, 5, 0, 1});
        double step = 0.05;
        // o mais rápido: o anel de latão e o cristal a 720° por laço de 120 ticks
        double limit = 720.0 / DockWork.LOOP_TICKS * step + 1e-6;
        // os outros campos por passo de 0,05 tick: o balanço do latão (±10° em 3 s, mais a rampa), o sobe e desce, a
        // escala do cristal (o maior salto da tabela é 0,13 por tick) e as do pacote e dos fragmentos (o trecho mais
        // curto, 0 a 1 em 5 ticks). Um laço cortado no meio salta bem mais que isso.
        double[] limits = {0.15, 0.005, 0.01, 0.02, 0.02};
        feed.advance(work, 900);
        double[] last = pose(work, 900);
        for (double t = 900 + step; t < 3200; t += step) {
            feed.advance(work, t);
            double[] now = pose(work, t);
            for (int i = 0; i < 4; i++) {
                assertTrue(Math.abs(wrap(now[i] - last[i])) <= limit, "o giro " + i + " pulou em " + t);
            }
            for (int i = 4; i < now.length; i++) {
                assertTrue(Math.abs(now[i] - last[i]) < limits[i - 4], "o campo " + i + " pulou em " + t);
            }
            last = now;
        }
    }

    /** A pose no tempo {@code t} pelo {@link DockWork}, como o renderer a monta. */
    private static double[] pose(DockWork work, double t) {
        POSE.fill(t, work.extra(t), work.speed(t), work.workLoop(t));
        return snapshot();
    }

    private static double[] snapshot() {
        return new double[] {POSE.brassSpin, POSE.crystalSpin, POSE.porcelainSpin, POSE.graphiteSpin, POSE.brassTilt,
            POSE.crystalLift, POSE.crystalScale, POSE.work ? POSE.packetScale : 0, POSE.work ? POSE.fragmentScale : 0};
    }

    /** Uma diferença de ângulo em (−180, 180]. */
    private static double wrap(double degrees) {
        double d = degrees % 360;
        if (d > 180) {
            d -= 360;
        } else if (d <= -180) {
            d += 360;
        }
        return d;
    }

    private static void check(JsonObject anim, String bone, String channel, int axis, DoubleUnaryOperator expected) {
        for (JsonElement key : keys(anim, bone, channel)) {
            double t = key.getAsJsonObject().get("time").getAsDouble();
            double value = value(key, axis);
            assertEquals(value, expected.applyAsDouble(t), EPS, bone + "." + channel + " em " + t + " s");
        }
    }

    @FunctionalInterface
    private interface Field {
        double get();
    }

    /** O giro do keyframe ({@code axis}) contra o campo da pose parada no mesmo segundo, a menos de voltas. */
    private static void spin(JsonObject anim, String bone, int axis, boolean work, Field field) {
        for (JsonElement key : keys(anim, bone, "rotation")) {
            double t = key.getAsJsonObject().get("time").getAsDouble();
            POSE.fillAt(t, work);
            assertEquals(0, wrap(field.get() - value(key, axis)), EPS, bone + " gira diferente em " + t + " s");
        }
    }

    private static double value(JsonElement key, int axis) {
        JsonObject point = key.getAsJsonObject().getAsJsonArray("data_points").get(0).getAsJsonObject();
        return point.get(new String[] {"x", "y", "z"}[axis]).getAsDouble();
    }

    private static JsonArray keys(JsonObject anim, String bone, String channel) {
        JsonArray out = new JsonArray();
        for (var entry : anim.getAsJsonObject("animators").entrySet()) {
            JsonObject animator = entry.getValue().getAsJsonObject();
            if (!bone.equals(animator.get("name").getAsString())) {
                continue;
            }
            for (JsonElement key : animator.getAsJsonArray("keyframes")) {
                if (channel.equals(key.getAsJsonObject().get("channel").getAsString())) {
                    out.add(key);
                }
            }
        }
        assertTrue(!out.isEmpty(), "sem keyframes de " + bone + "." + channel);
        return out;
    }

    private static JsonObject animation(String name) throws IOException {
        JsonObject model = JsonParser.parseString(Files.readString(BBMODEL)).getAsJsonObject();
        for (JsonElement anim : model.getAsJsonArray("animations")) {
            if (name.equals(anim.getAsJsonObject().get("name").getAsString())) {
                return anim.getAsJsonObject();
            }
        }
        throw new AssertionError("sem a animação " + name);
    }
}
