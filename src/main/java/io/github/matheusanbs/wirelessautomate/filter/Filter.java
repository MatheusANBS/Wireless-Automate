package io.github.matheusanbs.wirelessautomate.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Filtro de uma face (ou de um cartão), imutável. Sem entradas, passa tudo, em qualquer modo.
 * As mudanças devolvem um filtro novo ({@code with...}); duplicados são ignorados e há um teto
 * de {@link #MAX_ENTRIES} entradas para proteger o dado salvo e o pacote de rede.
 *
 * <p>A pergunta "este recurso passa?" é respondida por um matcher compilado em conjuntos de hash
 * e guardado em cache (ver {@code TODO(contrato)} abaixo).
 */
public final class Filter {
    public static final int MAX_ENTRIES = 4096;

    public enum ListMode {
        WHITELIST,
        BLACKLIST
    }

    public static final Filter EMPTY = new Filter(ListMode.WHITELIST, false, List.of());

    private final ListMode listMode;
    /** Itens: exigir componentes iguais (senão picareta encantada = picareta). */
    private final boolean matchComponents;
    private final List<FilterEntry> entries;

    public Filter(ListMode listMode, boolean matchComponents, List<FilterEntry> entries) {
        this.listMode = Objects.requireNonNull(listMode);
        this.matchComponents = matchComponents;
        this.entries = List.copyOf(entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries);
    }

    public ListMode listMode() {
        return listMode;
    }

    public boolean matchComponents() {
        return matchComponents;
    }

    public List<FilterEntry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Sem entradas e nos valores padrão: igual a não ter filtro. */
    public boolean isDefault() {
        return equals(EMPTY);
    }

    public Filter withListMode(ListMode listMode) {
        return new Filter(listMode, matchComponents, entries);
    }

    public Filter withMatchComponents(boolean matchComponents) {
        return new Filter(listMode, matchComponents, entries);
    }

    /** Acrescenta no fim; duplicado (mesmo alvo) ou teto atingido devolve {@code this}. */
    public Filter withEntry(FilterEntry entry) {
        if (entries.size() >= MAX_ENTRIES || indexOf(entry) >= 0) {
            return this;
        }
        List<FilterEntry> list = new ArrayList<>(entries);
        list.add(entry);
        return new Filter(listMode, matchComponents, list);
    }

    public Filter withoutEntry(int index) {
        if (index < 0 || index >= entries.size()) {
            return this;
        }
        List<FilterEntry> list = new ArrayList<>(entries);
        list.remove(index);
        return new Filter(listMode, matchComponents, list);
    }

    public Filter withStock(int index, long stock) {
        if (index < 0 || index >= entries.size()) {
            return this;
        }
        List<FilterEntry> list = new ArrayList<>(entries);
        list.set(index, list.get(index).withStock(stock));
        return new Filter(listMode, matchComponents, list);
    }

    public Filter cleared() {
        return new Filter(listMode, matchComponents, List.of());
    }

    /** Posição de uma entrada com o mesmo alvo, ou −1. */
    public int indexOf(FilterEntry entry) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).sameTarget(entry)) {
                return i;
            }
        }
        return -1;
    }

    // TODO(contrato): correspondência compilada (agente do motor). Sem entradas = passa tudo.

    /** O item passa pelo filtro? */
    public boolean testItem(ItemStack stack) {
        throw new UnsupportedOperationException("TODO");
    }

    /** O fluido passa pelo filtro? */
    public boolean testFluid(FluidStack stack) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Estoque da primeira entrada que casa com o item (lista branca), ou 0 se não houver. */
    public long itemStock(ItemStack stack) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Estoque da primeira entrada que casa com o fluido (lista branca), ou 0 se não houver. */
    public long fluidStock(FluidStack stack) {
        throw new UnsupportedOperationException("TODO");
    }

    public static final Codec<Filter> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(ListMode::valueOf, ListMode::name).optionalFieldOf("list", ListMode.WHITELIST)
                    .forGetter(Filter::listMode),
            Codec.BOOL.optionalFieldOf("components", false).forGetter(Filter::matchComponents),
            FilterEntry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(Filter::entries))
            .apply(i, Filter::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Filter> STREAM_CODEC =
            StreamCodec.of(Filter::encode, Filter::decode);

    private static void encode(RegistryFriendlyByteBuf buf, Filter filter) {
        buf.writeEnum(filter.listMode);
        buf.writeBoolean(filter.matchComponents);
        buf.writeVarInt(filter.entries.size());
        for (FilterEntry entry : filter.entries) {
            FilterEntry.STREAM_CODEC.encode(buf, entry);
        }
    }

    private static Filter decode(RegistryFriendlyByteBuf buf) {
        ListMode listMode = buf.readEnum(ListMode.class);
        boolean matchComponents = buf.readBoolean();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalArgumentException("Filtro com " + size + " entradas");
        }
        List<FilterEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(FilterEntry.STREAM_CODEC.decode(buf));
        }
        return new Filter(listMode, matchComponents, entries);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Filter other && listMode == other.listMode && matchComponents == other.matchComponents
                && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listMode, matchComponents, entries);
    }

    @Override
    public String toString() {
        return "Filter[" + listMode + ", components=" + matchComponents + ", " + entries.size() + " entradas]";
    }
}
