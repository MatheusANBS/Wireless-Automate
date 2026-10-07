package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor → cliente: a visão nova da tela do Configurador aberta. */
public record ConfiguratorViewPayload(int containerId, ConfiguratorView view) implements CustomPacketPayload {
    public static final Type<ConfiguratorViewPayload> TYPE = new Type<>(WirelessAutomate.id("configurator_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfiguratorViewPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ConfiguratorViewPayload::containerId,
                    ConfiguratorView.STREAM_CODEC, ConfiguratorViewPayload::view,
                    ConfiguratorViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
