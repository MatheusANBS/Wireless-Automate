package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: regras montadas na tela de filtro. Com {@code replace} = −1, acrescenta as
 * entradas (as tags marcadas no inspetor, um mod da busca, uma regra nova); senão, troca a entrada
 * {@code replace} pela única da lista (editar uma regra), mantendo o estoque dela. Aceita tags, mods
 * e regras ({@link FilterEntry.TagEntry}, {@link FilterEntry.ModEntry}, {@link FilterEntry.RuleEntry})
 * que valham para o tipo do filtro; o servidor valida cada uma e zera o estoque das novas.
 */
public record FilterEntriesPayload(int containerId, int replace, List<FilterEntry> entries) implements CustomPacketPayload {
    /** Entradas por pacote: uma tela cheia de tags marcadas cabe com folga. */
    public static final int MAX = 64;

    public static final Type<FilterEntriesPayload> TYPE = new Type<>(WirelessAutomate.id("filter_entries"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FilterEntriesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FilterEntriesPayload::containerId,
            ByteBufCodecs.VAR_INT, FilterEntriesPayload::replace,
            FilterEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX)), FilterEntriesPayload::entries,
            FilterEntriesPayload::new);

    public static FilterEntriesPayload add(int containerId, List<FilterEntry> entries) {
        return new FilterEntriesPayload(containerId, -1, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
