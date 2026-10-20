package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * O estado da câmara de um Núcleo no cliente, atualizado uma vez por tick novo no desenho: a sintonia suavizada, a
 * cabeça da figura, os pontos do tick atual e do anterior (interpolados por {@code partialTick}), o giro dos cristais,
 * as partículas e as Portas da casca. Guarda também o que as animações novas leem: a faixa de vazão de cada Porta de
 * Energia (suavizada por tick, para o conduto acender e apagar em cerca de 1 s e as faíscas seguirem a vazão real), a
 * pressão do plasma quando o buffer está cheio e o anel do fim da recarga (o tick em que cada cristal terminou).
 * Os arrays da {@link ChamberView} são lidos aqui, uma vez por tick, e nunca por quadro.
 */
final class ChamberScene {
    private static final double Q_RATE = 1.6;
    /** O quanto a sintonia do plasma anda por tick em direção ao alvo. */
    private static final float TUNE_RATE = 0.25f;
    /** O quanto a faixa de vazão de uma Porta anda por tick em direção ao alvo: acende e apaga em cerca de 1 s. */
    private static final float FLOW_RATE = 0.12f;
    private static final double TICK_SECONDS = 0.05;
    private static final int PORT_REFRESH_TICKS = 200;
    private static final double FADE_SECONDS = 0.9;
    private static final double BREATH_SECONDS = 2.4;
    /** O tempo desde algo que nunca aconteceu: tudo já aceso, nenhum clarão. */
    private static final double LONG_AGO = 1e9;
    private static final long NEVER = Long.MIN_VALUE / 2;
    /** A duração do anel ao fim da recarga, em ticks (0,7 s). */
    static final int RING_TICKS = 14;
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
    private byte[] flowTarget = new byte[0];
    private float[] flowPrev = new float[0];
    private float[] flowNow = new float[0];
    private boolean full;
    private boolean conduit;
    private float tunePrev;
    private float tuneNow;
    private int charged;
    private double q;
    private double head;
    private double liveSpin;
    private double dullSpin;
    private boolean started;
    /** O tick em que o anel de cada câmara começou, ou {@link #NEVER}; criado com as câmaras, nunca por quadro. */
    private long[] ringAt = new long[0];
    /** A câmara que recarregava na view anterior, ou -1, e se o anel dela já saiu. */
    private int rechargingPrev = -1;
    /** O índice, em {@link #crystalInner}, da Porta de Cristais mais perto de cada câmara Opaca, ou -1. */
    private int[] nearPort = new int[0];
    private boolean pickPorts;
    private final double[] pickAt = new double[3];
    private boolean ringFired;
    long lastUsed;

    ChamberScene(BlockPos core) {
        this.core = core.immutable();
        this.box = new AABB(core);
    }

    void update(ChamberView view, long tunedAt, long gameTime, ClientLevel level) {
        if (view != this.view) {
            this.view = view;
            chambers = view.chambers();
            flowTarget = view.flows();
            full = view.full();
            if (flowNow.length != flowTarget.length) {
                flowPrev = new float[flowTarget.length];
                flowNow = new float[flowTarget.length];
            }
            if (ringAt.length != chambers.length) {
                ringAt = new long[chambers.length];
                Arrays.fill(ringAt, NEVER);
                rechargingPrev = -1;
                nearPort = new int[chambers.length];
            }
            pickPorts = true;
            trackRecharge(gameTime);
            charged = 0;
            for (byte chamber : chambers) {
                if (chamber == 1 || chamber == 2) {
                    charged++;
                }
            }
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
        tunePrev = tuneNow;
        tuneNow += ((view.tuned() ? 1f : view.band() / (float) (TuneSignal.BANDS - 1)) - tuneNow) * TUNE_RATE;
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
            pickPorts = true;
        }
        if (pickPorts) {
            pickNearestPorts(gameTime);
            pickPorts = false;
        }
        for (int i = 0; i < flowNow.length; i++) {
            flowPrev[i] = flowNow[i];
            flowNow[i] += (flowTarget[i] - flowNow[i]) * FLOW_RATE;
        }
        if (rechargingPrev >= 0 && !ringFired
                && ChamberCrystals.recharge(rechargingPrev, chambers, gameTime, view) >= 1) {
            ringAt[rechargingPrev] = gameTime;
            ringFired = true;
        }
        for (int i = 0; i < ringAt.length; i++) {
            if (ringAt[i] != NEVER && gameTime - ringAt[i] >= RING_TICKS) {
                ringAt[i] = NEVER;
            }
        }
        particles.tick(this, view, level, gameTime, !conduit);
        lastUsed = gameTime;
    }

