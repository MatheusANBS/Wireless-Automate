package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: o cabeçalho do Tablet aberto (estatísticas, redes, grupos e aviso) mudou. Vai
 * sem a página de nós ({@link TabletSnapshot#HEADER_CODEC}); o cliente mantém a que já tem.
 */
public record TabletHeaderPayload(int containerId, TabletSnapshot header) implements CustomPacketPayload {
    public static final Type<TabletHeaderPayload> TYPE = new Type<>(WirelessAutomate.id("tablet_header"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletHeaderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletHeaderPayload::containerId,
            TabletSnapshot.HEADER_CODEC, TabletHeaderPayload::header,
            TabletHeaderPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
