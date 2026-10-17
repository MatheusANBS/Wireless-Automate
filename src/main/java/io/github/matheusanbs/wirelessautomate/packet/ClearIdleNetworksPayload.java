package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: "Tirar a rede das faces sem modo" no alvo de edição do painel (um grupo ou uma máquina
 * com exceção) ou, com {@code undo}, o Desfazer do aviso dele.
 */
public record ClearIdleNetworksPayload(int containerId, boolean undo) implements CustomPacketPayload {
    public static final Type<ClearIdleNetworksPayload> TYPE = new Type<>(WirelessAutomate.id("clear_idle_networks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClearIdleNetworksPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ClearIdleNetworksPayload::containerId,
                    ByteBufCodecs.BOOL, ClearIdleNetworksPayload::undo,
                    ClearIdleNetworksPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
