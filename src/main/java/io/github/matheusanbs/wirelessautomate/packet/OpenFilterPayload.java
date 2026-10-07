package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/** Cliente → servidor: botão Editar da tela do roteador; abre o filtro da face (absoluta) no lugar dela. */
public record OpenFilterPayload(int containerId, ResourceType resource, Direction face) implements CustomPacketPayload {
    public static final Type<OpenFilterPayload> TYPE = new Type<>(WirelessAutomate.id("open_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFilterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenFilterPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(ResourceType.class), OpenFilterPayload::resource,
            Direction.STREAM_CODEC, OpenFilterPayload::face,
            OpenFilterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
