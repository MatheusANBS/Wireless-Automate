package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.FilterView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor → cliente: o filtro da tela aberta mudou. */
public record FilterViewPayload(int containerId, FilterView view) implements CustomPacketPayload {
    public static final Type<FilterViewPayload> TYPE = new Type<>(WirelessAutomate.id("filter_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FilterViewPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FilterViewPayload::containerId,
            FilterView.STREAM_CODEC, FilterViewPayload::view,
            FilterViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
