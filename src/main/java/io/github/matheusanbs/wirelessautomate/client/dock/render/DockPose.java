package io.github.matheusanbs.wirelessautomate.client.dock.render;

import io.github.matheusanbs.wirelessautomate.dock.DockWork;

/**
 * A pose de cada peça da Doca num quadro, em graus e pixels do modelo (as unidades do Blockbench), montada pelo
 * {@link #fill} a partir do tempo e lida pelo desenho. Há uma só ({@link #INSTANCE}), reusada: o render é só na thread
 * de render, um bloco por vez, e assim nada é alocado por quadro. Puro.
 *
 * <p>Os giros dos anéis e do cristal correm pelo tempo do mundo inteiro, não pelo laço: cada um é
 * {@code repouso × t + (trabalho − repouso) × extra(t)} ({@link DockWork#extra}), contínuo também na rampa entre os
 * dois ritmos. Os balanços misturam as curvas do repouso e do trabalho pela velocidade ({@link DockWork#speed}). O
 * pacote, os fragmentos e a escala irregular do cristal são do laço de trabalho ({@link DockWork#workLoop}) e saem da
 * fase dele.
 */
final class DockPose {
    static final DockPose INSTANCE = new DockPose();
    private static final double TICKS_PER_SECOND = 20;

    double brassSpin;
    double brassTilt;
    double porcelainSpin;
    double graphiteSpin;
    double crystalSpin;
    double crystalLift;
    double crystalScale;
    /** O laço é de trabalho: o pacote e os fragmentos existem. */
    boolean work;
    double packetOffset;
    double packetScale;
    double packetSpin;
    double fragmentRadius;
    double fragmentScale;
    double fragmentSpin;

    private DockPose() {
    }

    /**
     * A pose no tempo {@code ticks} (com a fração do quadro), com a integral {@code extra} e a velocidade
     * {@code speed} da rampa e se o laço que toca é de trabalho.
     */
    void fill(double ticks, double extra, double speed, boolean workLoop) {
        double loop = DockWork.LOOP_TICKS;
        double base = (ticks % loop) / loop;
        double more = (extra % loop) / loop;
        brassSpin = turn(DockTimeline.BRASS_REST, base, DockTimeline.BRASS_WORK - DockTimeline.BRASS_REST, more);
        crystalSpin = turn(DockTimeline.CRYSTAL_REST, base, DockTimeline.CRYSTAL_WORK - DockTimeline.CRYSTAL_REST,
                more);
        porcelainSpin = turn(DockTimeline.PORCELAIN, base, 0, 0);
        graphiteSpin = turn(DockTimeline.GRAPHITE, base, 0, 0);
        double s = DockWork.loopPhase(ticks) * DockTimeline.LOOP;
        brassTilt = DockTimeline.brassTilt(s, speed);
        crystalLift = DockTimeline.crystalLift(s, speed);
        work = workLoop;
        if (workLoop) {
            crystalScale = DockTimeline.crystalScaleWork(s);
            double phase = s / DockTimeline.LOOP;
            packetOffset = DockTimeline.packetOffset(s);
            packetScale = DockTimeline.packetScale(s);
            packetSpin = (DockTimeline.PACKET_SPIN * phase) % 360;
            fragmentRadius = DockTimeline.fragmentRadius(s);
            fragmentScale = DockTimeline.fragmentScale(s);
            fragmentSpin = (DockTimeline.FRAGMENT_SPIN * phase) % 360;
        } else {
            crystalScale = DockTimeline.crystalScaleRest(s);
            packetScale = 0;
            fragmentScale = 0;
        }
    }

    /** A pose parada no segundo {@code seconds} de um laço de repouso ou de trabalho, na velocidade cheia dele. */
    void fillAt(double seconds, boolean workLoop) {
        double ticks = seconds * TICKS_PER_SECOND;
        fill(ticks, workLoop ? ticks : 0, workLoop ? 1 : 0, workLoop);
    }

    private static double turn(double perLoop, double base, double extraPerLoop, double more) {
        return (perLoop * base + extraPerLoop * more) % 360;
    }
}
