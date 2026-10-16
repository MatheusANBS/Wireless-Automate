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
 * As partículas da câmara, uma vez por tick e só com o jogador perto: faíscas do centro até a Porta de Energia mais
 * próxima, mais frequentes com a geração, e grãos de redstone da Porta de Cristais até o Opaco em recarga. Cada
 * viajante anda por um {@code smoothstep} entre os dois pontos e solta, onde está, uma partícula parada do vanilla.
 * As duas são de pó, opacas: o NeoForge desenha as partículas translúcidas (como a {@code ELECTRIC_SPARK}) depois
 * do terreno translúcido, e o Vidro do Ressonador, que grava profundidade, as esconderia.
 */
final class ChamberParticles {
    private static final double NEAR = 32 * 32;
    private static final int MAX_SPARKS = 8;
    private static final int MAX_REDSTONE = 4;
    private static final int SPARK_TICKS = 25;
    private static final int REDSTONE_TICKS = 33;
    private static final double JITTER = 0.04;
    private static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.26f), 0.5f);
    private static final DustParticleOptions REDSTONE = new DustParticleOptions(new Vector3f(0.84f, 0.27f, 0.23f),
            0.6f);

    /** Um viajante: de {@code from} até {@code to} (ou até o Opaco da câmara {@code chamber}), com o tempo {@code t}. */
    private static final class Traveler {
        private Vec3 from;
        private Vec3 to;
        private int chamber = -1;
        private double t;
        private boolean alive;
    }

    private final Traveler[] sparks = travelers(MAX_SPARKS);
    private final Traveler[] redstone = travelers(MAX_REDSTONE);
    private final double[] crystalAt = new double[3];
    private double sparkClock;
    private double redClock;

    void tick(ChamberScene scene, ChamberView view, ClientLevel level, long gameTime) {
        Player player = Minecraft.getInstance().player;
        Vec3 center = scene.worldCenter();
        if (player == null || player.distanceToSqr(center) >= NEAR) {
            return;
        }
        RandomSource random = level.getRandom();
        byte[] chambers = scene.chambers();
        int band = view.band();
        sparkClock += (band == 0 ? 0 : 2 + 3.0 * band / 7) / 20;
        if (sparkClock > 1) {
            sparkClock -= 1;
            Vec3 port = nearest(scene.energyInner(), center);
            Traveler spark = free(sparks);
            if (port != null && spark != null) {
                spark.from = center.add(0, (random.nextDouble() * 2 - 1) * JITTER, (random.nextDouble() * 2 - 1) * JITTER);
                spark.to = port;
                spark.t = 0;
                spark.alive = true;
            }
        }
        int target = ChamberCrystals.recharging(chambers, view);
        redClock += 2.0 * (target < 0 ? 0 : 1) / 20;
        if (redClock > 1) {
            redClock -= 1;
            Vec3 port = nearest(scene.crystalInner(), center);
            Traveler grain = free(redstone);
            if (port != null && grain != null) {
                grain.from = port;
                grain.chamber = target;
                grain.t = 0;
                grain.alive = true;
            }
        }
        for (Traveler spark : sparks) {
            if (advance(spark, SPARK_TICKS)) {
                Vec3 at = lerp(spark.from, spark.to, spark.t);
                level.addParticle(SPARK, at.x, at.y, at.z, 0, 0, 0);
            }
        }
        for (Traveler grain : redstone) {
            if (grain.alive && grain.chamber >= chambers.length) {
                grain.alive = false;
            }
            if (advance(grain, REDSTONE_TICKS)) {
                double[] crystal = ChamberCrystals.crystalPos(grain.chamber, chambers, scene.q(), gameTime, view,
                        crystalAt);
                Vec3 at = lerp(grain.from, center.add(crystal[0], crystal[1], crystal[2]), grain.t);
                level.addParticle(REDSTONE, at.x, at.y, at.z, 0, 0, 0);
            }
        }
    }

    /** Anda um tick; devolve se o viajante ainda está vivo. */
    private static boolean advance(Traveler traveler, int ticks) {
        if (!traveler.alive) {
            return false;
        }
        traveler.t += 1.0 / ticks;
        if (traveler.t >= 1) {
            traveler.alive = false;
        }
        return traveler.alive;
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double t) {
        double s = ChamberCrystals.smoothstep(t);
        return from.add(to.subtract(from).scale(s));
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

    private static Traveler free(Traveler[] pool) {
        for (Traveler traveler : pool) {
            if (!traveler.alive) {
                return traveler;
            }
        }
        return null;
    }

    private static Traveler[] travelers(int count) {
        Traveler[] pool = new Traveler[count];
        for (int i = 0; i < count; i++) {
            pool[i] = new Traveler();
        }
        return pool;
    }
}
