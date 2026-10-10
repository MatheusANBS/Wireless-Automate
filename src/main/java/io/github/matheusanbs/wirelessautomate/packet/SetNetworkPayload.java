package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.net.ByteBufCodecs;
import io.github.matheusanbs.wirelessautomate.net.CustomPacketPayload;
import io.github.matheusanbs.wirelessautomate.net.GameCodecs;
import io.github.matheusanbs.wirelessautomate.net.NeoForgeStreamCodecs;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.Optional;
import java.util.UUID;

/** Cliente → servidor: muda a rede de uma aba (tipo) do roteador da tela aberta (vazio = sem rede). */
public record SetNetworkPayload(int containerId, ResourceType resource, Optional<UUID> network)
        implements CustomPacketPayload {
    public static final Type<SetNetworkPayload> TYPE = new Type<>(WirelessAutomate.id("set_network"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetNetworkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetNetworkPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(ResourceType.class), SetNetworkPayload::resource,
            ByteBufCodecs.optional(GameCodecs.UUID), SetNetworkPayload::network,
            SetNetworkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