    /**
     * O anel sai quando a câmara que recarregava deixa de ser Opaco (a view nova) e ainda não saiu pelo progresso
     * (a recarga do cliente chegou a 1 antes); só então, e nunca por uma câmara esvaziada.
     */
    private void trackRecharge(long gameTime) {
        int now = ChamberCrystals.recharging(chambers, view);
        if (now == rechargingPrev) {
            return;
        }
        if (rechargingPrev >= 0 && !ringFired && (chambers[rechargingPrev] == 1 || chambers[rechargingPrev] == 2)) {
            ringAt[rechargingPrev] = gameTime;
        }
        rechargingPrev = now;
        ringFired = false;
    }

    /** O quanto o anel da câmara {@code i} andou, de 0 a 1 em 0,7 s; negativo quando não há anel em curso. */
    float ring(int i, long gameTime, float partialTick) {
        return i < ringAt.length && ringAt[i] != NEVER
                ? (gameTime - ringAt[i] + partialTick) / RING_TICKS : -1f;
    }

    /**
     * A face de dentro da Porta de Cristais mais perto do Opaco da câmara {@code i}, guardada até as Portas ou as
     * câmaras mudarem; {@code null} sem Porta ou se a câmara não é Opaca.
     */
    Vec3 nearestCrystalPort(int i) {
        int at = i < nearPort.length ? nearPort[i] : -1;
        return at >= 0 && at < crystalInner.size() ? crystalInner.get(at) : null;
    }

    private void pickNearestPorts(long gameTime) {
        for (int i = 0; i < nearPort.length; i++) {
            nearPort[i] = -1;
            if (!ChamberCrystals.isDull(chambers[i]) || crystalInner.isEmpty()) {
                continue;
            }
            ChamberCrystals.crystalPos(i, chambers, q, gameTime, view, pickAt);
            double best = Double.MAX_VALUE;
            for (int k = 0; k < crystalInner.size(); k++) {
                Vec3 port = crystalInner.get(k);
                double dx = port.x - worldCenter.x - pickAt[0];
                double dy = port.y - worldCenter.y - pickAt[1];
                double dz = port.z - worldCenter.z - pickAt[2];
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < best) {
                    best = distance;
                    nearPort[i] = k;
                }
            }
        }
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

    /** Liga o conduto de luz no lugar das faíscas; o renderer diz a cada quadro, antes do {@code update}. */
    void useConduit(boolean on) {
        conduit = on;
    }

    /** A faixa de vazão (0 a 7) que o servidor mandou para a Porta de Energia {@code port}; 0 sem faixa. */
    int flowBand(int port) {
        return port < flowTarget.length ? flowTarget[port] : 0;
    }

    /** A faixa suavizada da Porta de Energia {@code port}, de 0 a 7, interpolada entre o tick anterior e o de agora. */
    float flow(int port, float partialTick) {
        return port < flowNow.length ? Mth.lerp(partialTick, flowPrev[port], flowNow[port]) : 0f;
    }

    /** O buffer terminou o último lote cheio: o plasma fica sob pressão. */
    boolean full() {
        return full;
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

    /** A sintonia suavizada do plasma, de 0 a 1, interpolada entre o tick anterior e o de agora. */
    float tune(float partialTick) {
        return Mth.lerp(partialTick, tunePrev, tuneNow);
    }

    /** Os cristais não Opacos nas câmaras (Sinal e Lapidado com carga), de 0 a 8. */
    int charged() {
        return charged;
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
