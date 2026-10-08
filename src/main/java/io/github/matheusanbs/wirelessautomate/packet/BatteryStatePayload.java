package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: a energia da Bateria da tela aberta e o tick do servidor em que foi lida (a
 * tela calcula a variação por tick entre dois pacotes).
 */
public record BatteryStatePayload(int containerId, long stored, long capacity, long tick) implements CustomPacketPayload {
    public static final Type<BatteryStatePayload> TYPE = new Type<>(WirelessAutomate.id("battery_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BatteryStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BatteryStatePayload::containerId,
            ByteBufCodecs.VAR_LONG, BatteryStatePayload::stored,
            ByteBufCodecs.VAR_LONG, BatteryStatePayload::capacity,
            ByteBufCodecs.VAR_LONG, BatteryStatePayload::tick,
            BatteryStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
