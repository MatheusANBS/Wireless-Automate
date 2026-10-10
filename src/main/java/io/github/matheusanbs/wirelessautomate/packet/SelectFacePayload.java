package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.net.ByteBufCodecs;
import io.github.matheusanbs.wirelessautomate.net.CustomPacketPayload;
import io.github.matheusanbs.wirelessautomate.net.GameCodecs;
import io.github.matheusanbs.wirelessautomate.net.NeoForgeStreamCodecs;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.core.Direction;

/**
 * Cliente → servidor: a tela do roteador mudou de aba ou de face (absoluta); os slots de Cartão de
 * Filtro do menu passam a mostrar os daquela face e tipo.
 */
public record SelectFacePayload(int containerId, ResourceType resource, Direction face) implements CustomPacketPayload {
    public static final Type<SelectFacePayload> TYPE = new Type<>(WirelessAutomate.id("select_face"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectFacePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectFacePayload::containerId,
            NeoForgeStreamCodecs.enumCodec(ResourceType.class), SelectFacePayload::resource,
            GameCodecs.DIRECTION, SelectFacePayload::face,
            SelectFacePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
