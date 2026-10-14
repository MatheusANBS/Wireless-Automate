package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente → servidor: o nó que o painel do Tablet aberto mostra e edita; vazio, nenhum. */
public record TabletSelectNodePayload(int containerId, Optional<NodeKey> node) implements CustomPacketPayload {
    public static final Type<TabletSelectNodePayload> TYPE = new Type<>(WirelessAutomate.id("tablet_select_node"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletSelectNodePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletSelectNodePayload::containerId,
            ByteBufCodecs.optional(NodeKey.STREAM_CODEC.<RegistryFriendlyByteBuf>cast()), TabletSelectNodePayload::node,
            TabletSelectNodePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
