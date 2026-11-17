package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.registry.ModSounds;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Os sons de uma Armilar no cliente, um por Núcleo, tocado uma vez por tick pelo {@link ArmilarSounds} (e não pelo
 * quadro desenhado, que só roda com a Armilar na tela). Alimenta o {@link ArmilarSoundTriggers}, toca os eventos no
 * ponto do mundo em que a coisa acontece (o sol, o horizonte, a Porta de Energia, a Porta de Cristais ou o cristal da
 * câmara) e cuida dos três loops: um de cada por Armilar, que sobem e descem por rampa e só existem enquanto ela está
 * ativa e o jogador está ao alcance.
 */
final class ArmilarSoundBox implements ArmilarSoundTriggers.Sink {
    /** O alcance de cada loop, em blocos: o {@code attenuation_distance} do {@code sounds.json}. */
    static final double SUN_RANGE = 24;
    static final double FLOW_RANGE = 24;
    static final double FULL_RANGE = 16;
    /** O loop liga dentro do alcance menos a folga e só desliga passando do alcance mais a folga (sem liga e desliga na borda). */
    private static final double RANGE_SLACK = 2;
    /** O volume do loop da pressão: baixo, por baixo do sol. */
    static final float FULL_GAIN = 0.6f;
    private static final float MIN_LEVEL = 0.01f;

    private final ResonanceCoreBlockEntity core;
    private final ArmilarSoundTriggers triggers;
    private final ArmilarFrame frame = new ArmilarFrame();
    private final double[] sunAt = new double[3];
    private final double[] horizonAt = new double[3];
    private final double[] doorAt = new double[3];
    private final double[] crystalDoorAt = new double[3];
    private final double[] crystalAt = new double[3];
    private ChamberView seen = ChamberView.EMPTY;
    private int chamberCount;
    private ArmilarLoopSound sun;
    private ArmilarLoopSound flow;
    private ArmilarLoopSound full;

    private long remembered = ArmilarSoundTriggers.NEVER;

    /** Se o {@code formedAt} da última montagem tocada mudou desde a última vez que alguém perguntou (e anota). */
    boolean assembledChanged() {
        long now = triggers.assembledFor();
        if (now == remembered) {
            return false;
        }
        remembered = now;
        return true;
    }

    ArmilarSoundBox(ResonanceCoreBlockEntity core, long assembledFor) {
        this.core = core;
        this.triggers = new ArmilarSoundTriggers(assembledFor);
    }

    ResonanceCoreBlockEntity core() {
        return core;
    }

    long assembledFor() {
        return triggers.assembledFor();
    }

