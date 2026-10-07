package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor → cliente: o que o Tablet aberto mostra mudou. */
public record TabletSnapshotPayload(int containerId, TabletSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<TabletSnapshotPayload> TYPE = new Type<>(WirelessAutomate.id("tablet_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletSnapshotPayload::containerId,
            TabletSnapshot.STREAM_CODEC, TabletSnapshotPayload::snapshot,
            TabletSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
