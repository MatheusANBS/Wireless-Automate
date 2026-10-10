package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.net.ByteBufCodecs;
import io.github.matheusanbs.wirelessautomate.net.CustomPacketPayload;
import io.github.matheusanbs.wirelessautomate.net.NeoForgeStreamCodecs;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.preset.PasteMode;
import java.util.Optional;

/**
 * Cliente → servidor: a escolha na roda do Configurador, com o estado completo (o modo de colar e o
 * tipo colado; vazio = Todos). O servidor confere o Configurador na mão principal e o tipo.
 */
public record ConfiguratorWheelPayload(PasteMode mode, Optional<ResourceType> pasteType) implements CustomPacketPayload {
    public static final Type<ConfiguratorWheelPayload> TYPE = new Type<>(WirelessAutomate.id("configurator_wheel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfiguratorWheelPayload> STREAM_CODEC =
            StreamCodec.composite(
                    NeoForgeStreamCodecs.enumCodec(PasteMode.class), ConfiguratorWheelPayload::mode,
                    ByteBufCodecs.optional(NeoForgeStreamCodecs.enumCodec(ResourceType.class)),
                    ConfiguratorWheelPayload::pasteType,
                    ConfiguratorWheelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
