package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: o cliente segura o Vinculador com o roteador ({@link LinkerReachPayload#ROUTER}) ou a Doca
 * ({@link LinkerReachPayload#DOCK}) em {@code pos} escolhido e não tem o cubo dele (entrou no mundo de novo, ou o prazo
 * de colocar venceu). O servidor confere e responde com o {@link LinkerReachPayload}
 * ({@code range/RouterReachSync#request}, {@code dock/DockReachSync#request}). O cliente pede no máximo uma vez por
 * segundo.
 */
public record LinkerReachRequestPayload(byte kind, BlockPos pos) implements CustomPacketPayload {
    public static final Type<LinkerReachRequestPayload> TYPE =
            new Type<>(WirelessAutomate.id("linker_reach_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerReachRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BYTE, LinkerReachRequestPayload::kind, BlockPos.STREAM_CODEC,
                    LinkerReachRequestPayload::pos, LinkerReachRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
