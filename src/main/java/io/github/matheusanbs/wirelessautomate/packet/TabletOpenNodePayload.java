package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.net.ByteBufCodecs;
import io.github.matheusanbs.wirelessautomate.net.CustomPacketPayload;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;

/** Cliente → servidor: abrir a tela de um nó à distância pelo Tablet aberto. */
public record TabletOpenNodePayload(int containerId, NodeKey node) implements CustomPacketPayload {
    public static final Type<TabletOpenNodePayload> TYPE = new Type<>(WirelessAutomate.id("tablet_open_node"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletOpenNodePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletOpenNodePayload::containerId,
            NodeKey.STREAM_CODEC.<RegistryFriendlyByteBuf>cast(), TabletOpenNodePayload::node,
            TabletOpenNodePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
