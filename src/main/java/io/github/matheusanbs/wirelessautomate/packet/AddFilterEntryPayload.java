package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.net.ByteBufCodecs;
import io.github.matheusanbs.wirelessautomate.net.CustomPacketPayload;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;

/**
 * Cliente → servidor: acrescenta ao filtro da tela aberta um item ou fluido exato que o jogador não
 * precisa ter (ingrediente fantasma do JEI). Só {@link FilterEntry.ItemEntry} num filtro de itens e
 * {@link FilterEntry.FluidEntry} num de fluidos; o servidor normaliza (quantidade 1, sem estoque).
 */
public record AddFilterEntryPayload(int containerId, FilterEntry entry) implements CustomPacketPayload {
    public static final Type<AddFilterEntryPayload> TYPE = new Type<>(WirelessAutomate.id("add_filter_entry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AddFilterEntryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AddFilterEntryPayload::containerId,
            FilterEntry.STREAM_CODEC, AddFilterEntryPayload::entry,
            AddFilterEntryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
