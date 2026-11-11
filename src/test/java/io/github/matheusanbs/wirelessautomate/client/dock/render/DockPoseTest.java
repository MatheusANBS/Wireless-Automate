package io.github.matheusanbs.wirelessautomate.client.dock.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.matheusanbs.wirelessautomate.dock.DockWork;
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
        DockWork work = new DockWork();
        work.event(1000, 5);
        // o laço de 960 é de repouso, os de 1080 e 1200 de trabalho (o evento de 1000 vale até 1300), o de 1320 em diante de repouso
        for (long boundary : new long[] {960, 1080, 1200, 1320, 1440, 1560}) {
            double[] before = pose(work, boundary - 1e-6);
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
        DockWork work = new DockWork();
        work.event(1010, 2);
        work.event(1290, 3);
        work.event(1610, 4);
        double step = 0.05;
        // o mais rápido: o anel de latão e o cristal a 720° por laço de 120 ticks
        double limit = 720.0 / DockWork.LOOP_TICKS * step + 1e-6;
        double[] last = pose(work, 900);
        for (double t = 900 + step; t < 2300; t += step) {
            double[] now = pose(work, t);
            for (int i = 0; i < 4; i++) {
                assertTrue(Math.abs(wrap(now[i] - last[i])) <= limit, "o giro " + i + " pulou em " + t);
            }
            for (int i = 4; i < now.length; i++) {
                assertTrue(Math.abs(now[i] - last[i]) < 0.1, "o campo " + i + " pulou em " + t);
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
