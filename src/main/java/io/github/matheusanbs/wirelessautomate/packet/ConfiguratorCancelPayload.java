package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: a pintura da proposta do Afinador sumiu no cliente porque o jogador mirou outro bloco
 * ({@code TunerProposal}), então a proposta pendente dessa máquina some no servidor também e o próximo clique nela
 * refaz a análise. Um pacote por proposta apagada, nunca por tick.
 *
 * @param machine a máquina da proposta apagada; uma proposta pendente de outra máquina fica
 */
public record ConfiguratorCancelPayload(BlockPos machine) implements CustomPacketPayload {
    public static final Type<ConfiguratorCancelPayload> TYPE = new Type<>(WirelessAutomate.id("configurator_cancel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfiguratorCancelPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, ConfiguratorCancelPayload::machine,
                    ConfiguratorCancelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
