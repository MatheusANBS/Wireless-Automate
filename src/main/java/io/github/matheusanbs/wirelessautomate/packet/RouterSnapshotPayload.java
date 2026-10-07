package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor → cliente: o roteador da tela aberta mudou. */
public record RouterSnapshotPayload(int containerId, RouterSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<RouterSnapshotPayload> TYPE = new Type<>(WirelessAutomate.id("router_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouterSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RouterSnapshotPayload::containerId,
            RouterSnapshot.STREAM_CODEC, RouterSnapshotPayload::snapshot,
            RouterSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
