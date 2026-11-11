package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: o cliente segura o Vinculador com a Doca em {@code dock} escolhida e não tem o cubo dela (entrou
 * no mundo de novo, ou o prazo de colocar venceu). O servidor confere e responde com o {@link DockReachPayload}
 * ({@code dock/DockReachSync#request}). O cliente pede no máximo uma vez por segundo.
 */
public record DockReachRequestPayload(BlockPos dock) implements CustomPacketPayload {
    public static final Type<DockReachRequestPayload> TYPE = new Type<>(WirelessAutomate.id("dock_reach_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DockReachRequestPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, DockReachRequestPayload::dock, DockReachRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
