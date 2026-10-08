package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: o conteúdo (energia ou Source) do armazenamento da tela aberta e o tick do servidor em que foi lida (a
 * tela calcula a variação por tick entre dois pacotes).
 */
public record ScalarStatePayload(int containerId, long stored, long capacity, long tick) implements CustomPacketPayload {
    public static final Type<ScalarStatePayload> TYPE = new Type<>(WirelessAutomate.id("scalar_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScalarStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScalarStatePayload::containerId,
            ByteBufCodecs.VAR_LONG, ScalarStatePayload::stored,
            ByteBufCodecs.VAR_LONG, ScalarStatePayload::capacity,
            ByteBufCodecs.VAR_LONG, ScalarStatePayload::tick,
            ScalarStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
