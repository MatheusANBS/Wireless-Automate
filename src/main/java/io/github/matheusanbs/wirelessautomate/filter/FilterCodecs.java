package io.github.matheusanbs.wirelessautomate.filter;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Codec tolerante do {@link Filter}, para dados salvos no mundo e em itens: mesmo formato do
 * {@link Filter#CODEC}, mas uma entrada que não lê (item de um mod removido, tipo desconhecido) é
 * descartada com aviso no log, em vez de derrubar o filtro inteiro, e campos inválidos voltam ao padrão.
 * Porte 1.20.1: funciona com o {@code NbtOps} puro (as entradas de item e fluido salvam o NBT da pilha); o
 * {@code optionalFieldOf} do DFU 6 já é tolerante (um campo inválido volta ao padrão).
 */
public final class FilterCodecs {
    private static final Codec<Filter.ListMode> LIST_MODE = Codec.STRING.xmap(FilterCodecs::parseListMode,
            Filter.ListMode::name);

    private static final Codec<List<FilterEntry>> LENIENT_ENTRIES = new Codec<>() {
        @Override
        public <T> DataResult<Pair<List<FilterEntry>, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getList(input).map(stream -> {
                List<FilterEntry> entries = new ArrayList<>();
                stream.accept(element -> {
                    if (entries.size() >= Filter.MAX_ENTRIES) {
                        return;
                    }
                    DataResult<FilterEntry> result;
                    try {
                        result = FilterEntry.CODEC.parse(ops, element);
                    } catch (RuntimeException e) {
                        result = DataResult.error(e::getMessage);
                    }
                    result.resultOrPartial(error -> WirelessAutomate.LOGGER.warn(
                            "Entrada de filtro ignorada ({}): {}", error, element)).ifPresent(entries::add);
                });
                return Pair.of(entries, input);
            });
        }

        @Override
        public <T> DataResult<T> encode(List<FilterEntry> input, DynamicOps<T> ops, T prefix) {
            return FilterEntry.CODEC.listOf().encode(input, ops, prefix);
        }
    };

    public static final Codec<Filter> LENIENT = RecordCodecBuilder.create(i -> i.group(
            LIST_MODE.optionalFieldOf("list", Filter.ListMode.WHITELIST).forGetter(Filter::listMode),
            Codec.BOOL.optionalFieldOf("components", false).forGetter(Filter::matchComponents),
            LENIENT_ENTRIES.optionalFieldOf("entries", List.of()).forGetter(Filter::entries))
            .apply(i, Filter::new));

    private static Filter.ListMode parseListMode(String name) {
        try {
            return Filter.ListMode.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Filter.ListMode.WHITELIST;
        }
    }

    private FilterCodecs() {
    }
}
