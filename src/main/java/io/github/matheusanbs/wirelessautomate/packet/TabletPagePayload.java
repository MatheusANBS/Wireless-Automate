package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor → cliente: a página de nós do Tablet aberto mudou (consulta, totais ou nós). */
public record TabletPagePayload(int containerId, TabletSnapshot.Page page) implements CustomPacketPayload {
    public static final Type<TabletPagePayload> TYPE = new Type<>(WirelessAutomate.id("tablet_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletPagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletPagePayload::containerId,
            TabletSnapshot.Page.STREAM_CODEC, TabletPagePayload::page,
            TabletPagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
