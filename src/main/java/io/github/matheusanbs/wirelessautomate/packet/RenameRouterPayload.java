package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente → servidor: dá nome ao nó da tela aberta (vazio = sem nome). No máximo {@link #MAX_LENGTH}. */
public record RenameRouterPayload(int containerId, String name) implements CustomPacketPayload {
    public static final int MAX_LENGTH = 32;
    public static final Type<RenameRouterPayload> TYPE = new Type<>(WirelessAutomate.id("rename_router"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RenameRouterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RenameRouterPayload::containerId,
            ByteBufCodecs.stringUtf8(64), RenameRouterPayload::name,
            RenameRouterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
