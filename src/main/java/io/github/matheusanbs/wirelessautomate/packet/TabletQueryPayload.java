package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente → servidor: busca, filtro por papel ou página da lista do Tablet aberto. */
public record TabletQueryPayload(int containerId, TabletSnapshot.Query query) implements CustomPacketPayload {
    public static final Type<TabletQueryPayload> TYPE = new Type<>(WirelessAutomate.id("tablet_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletQueryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletQueryPayload::containerId,
            TabletSnapshot.Query.STREAM_CODEC, TabletQueryPayload::query,
            TabletQueryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
