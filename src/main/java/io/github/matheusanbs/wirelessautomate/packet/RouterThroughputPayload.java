package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: vazão atual do roteador da tela aberta, por {@link ResourceType#ordinal()}
 * (itens/s, mB/s, FE/t). Enviado no máximo uma vez por segundo e só quando muda.
 */
public record RouterThroughputPayload(int containerId, long[] perType) implements CustomPacketPayload {
    public static final Type<RouterThroughputPayload> TYPE = new Type<>(WirelessAutomate.id("router_throughput"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouterThroughputPayload> STREAM_CODEC =
            StreamCodec.of(RouterThroughputPayload::encode, RouterThroughputPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, RouterThroughputPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeLongArray(payload.perType);
    }

    private static RouterThroughputPayload decode(RegistryFriendlyByteBuf buf) {
        return new RouterThroughputPayload(buf.readVarInt(), buf.readLongArray(null, ResourceType.values().length));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
