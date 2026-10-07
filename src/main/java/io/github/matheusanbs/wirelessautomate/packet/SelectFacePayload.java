package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: a tela do roteador mudou de aba ou de face (absoluta); os slots de Cartão de
 * Filtro do menu passam a mostrar os daquela face e tipo.
 */
public record SelectFacePayload(int containerId, ResourceType resource, Direction face) implements CustomPacketPayload {
    public static final Type<SelectFacePayload> TYPE = new Type<>(WirelessAutomate.id("select_face"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectFacePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectFacePayload::containerId,
            NeoForgeStreamCodecs.enumCodec(ResourceType.class), SelectFacePayload::resource,
            Direction.STREAM_CODEC, SelectFacePayload::face,
            SelectFacePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
