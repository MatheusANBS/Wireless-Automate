package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor → cliente: o estado novo da tela do Vinculador aberta. */
public record LinkerSnapshotPayload(int containerId, LinkerSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<LinkerSnapshotPayload> TYPE = new Type<>(WirelessAutomate.id("linker_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LinkerSnapshotPayload::containerId,
            LinkerSnapshot.STREAM_CODEC, LinkerSnapshotPayload::snapshot,
            LinkerSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
