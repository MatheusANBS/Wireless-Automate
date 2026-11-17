package io.github.matheusanbs.wirelessautomate.resonator;

import net.minecraft.nbt.CompoundTag;

/**
 * O que a última montagem achou na casca do Núcleo (Sintonizador e Portas) e o tempo dela; a ligação do
 * Sintonizador fica fixa até a próxima montagem. Chaves de NBT {@code has_tuner}, {@code energy_ports},
 * {@code crystal_ports} e {@code formed_at}.
 */
final class CoreShell {
    private boolean hasTuner;
    private int energyPorts;
    private int crystalPorts;
    /**
     * O tempo de jogo da última montagem, ou -1 se nunca montou; 0 num Núcleo que já estava montado antes de o tempo
     * ser gravado (a sequência de acender fica no passado).
     */
    private long formedAt = -1;

    /**
     * Guarda a casca achada e devolve se algo mudou. O tempo só muda quando a estrutura estava desmontada
     * ({@code formed} falso); montada e sem tempo gravado, ganha 0.
     */
    boolean set(boolean hasTuner, int energyPorts, int crystalPorts, boolean formed, long gameTime) {
        boolean changed = this.hasTuner != hasTuner || this.energyPorts != energyPorts
                || this.crystalPorts != crystalPorts;
        this.hasTuner = hasTuner;
        this.energyPorts = energyPorts;
        this.crystalPorts = crystalPorts;
        if (!formed) {
            formedAt = gameTime;
            changed = true;
        } else if (formedAt < 0) {
            formedAt = 0;
            changed = true;
        }
        return changed;
    }

    boolean hasTuner() {
        return hasTuner;
    }

    int energyPorts() {
        return energyPorts;
    }

    int crystalPorts() {
        return crystalPorts;
    }

    long formedAt() {
        return formedAt;
    }

    void save(CompoundTag tag) {
        tag.putBoolean("has_tuner", hasTuner);
        tag.putInt("energy_ports", energyPorts);
        tag.putInt("crystal_ports", crystalPorts);
        tag.putLong("formed_at", formedAt);
    }

    void load(CompoundTag tag) {
        hasTuner = tag.getBoolean("has_tuner");
        energyPorts = Math.max(0, tag.getInt("energy_ports"));
        crystalPorts = Math.max(0, tag.getInt("crystal_ports"));
        formedAt = tag.contains("formed_at") ? tag.getLong("formed_at") : -1;
    }
}
