package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente → servidor: a aba do Tablet aberto (a posição na ordem das abas), para reabrir na mesma. */
public record TabletTabPayload(int containerId, int tab) implements CustomPacketPayload {
    public static final Type<TabletTabPayload> TYPE = new Type<>(WirelessAutomate.id("tablet_tab"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletTabPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletTabPayload::containerId,
            ByteBufCodecs.VAR_INT, TabletTabPayload::tab,
            TabletTabPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
