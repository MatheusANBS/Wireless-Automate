package io.github.matheusanbs.wirelessautomate.network;

import net.minecraft.nbt.CompoundTag;

/**
 * Configuração de uma face da máquina para um tipo de recurso: modo, prioridade e redstone.
 * TODO(contrato): implementar. O filtro entra aqui depois.
 */
public final class FaceConfig {
    public PortMode mode() {
        throw new UnsupportedOperationException("TODO");
    }

    public int priority() {
        throw new UnsupportedOperationException("TODO");
    }

    public RedstoneMode redstone() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Modo diferente de NONE e redstone permitindo. */
    public boolean isActive(boolean powered) {
        throw new UnsupportedOperationException("TODO");
    }

    public boolean isDefault() {
        throw new UnsupportedOperationException("TODO");
    }

    public CompoundTag save() {
        throw new UnsupportedOperationException("TODO");
    }

    public static FaceConfig load(CompoundTag tag) {
        throw new UnsupportedOperationException("TODO");
    }
}
