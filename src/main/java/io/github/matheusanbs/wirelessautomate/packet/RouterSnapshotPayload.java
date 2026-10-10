package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.net.ByteBufCodecs;
import io.github.matheusanbs.wirelessautomate.net.CustomPacketPayload;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;

/**
 * Servidor → cliente: o roteador da tela aberta mudou. Vai sem as redes do seletor
 * ({@link RouterSnapshot#BODY_CODEC}); elas vêm no {@link RouterNetworksPayload}, só quando mudam.
 */
public record RouterSnapshotPayload(int containerId, RouterSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<RouterSnapshotPayload> TYPE = new Type<>(WirelessAutomate.id("router_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouterSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RouterSnapshotPayload::containerId,
            RouterSnapshot.BODY_CODEC, RouterSnapshotPayload::snapshot,
            RouterSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
