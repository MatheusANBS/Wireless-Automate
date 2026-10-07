package io.github.matheusanbs.wirelessautomate.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Filtro de uma face (ou de um cartão), imutável. Sem entradas, passa tudo, em qualquer modo.
 * As mudanças devolvem um filtro novo ({@code with...}); duplicados são ignorados e há um teto
 * de {@link #MAX_ENTRIES} entradas para proteger o dado salvo e o pacote de rede.
 *
 * <p>A pergunta "este recurso passa?" é respondida por um matcher compilado em conjuntos de hash
 * e guardado no próprio filtro ({@link CompiledMatcher}); como o filtro é imutável, o matcher só
 * é refeito quando o filtro é trocado, e o cache dele quando as tags recarregam.
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

    // Correspondência: compilada na primeira consulta de cada tipo (ver CompiledMatcher) e guardada
    // aqui. Não entra em equals/hashCode nem nos codecs. Uma entrada que não vale para o tipo
    // (fluido num filtro de itens) não casa com nada, mas conta para "o filtro tem entradas".

    private @Nullable ItemMatcher itemMatcher;
    private @Nullable FluidMatcher fluidMatcher;
    private @Nullable ChemicalMatcher chemicalMatcher;

    private ItemMatcher items() {
        ItemMatcher matcher = itemMatcher;
        if (matcher == null) {
            matcher = new ItemMatcher(entries, matchComponents);
            itemMatcher = matcher;
        }
        return matcher;
    }

    private FluidMatcher fluids() {
        FluidMatcher matcher = fluidMatcher;
        if (matcher == null) {
            matcher = new FluidMatcher(entries, matchComponents);
            fluidMatcher = matcher;
        }
        return matcher;
    }

    private ChemicalMatcher chemicals() {
        ChemicalMatcher matcher = chemicalMatcher;
        if (matcher == null) {
            matcher = new ChemicalMatcher(entries);
            chemicalMatcher = matcher;
        }
        return matcher;
    }

    private @Nullable FilterSet asSet;

    /** Este filtro sozinho como conjunto (face sem cartões), criado uma vez e guardado aqui. */
    public FilterSet asSet() {
        FilterSet set = asSet;
        if (set == null) {
            set = FilterSet.single(this);
            asSet = set;
        }
        return set;
    }

    /** Alguma entrada casa com o item (sem olhar o modo da lista). */
    boolean matchesItem(ItemStack stack) {
        return !entries.isEmpty() && items().index(stack) >= 0;
    }

    /** Alguma entrada casa com o fluido (sem olhar o modo da lista). */
    boolean matchesFluid(FluidStack stack) {
        return !entries.isEmpty() && fluids().index(stack) >= 0;
    }

    /** Alguma entrada casa com o químico de id {@code chemical} (sem olhar o modo da lista). */
    boolean matchesChemical(ResourceLocation chemical) {
        return !entries.isEmpty() && chemicals().index(chemical) >= 0;
    }

    /** O item passa pelo filtro? */
    public boolean testItem(ItemStack stack) {
        if (entries.isEmpty()) {
            return true;
        }
        return (items().index(stack) >= 0) == (listMode == ListMode.WHITELIST);
    }

    /** O fluido passa pelo filtro? */
    public boolean testFluid(FluidStack stack) {
        if (entries.isEmpty()) {
            return true;
        }
        return (fluids().index(stack) >= 0) == (listMode == ListMode.WHITELIST);
    }

    /** O químico (pelo id) passa pelo filtro? */
    public boolean testChemical(ResourceLocation chemical) {
        if (entries.isEmpty()) {
            return true;
        }
        return (chemicals().index(chemical) >= 0) == (listMode == ListMode.WHITELIST);
    }

    /** Estoque da primeira entrada que casa com o item (lista branca), ou 0 se não houver. */
    public long itemStock(ItemStack stack) {
        if (!usesItemStock()) {
            return 0;
        }
        int index = items().index(stack);
        return index >= 0 ? entries.get(index).stock() : 0;
    }

    /** Estoque da primeira entrada que casa com o fluido (lista branca), ou 0 se não houver. */
    public long fluidStock(FluidStack stack) {
        if (!usesFluidStock()) {
            return 0;
        }
        int index = fluids().index(stack);
        return index >= 0 ? entries.get(index).stock() : 0;
    }

    /** Estoque da primeira entrada que casa com o químico (lista branca), ou 0 se não houver. */
    public long chemicalStock(ResourceLocation chemical) {
        if (!usesChemicalStock()) {
            return 0;
        }
        int index = chemicals().index(chemical);
        return index >= 0 ? entries.get(index).stock() : 0;
    }

    /** Lista branca com alguma entrada de item (exata, tag ou mod) com estoque. Barato depois da primeira vez. */
    public boolean usesItemStock() {
        return listMode == ListMode.WHITELIST && !entries.isEmpty() && items().usesStock;
    }

    /** Lista branca com alguma entrada de fluido (exata, tag ou mod) com estoque. */
    public boolean usesFluidStock() {
        return listMode == ListMode.WHITELIST && !entries.isEmpty() && fluids().usesStock;
    }

    /** Lista branca com alguma entrada de químico (exata ou mod) com estoque. */
    public boolean usesChemicalStock() {
        return listMode == ListMode.WHITELIST && !entries.isEmpty() && chemicals().usesStock;
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