    /** Um tick do cliente; falso se o Núcleo saiu do mundo ou de dimensão (quem chama descarta a caixa). */
    boolean tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (core.isRemoved() || core.getLevel() != minecraft.level || player == null) {
            return false;
        }
        BlockState state = core.getBlockState();
        ChamberView view = core.clientView();
        if (view != seen) {
            refresh(view, state);
        }
        boolean active = chamberCount > 0 && state.hasProperty(ResonatorPartBlock.FORMED)
                && state.getValue(ResonatorPartBlock.FORMED);
        long gameTime = minecraft.level.getGameTime();
        triggers.tick(view, active, gameTime, ArmilarScene.clockAtTick(gameTime), this);
        loops(minecraft, player.getX(), player.getY(), player.getZ());
        return true;
    }

    /** Uma view nova: o referencial e os pontos fixos dos sons, lidos uma vez por view e nunca por tick. */
    private void refresh(ChamberView view, BlockState state) {
        seen = view;
        chamberCount = view.chambers().length;
        if (chamberCount == 0 || !state.hasProperty(ResonatorPartBlock.FACING)) {
            return;
        }
        frame.set(ArmilarScene.faceOf(state.getValue(ResonatorPartBlock.FACING)), view.origin());
        frame.toWorld(0, ArmilarSpec.SOL_Y, 0, sunAt);
        frame.toWorld(ArmilarSpec.HORIZ_BURST[0], ArmilarSpec.HORIZ_BURST[1], ArmilarSpec.HORIZ_BURST[2], horizonAt);
        frame.toWorld(ArmilarSpec.PORTA_IMPACTO[0], ArmilarSpec.PORTA_IMPACTO[1], ArmilarSpec.PORTA_IMPACTO[2], doorAt);
        frame.toWorld(ArmilarSpec.PORTA_CRISTAIS[0], ArmilarSpec.PORTA_CRISTAIS[1], ArmilarSpec.PORTA_CRISTAIS[2],
                crystalDoorAt);
    }

    /** Toca o evento no seu ponto; só nasce uma instância aqui, no evento. */
    @Override
    public void play(ArmilarSoundTriggers.Event event, int chamber) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double[] at = switch (event) {
            case ASSEMBLE, TUNE_LOCK, TUNE_LOST, CONJ_CHARGE -> sunAt;
            case CONJ_BURST, CONJ_ARC -> horizonAt;
            case CONJ_IMPACT -> doorAt;
            case GRAIN -> crystalDoorAt;
            case DONE, CRACK, DULL -> crystalPoint(chamber);
        };
        minecraft.level.playLocalSound(at[0], at[1], at[2], soundOf(event), SoundSource.BLOCKS, 1f, 1f, false);
        ArmilarSounds.played(event);
    }

    /**
     * O cristal da câmara no mundo: no nó dele, sem o giro. O cristal anda alguns blocos na órbita, e a atenuação do
     * som em 16 blocos não distingue isso; o rascunho é do tick, nunca do desenho.
     */
    private double[] crystalPoint(int chamber) {
        double[] center = crystalAt;
        ArmilarNodes.center(Math.max(0, Math.min(chamber, ArmilarNodes.FIRST[2] + ArmilarSpec.NOS[2] - 1)), 1, 0, 0, 0,
                center);
        frame.toWorld(center[0], center[1], center[2], center);
        return center;
    }

    private static SoundEvent soundOf(ArmilarSoundTriggers.Event event) {
        return switch (event) {
            case ASSEMBLE -> ModSounds.ARMILAR_ASSEMBLE.get();
            case TUNE_LOCK -> ModSounds.ARMILAR_TUNE_LOCK.get();
            case TUNE_LOST -> ModSounds.ARMILAR_TUNE_LOST.get();
            case CONJ_CHARGE -> ModSounds.ARMILAR_CONJ_CHARGE.get();
            case CONJ_BURST -> ModSounds.ARMILAR_CONJ_BURST.get();
            case CONJ_ARC -> ModSounds.ARMILAR_CONJ_ARC.get();
            case CONJ_IMPACT -> ModSounds.ARMILAR_CONJ_IMPACT.get();
            case GRAIN -> ModSounds.ARMILAR_RECHARGE_GRAIN.get();
            case DONE -> ModSounds.ARMILAR_RECHARGE_DONE.get();
            case CRACK -> ModSounds.ARMILAR_CRYSTAL_CRACK.get();
            case DULL -> ModSounds.ARMILAR_CRYSTAL_DULL.get();
        };
    }

    /** Liga, ajusta e desliga os três loops pelo estado e pela distância do jogador. */
    private void loops(Minecraft minecraft, double px, double py, double pz) {
        boolean active = triggers.active();
        sun = loop(minecraft, sun, ModSounds.ARMILAR_SUN_LOOP.get(), sunAt, SUN_RANGE, active,
                (float) triggers.sunLevel(), 1f, px, py, pz);
        flow = loop(minecraft, flow, ModSounds.ARMILAR_FLOW_LOOP.get(), doorAt, FLOW_RANGE, active,
                (float) triggers.flowLevel(), (float) triggers.flowPitch(), px, py, pz);
        full = loop(minecraft, full, ModSounds.ARMILAR_FULL_LOOP.get(), sunAt, FULL_RANGE, active && triggers.full(),
                FULL_GAIN, 1f, px, py, pz);
    }

    /**
     * Um loop: nasce se há motivo (ativa, com volume e ao alcance) e ainda não há um tocando, segue o alvo enquanto há
     * motivo e desce por rampa quando não há. Nunca cria o segundo: reaproveita o que ainda está descendo.
     */
    private ArmilarLoopSound loop(Minecraft minecraft, ArmilarLoopSound current, SoundEvent event, double[] at,
            double range, boolean wanted, float level, float pitch, double px, double py, double pz) {
        long now = minecraft.level.getGameTime();
        boolean playing = current != null && ArmilarLoopLife.alive(current.isStopped(),
                minecraft.getSoundManager().isActive(current), now, current.startedAt);
        if (current != null && !playing && !current.isStopped()) {
            // o engine derrubou a instância sem avisar: solta de vez e deixa nascer outra se ainda há motivo
            current.halt();
        }
        double dx = at[0] - px;
        double dy = at[1] - py;
        double dz = at[2] - pz;
        double limit = playing ? range + RANGE_SLACK : range - RANGE_SLACK;
        boolean near = dx * dx + dy * dy + dz * dz <= limit * limit;
        if (wanted && near && level > MIN_LEVEL) {
            if (!playing) {
                current = new ArmilarLoopSound(event, at[0], at[1], at[2], pitch);
                current.startedAt = now;
                minecraft.getSoundManager().play(current);
            }
            current.drive(level, pitch);
        } else if (playing) {
            current.fadeOut();
        }
        return current;
    }

    /** Se o loop do tipo está tocando de fato no {@code SoundManager} (só o e2e usa). */
    boolean loopActive(Loop which) {
        ArmilarLoopSound sound = switch (which) {
            case SUN -> sun;
            case FLOW -> flow;
            case FULL -> full;
        };
        return sound != null && !sound.isStopped() && Minecraft.getInstance().getSoundManager().isActive(sound);
    }

    /** Para os loops na hora: a Armilar saiu do mundo, da dimensão ou do jogo. */
    void dispose() {
        sun = halt(sun);
        flow = halt(flow);
        full = halt(full);
    }

    private static ArmilarLoopSound halt(ArmilarLoopSound sound) {
        if (sound != null && !sound.isStopped()) {
            sound.halt();
        }
        return null;
    }

    /** Os três loops da Armilar. */
    enum Loop {
        SUN, FLOW, FULL
    }
}
