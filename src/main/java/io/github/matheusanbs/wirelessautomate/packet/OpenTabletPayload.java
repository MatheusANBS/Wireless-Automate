package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente → servidor: a tecla de atalho do Tablet. O servidor abre se o jogador tem um Tablet no inventário. */
public record OpenTabletPayload() implements CustomPacketPayload {
    public static final OpenTabletPayload INSTANCE = new OpenTabletPayload();
    public static final Type<OpenTabletPayload> TYPE = new Type<>(WirelessAutomate.id("open_tablet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTabletPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
