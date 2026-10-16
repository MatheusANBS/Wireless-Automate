package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * O estado da câmara de um Núcleo no cliente, atualizado uma vez por tick novo no desenho: a sintonia suavizada, a
 * cabeça da figura, os pontos do tick atual e do anterior (interpolados por {@code partialTick}), o giro dos cristais,
 * as partículas e as Portas da casca.
 * Os arrays da {@link ChamberView} são lidos aqui, uma vez por tick, e nunca por quadro.
 */
final class ChamberScene {
    private static final double Q_RATE = 1.6;
    private static final double TICK_SECONDS = 0.05;
    private static final int PORT_REFRESH_TICKS = 200;
    private static final double FADE_SECONDS = 0.9;
    private static final double BREATH_SECONDS = 2.4;
    /** O tempo desde algo que nunca aconteceu: tudo já aceso, nenhum clarão. */
    private static final double LONG_AGO = 1e9;
    private static final int SIZE = 5;
    private static final double DULL_SPIN = 0.1;
    /** A face de dentro de uma Porta, um pouco para dentro da câmara. */
    private static final double PORT_INSET = 0.51;

    private final BlockPos core;
    private final double[] current = new double[LissajousCurve.POINTS * 3];
    private final double[] previous = new double[LissajousCurve.POINTS * 3];
    private final double[] headAt = new double[3];
    private ChamberView view = ChamberView.EMPTY;
    private byte[] chambers = new byte[0];
    private int[] origin;
    private Vec3 center = Vec3.ZERO;
    private Vec3 worldCenter = Vec3.ZERO;
    private AABB box;
    private long tunedAt = -1;
    private long portsReadAt;
    private List<Vec3> energyInner = List.of();
    private List<Vec3> crystalInner = List.of();
    private final ChamberParticles particles = new ChamberParticles();
    private double q;
    private double head;
    private double liveSpin;
    private double dullSpin;
    private boolean started;
    long lastUsed;

    ChamberScene(BlockPos core) {
        this.core = core.immutable();
        this.box = new AABB(core);
    }

    void update(ChamberView view, long tunedAt, long gameTime, ClientLevel level) {
        if (view != this.view) {
            this.view = view;
            chambers = view.chambers();
            int[] next = view.origin();
            if (!Arrays.equals(next, origin)) {
                origin = next;
                worldCenter = new Vec3(origin[0] + 2.5, origin[1] + 2.5, origin[2] + 2.5);
                center = worldCenter.subtract(core.getX(), core.getY(), core.getZ());
                box = new AABB(origin[0], origin[1], origin[2], origin[0] + SIZE, origin[1] + SIZE, origin[2] + SIZE);
                portsReadAt = Long.MIN_VALUE;
            }
        }
        this.tunedAt = tunedAt;
        double target = view.band() / (double) (TuneSignal.BANDS - 1);
        if (started) {
            q += (target - q) * Math.min(1, Q_RATE * TICK_SECONDS);
            head += LissajousCurve.advance(q) * TICK_SECONDS;
            System.arraycopy(current, 0, previous, 0, current.length);
            LissajousCurve.fill(current, head, q, gameTime * TICK_SECONDS);
            liveSpin += liveSpinRate() * TICK_SECONDS;
            dullSpin += DULL_SPIN * TICK_SECONDS;
        } else {
            // Na primeira vez a figura já nasce na sintonia de agora, sem escorregar do zero ao chegar perto.
            q = target;
            LissajousCurve.fill(current, head, q, gameTime * TICK_SECONDS);
            System.arraycopy(current, 0, previous, 0, current.length);
            started = true;
        }
        if (origin != null && (portsReadAt == Long.MIN_VALUE || gameTime - portsReadAt >= PORT_REFRESH_TICKS
                || gameTime < portsReadAt)) {
            readPorts(level);
            portsReadAt = gameTime;
        }
        particles.tick(this, view, level, gameTime);
        lastUsed = gameTime;
    }

