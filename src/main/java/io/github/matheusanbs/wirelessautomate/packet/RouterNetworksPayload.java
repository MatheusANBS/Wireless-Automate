package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: as redes do seletor da tela do roteador aberta mudaram. Vão à parte do
 * {@link RouterSnapshotPayload}, porque a lista (com as públicas de todos) pode ser grande e muda pouco.
 */
public record RouterNetworksPayload(int containerId, List<RouterSnapshot.NetworkEntry> networks)
        implements CustomPacketPayload {
    public static final Type<RouterNetworksPayload> TYPE = new Type<>(WirelessAutomate.id("router_networks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouterNetworksPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RouterNetworksPayload::containerId,
            RouterSnapshot.NETWORKS_CODEC, RouterNetworksPayload::networks,
            RouterNetworksPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
