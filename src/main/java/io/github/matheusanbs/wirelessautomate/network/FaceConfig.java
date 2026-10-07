package io.github.matheusanbs.wirelessautomate.network;

import java.util.Locale;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/**
 * Configuração de uma face da máquina para um tipo de recurso: modo, prioridade e redstone.
 * Mutável: num roteador, mude pelos setters do {@code RouterBlockEntity}, que salvam e avisam o
 * {@link NetworkManager}. Os setters daqui devolvem {@code true} quando o valor mudou.
 */
public final class FaceConfig {
    private static final String MODE = "mode";
    private static final String PRIORITY = "priority";
    private static final String REDSTONE = "redstone";

    private PortMode mode = PortMode.NONE;
    private int priority;
    private RedstoneMode redstone = RedstoneMode.IGNORE;
    // Filtro: entra aqui como mais um campo, salvo em save()/load() e considerado em isDefault().

    public PortMode mode() {
        return mode;
    }

    public int priority() {
        return priority;
    }

    public RedstoneMode redstone() {
        return redstone;
    }

    public boolean setMode(PortMode mode) {
        Objects.requireNonNull(mode);
        boolean changed = this.mode != mode;
        this.mode = mode;
        return changed;
    }

    public boolean setPriority(int priority) {
        boolean changed = this.priority != priority;
        this.priority = priority;
        return changed;
    }

    public boolean setRedstone(RedstoneMode redstone) {
        Objects.requireNonNull(redstone);
        boolean changed = this.redstone != redstone;
        this.redstone = redstone;
        return changed;
    }

    /** Copia todos os campos de {@code other}. Devolve {@code true} se algo mudou. */
    public boolean copyFrom(FaceConfig other) {
        boolean changed = setMode(other.mode);
        changed |= setPriority(other.priority);
        changed |= setRedstone(other.redstone);
        return changed;
    }

    /** Volta aos valores padrão. Devolve {@code true} se algo mudou. */
    public boolean reset() {
        return copyFrom(new FaceConfig());
    }

    /** Modo diferente de NONE e redstone permitindo. */
    public boolean isActive(boolean powered) {
        return mode != PortMode.NONE && redstone.allows(powered);
    }

    public boolean isDefault() {
        return mode == PortMode.NONE && priority == 0 && redstone == RedstoneMode.IGNORE;
    }

    /** NBT compacto: só os campos fora do padrão; uma face padrão vira uma tag vazia. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (mode != PortMode.NONE) {
            tag.putString(MODE, mode.name());
        }
        if (priority != 0) {
            tag.putInt(PRIORITY, priority);
        }
        if (redstone != RedstoneMode.IGNORE) {
            tag.putString(REDSTONE, redstone.name());
        }
        return tag;
    }

    /** Lê o formato de {@link #save()}. Campo ausente ou nome de enum inválido fica no padrão. */
    public static FaceConfig load(CompoundTag tag) {
        FaceConfig config = new FaceConfig();
        config.mode = parse(PortMode.class, tag.getString(MODE), PortMode.NONE);
        config.priority = tag.getInt(PRIORITY);
        config.redstone = parse(RedstoneMode.class, tag.getString(REDSTONE), RedstoneMode.IGNORE);
        return config;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        if (name.isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FaceConfig other
                && mode == other.mode && priority == other.priority && redstone == other.redstone;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, priority, redstone);
    }

    @Override
    public String toString() {
        return "FaceConfig[" + mode + ", priority=" + priority + ", redstone=" + redstone + "]";
    }
}
