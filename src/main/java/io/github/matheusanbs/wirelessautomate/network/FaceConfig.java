package io.github.matheusanbs.wirelessautomate.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterCodecs;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

/**
 * Configuração de uma face da máquina para um tipo de recurso: modo, prioridade, redstone e filtro.
 * Mutável: num roteador, mude pelos setters do {@code RouterBlockEntity}, que salvam e avisam o
 * {@link NetworkManager}. Os setters daqui devolvem {@code true} quando o valor mudou.
 */
public final class FaceConfig {
    private static final String MODE = "mode";
    private static final String PRIORITY = "priority";
    private static final String REDSTONE = "redstone";
    private static final String FILTER = "filter";

    private PortMode mode = PortMode.NONE;
    private int priority;
    private RedstoneMode redstone = RedstoneMode.IGNORE;
    private Filter filter = Filter.EMPTY;

    public PortMode mode() {
        return mode;
    }

    public int priority() {
        return priority;
    }

    public RedstoneMode redstone() {
        return redstone;
    }

    public Filter filter() {
        return filter;
    }

    public boolean setFilter(Filter filter) {
        Objects.requireNonNull(filter);
        boolean changed = !this.filter.equals(filter);
        this.filter = filter;
        return changed;
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
        changed |= setFilter(other.filter);
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
        return mode == PortMode.NONE && priority == 0 && redstone == RedstoneMode.IGNORE && filter.isDefault();
    }

    /**
     * Formato salvo (NBT da face, preset do Configurador): só os campos fora do padrão, então uma
     * face padrão vira um mapa vazio. Tolerante: enum com nome inválido ou campo com tipo errado
     * volta ao padrão, e uma entrada de filtro que não lê é descartada ({@link FilterCodecs#LENIENT}).
     * Precisa de {@code RegistryOps} por causa dos componentes das entradas de item e fluido.
     */
    public static final Codec<FaceConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            enumCodec(PortMode.class, PortMode.NONE).lenientOptionalFieldOf(MODE, PortMode.NONE)
                    .forGetter(FaceConfig::mode),
            Codec.INT.lenientOptionalFieldOf(PRIORITY, 0).forGetter(FaceConfig::priority),
            enumCodec(RedstoneMode.class, RedstoneMode.IGNORE).lenientOptionalFieldOf(REDSTONE, RedstoneMode.IGNORE)
                    .forGetter(FaceConfig::redstone),
            FilterCodecs.LENIENT.lenientOptionalFieldOf(FILTER, Filter.EMPTY).forGetter(FaceConfig::filter))
            .apply(i, FaceConfig::of));

    private static FaceConfig of(PortMode mode, int priority, RedstoneMode redstone, Filter filter) {
        FaceConfig config = new FaceConfig();
        config.mode = mode;
        config.priority = priority;
        config.redstone = redstone;
        config.filter = filter;
        return config;
    }

    /** NBT compacto pelo {@link #CODEC}: uma face padrão vira uma tag vazia. */
    public CompoundTag save(HolderLookup.Provider registries) {
        return CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this)
                .resultOrPartial(error -> WirelessAutomate.LOGGER.error("Falha ao salvar a face {}: {}", this, error))
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .orElseGet(CompoundTag::new);
    }

    /** Lê o formato de {@link #save}; também lê as tags antigas, sem filtro. Campo ausente ou inválido fica no padrão. */
    public static FaceConfig load(CompoundTag tag, HolderLookup.Provider registries) {
        return CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .resultOrPartial(error -> WirelessAutomate.LOGGER.warn("Face com dados inválidos ({}): {}", error, tag))
                .orElseGet(FaceConfig::new);
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type, E fallback) {
        return Codec.STRING.xmap(name -> parse(type, name, fallback), Enum::name);
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
                && mode == other.mode && priority == other.priority && redstone == other.redstone
                && filter.equals(other.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, priority, redstone, filter);
    }

    @Override
    public String toString() {
        return "FaceConfig[" + mode + ", priority=" + priority + ", redstone=" + redstone + ", " + filter + "]";
    }
}
