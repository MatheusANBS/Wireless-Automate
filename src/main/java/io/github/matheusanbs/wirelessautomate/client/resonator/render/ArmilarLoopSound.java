package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Um loop da Armilar (o sol, o feixe ou o buffer cheio): um por Armilar e por tipo, fixo no ponto do mundo. O volume e
 * o tom andam por tick até o alvo que o {@link ArmilarSoundBox} dá, então entra e sai por rampa, sem corte seco. Depois
 * de {@link #fadeOut()} ele para sozinho ao chegar ao silêncio; {@link #halt()} para na hora (a Armilar saiu do mundo).
 */
final class ArmilarLoopSound extends AbstractTickableSoundInstance {
    /** O volume por tick: de 0 a 1 em cerca de 1 s. */
    static final float VOLUME_STEP = 0.04f;
    /** O tom por tick: a faixa toda (0,25) em cerca de 2 s. */
    static final float PITCH_STEP = 0.006f;
    private static final float SILENT = 0.0005f;

    /** O tick do cliente em que o {@code play} foi pedido (a tolerância do {@link ArmilarLoopLife}). */
    long startedAt;
    private float targetVolume;
    private float targetPitch;
    private boolean ending;

    ArmilarLoopSound(SoundEvent event, double x, double y, double z, float pitch) {
        super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = 0;
        this.pitch = pitch;
        this.targetPitch = pitch;
    }

    /** O volume pode nascer em 0: a rampa sobe dali. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** Muda o alvo do volume e do tom; cancela um {@link #fadeOut()} em andamento. */
    void drive(float volume, float pitch) {
        targetVolume = volume;
        targetPitch = pitch;
        ending = false;
    }

    /** Desce a zero por rampa e para ao chegar. */
    void fadeOut() {
        targetVolume = 0;
        ending = true;
    }

    /** Para na hora, sem rampa. */
    void halt() {
        volume = 0;
        stop();
    }

    @Override
    public void tick() {
        volume += Mth.clamp(targetVolume - volume, -VOLUME_STEP, VOLUME_STEP);
        pitch += Mth.clamp(targetPitch - pitch, -PITCH_STEP, PITCH_STEP);
        if (ending && volume <= SILENT) {
            stop();
        }
    }
}
