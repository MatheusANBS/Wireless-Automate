package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente → servidor: muda a rede do roteador da tela aberta (vazio = sem rede). */
public record SetNetworkPayload(int containerId, Optional<UUID> network) implements CustomPacketPayload {
    public static final Type<SetNetworkPayload> TYPE = new Type<>(WirelessAutomate.id("set_network"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetNetworkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetNetworkPayload::containerId,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), SetNetworkPayload::network,
            SetNetworkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