    /** As faces de dentro das Portas montadas nas 98 posições da casca. */
    private void readPorts(ClientLevel level) {
        List<Vec3> energy = new ArrayList<>();
        List<Vec3> crystal = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    if (x != 0 && x != SIZE - 1 && y != 0 && y != SIZE - 1 && z != 0 && z != SIZE - 1) {
                        continue;
                    }
                    pos.set(origin[0] + x, origin[1] + y, origin[2] + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof ResonatorPartBlock part && state.getValue(ResonatorPartBlock.FORMED)) {
                        Vec3 inner = Vec3.atCenterOf(pos).subtract(
                                Vec3.atLowerCornerOf(state.getValue(ResonatorPartBlock.FACING).getNormal()).scale(PORT_INSET));
                        switch (part.kind()) {
                            case ENERGY_PORT -> energy.add(inner);
                            case CRYSTAL_PORT -> crystal.add(inner);
                            case TUNER -> {
                            }
                        }
                    }
                }
            }
        }
        energyInner = List.copyOf(energy);
        crystalInner = List.copyOf(crystal);
    }

    /** As faíscas da câmara, para o desenho. */
    ChamberParticles.Spark[] sparks() {
        return particles.sparks();
    }

    ChamberView view() {
        return view;
    }

    /** O que há em cada câmara, lido da view uma vez por view nova; não altere. */
    byte[] chambers() {
        return chambers;
    }

    double q() {
        return q;
    }

    double[] current() {
        return current;
    }

    double[] previous() {
        return previous;
    }

    /** O centro da câmara relativo ao Núcleo. */
    Vec3 center() {
        return center;
    }

    /** A caixa 5 × 5 × 5 da estrutura, ou o bloco do Núcleo antes da primeira view. */
    AABB box() {
        return box;
    }

    /** O centro da câmara no mundo. */
    Vec3 worldCenter() {
        return worldCenter;
    }

    /** As faces de dentro das Portas de Energia, no mundo. */
    List<Vec3> energyInner() {
        return energyInner;
    }

    /** As faces de dentro das Portas de Cristais, no mundo. */
    List<Vec3> crystalInner() {
        return crystalInner;
    }

    /** O giro acumulado dos cristais, em radianos, interpolado: o Opaco devagar, os outros mais rápido com a sintonia. */
    double spin(boolean dull, float partialTick) {
        return dull ? dullSpin + DULL_SPIN * TICK_SECONDS * partialTick
                : liveSpin + liveSpinRate() * TICK_SECONDS * partialTick;
    }

    private double liveSpinRate() {
        return 0.4 + 1.2 * q;
    }

    /** A cabeça da figura, interpolada, relativa ao centro, num array da cena que a próxima chamada reescreve. */
    double[] headPoint(float partialTick) {
        int at = (LissajousCurve.POINTS - 1) * 3;
        for (int k = 0; k < 3; k++) {
            headAt[k] = previous[at + k] + (current[at + k] - previous[at + k]) * partialTick;
        }
        return headAt;
    }

    /** Segundos desde a montagem; montado de antes (0) ou nunca (-1), muito tempo. */
    double sinceFormed(long gameTime, float partialTick) {
        long formedAt = view.formedAt();
        return formedAt <= 0 ? LONG_AGO : seconds(gameTime - formedAt, partialTick);
    }

    /** Segundos desde que a lâmpada acendeu; sem sintonia vista, muito tempo. */
    double sinceTuned(long gameTime, float partialTick) {
        return tunedAt < 0 ? LONG_AGO : seconds(gameTime - tunedAt, partialTick);
    }

    private static double seconds(long ticks, float partialTick) {
        return Math.max(0, (ticks + partialTick) * TICK_SECONDS);
    }

    /** O quanto uma parte já acendeu, de 0 a 1 em 0,9 s, começando {@code delay} segundos depois da montagem. */
    static double on(double since, double delay) {
        double x = Math.clamp((since - delay) / FADE_SECONDS, 0.0, 1.0);
        return x * x * (3 - 2 * x);
    }

    /** A respiração da lente, de 0 a 1 num ciclo de 2,4 s. */
    double breath(long gameTime, float partialTick) {
        double t = (gameTime + partialTick) * TICK_SECONDS;
        return 0.5 + 0.5 * Math.sin(2 * Math.PI * t / BREATH_SECONDS);
    }
}
