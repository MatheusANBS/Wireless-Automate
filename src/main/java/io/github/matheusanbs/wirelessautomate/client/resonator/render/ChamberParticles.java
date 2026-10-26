package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Os viajantes da câmara do desenho antigo (sem o shader ou com o Iris), uma vez por tick e só com o jogador perto:
 * faíscas do centro até uma Porta de Energia com vazão, mais frequentes com a faixa dela, e grãos de redstone da Porta
 * de Cristais até o Opaco em recarga. No modo shader não há nenhum dos dois: o conduto e os tentáculos os substituem. Cada
 * viajante anda por um {@code smoothstep} entre os dois pontos. As faíscas não são partículas: o
 * {@link ChamberSparks} as desenha no passe do renderer, com o tempo do tick anterior e o de agora interpolados,
 * porque as partículas translúcidas do vanilla saem depois do terreno translúcido e o Vidro, que grava
 * profundidade, as esconderia. Os grãos soltam, onde estão, uma partícula de pó parada do vanilla.
 */
final class ChamberParticles {
    private static final double NEAR = 32 * 32;
    static final int SPARK_TICKS = 25;
    private static final int MAX_SPARKS = 8;
    private static final int MAX_REDSTONE = 4;
    private static final int REDSTONE_TICKS = 33;
    private static final double JITTER = 0.04;
    private static final DustParticleOptions REDSTONE = new DustParticleOptions(new Vector3f(0.84f, 0.27f, 0.23f),
            0.6f);

    /**
     * Uma faísca, relativa ao centro da câmara: de {@code from} até {@code to}, com o tempo do tick anterior e o de
     * agora, a semente da cintilação e os dois lados do caminho (para o zigue-zague do rastro).
     */
    static final class Spark {
        final double[] from = new double[3];
        final double[] to = new double[3];
        final float[] side = new float[3];
        final float[] up = new float[3];
        double t;
        double previousT;
        int seed;
        boolean alive;

        /** O ponto do caminho no tempo {@code at} (de 0 a 1), em {@code out}. */
        void point(double at, float[] out) {
            double s = ChamberCrystals.smoothstep(at);
            for (int k = 0; k < 3; k++) {
                out[k] = (float) (from[k] + (to[k] - from[k]) * s);
            }
        }

        /** O tempo interpolado pela fração do tick. */
        double at(float partialTick) {
            return previousT + (t - previousT) * partialTick;
        }

        private void start(Vec3 start, Vec3 end, int seed) {
            from[0] = start.x;
            from[1] = start.y;
            from[2] = start.z;
            to[0] = end.x;
            to[1] = end.y;
            to[2] = end.z;
            float dx = (float) (end.x - start.x);
            float dy = (float) (end.y - start.y);
            float dz = (float) (end.z - start.z);
            // O lado horizontal: d × (0, 1, 0); num caminho vertical, o eixo x. O outro: lado × d.
            float flat = (float) Math.sqrt(dx * dx + dz * dz);
            side[0] = flat < 1e-6f ? 1 : -dz / flat;
            side[1] = 0;
            side[2] = flat < 1e-6f ? 0 : dx / flat;
            float length = Math.max(1e-6f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            up[0] = side[2] * dy / length;
            up[1] = (side[0] * dz - side[2] * dx) / length;
            up[2] = -side[0] * dy / length;
            this.seed = seed;
            t = 0;
            previousT = 0;
            alive = true;
        }
    }

    /** Um grão de redstone: da Porta de Cristais {@code from} até o Opaco da câmara {@code chamber}. */
    private static final class Grain {
        private Vec3 from;
        private int chamber = -1;
        private double t;
        private boolean alive;
    }

    private final Spark[] sparks = new Spark[MAX_SPARKS];
    private final Grain[] redstone = new Grain[MAX_REDSTONE];
    private final double[] crystalAt = new double[3];
    private double sparkClock;
    private double redClock;
    private int born;

    ChamberParticles() {
        for (int i = 0; i < MAX_SPARKS; i++) {
            sparks[i] = new Spark();
        }
        for (int i = 0; i < MAX_REDSTONE; i++) {
            redstone[i] = new Grain();
        }
    }

