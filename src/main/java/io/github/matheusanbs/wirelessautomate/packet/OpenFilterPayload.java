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

/** Cliente → servidor: botão Editar da tela do roteador; abre o filtro da face (absoluta) no lugar dela. */
public record OpenFilterPayload(int containerId, ResourceType resource, Direction face) implements CustomPacketPayload {
    public static final Type<OpenFilterPayload> TYPE = new Type<>(WirelessAutomate.id("open_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFilterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenFilterPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(ResourceType.class), OpenFilterPayload::resource,
            GameCodecs.DIRECTION, OpenFilterPayload::face,
            OpenFilterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