    /** As faíscas, vivas ou não; o desenho só lê. */
    Spark[] sparks() {
        return sparks;
    }

    void tick(ChamberScene scene, ChamberView view, ClientLevel level, long gameTime, boolean legacy) {
        Player player = Minecraft.getInstance().player;
        Vec3 center = scene.worldCenter();
        if (player == null || player.distanceToSqr(center) >= NEAR) {
            for (Spark spark : sparks) {
                spark.alive = false;
            }
            return;
        }
        RandomSource random = level.getRandom();
        byte[] chambers = scene.chambers();
        for (Spark spark : sparks) {
            if (spark.alive) {
                spark.previousT = spark.t;
                spark.t += 1.0 / SPARK_TICKS;
                spark.alive = spark.t < 1 && legacy;
            }
        }
        List<Vec3> ports = scene.energyInner();
        int flowing = 0;
        double rate = 0;
        for (int i = 0; legacy && i < ports.size(); i++) {
            int band = scene.flowBand(i);
            if (band > 0) {
                flowing++;
                rate += (2 + 3.0 * band / 7) / 20;
            }
        }
        sparkClock = flowing == 0 ? 0 : sparkClock + rate;
        if (sparkClock > 1) {
            sparkClock -= 1;
            Vec3 port = flowingPort(scene, ports, Math.floorMod(born, flowing));
            Spark spark = freeSpark();
            if (port != null && spark != null) {
                Vec3 start = new Vec3(0, (random.nextDouble() * 2 - 1) * JITTER,
                        (random.nextDouble() * 2 - 1) * JITTER);
                spark.start(start, port.subtract(center), born++ * 7919 + 13);
            }
        }
        int target = ChamberCrystals.firstRecharging(chambers, view);
        redClock += 2.0 * (target < 0 || !legacy ? 0 : 1) / 20;
        if (redClock > 1) {
            redClock -= 1;
            Vec3 port = nearest(scene.crystalInner(), center);
            Grain grain = freeGrain();
            if (port != null && grain != null) {
                grain.from = port;
                grain.chamber = target;
                grain.t = 0;
                grain.alive = true;
            }
        }
        for (Grain grain : redstone) {
            if (grain.alive && (!legacy || grain.chamber >= chambers.length)) {
                grain.alive = false;
            }
            if (advance(grain)) {
                double[] crystal = ChamberCrystals.crystalPos(grain.chamber, chambers, gameTime, view,
                        crystalAt);
                Vec3 to = center.add(crystal[0], crystal[1], crystal[2]);
                Vec3 at = grain.from.add(to.subtract(grain.from).scale(ChamberCrystals.smoothstep(grain.t)));
                level.addParticle(REDSTONE, at.x, at.y, at.z, 0, 0, 0);
            }
        }
    }

    /** Anda um tick; devolve se o grão ainda está vivo. */
    private static boolean advance(Grain grain) {
        if (!grain.alive) {
            return false;
        }
        grain.t += 1.0 / REDSTONE_TICKS;
        if (grain.t >= 1) {
            grain.alive = false;
        }
        return grain.alive;
    }

    /** A {@code k}-ésima Porta de Energia com faixa de vazão, ou {@code null}. */
    private static Vec3 flowingPort(ChamberScene scene, List<Vec3> ports, int k) {
        int seen = 0;
        for (int i = 0; i < ports.size(); i++) {
            if (scene.flowBand(i) > 0 && seen++ == k) {
                return ports.get(i);
            }
        }
        return null;
    }

    private static Vec3 nearest(List<Vec3> points, Vec3 center) {
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Vec3 point : points) {
            double distance = point.distanceToSqr(center);
            if (distance < bestDistance) {
                best = point;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Spark freeSpark() {
        for (Spark spark : sparks) {
            if (!spark.alive) {
                return spark;
            }
        }
        return null;
    }

    private Grain freeGrain() {
        for (Grain grain : redstone) {
            if (!grain.alive) {
                return grain;
            }
        }
        return null;
    }
}
