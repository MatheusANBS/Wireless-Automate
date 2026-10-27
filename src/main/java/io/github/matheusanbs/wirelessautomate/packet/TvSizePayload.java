package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente → servidor: o tamanho e a âncora pedidos para a Tela de Sinal da tela aberta. O servidor valida tudo. */
public record TvSizePayload(int containerId, int width, int height, int anchorCol, int anchorRow)
        implements CustomPacketPayload {
    public static final Type<TvSizePayload> TYPE = new Type<>(WirelessAutomate.id("tv_size"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TvSizePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TvSizePayload::containerId,
            ByteBufCodecs.VAR_INT, TvSizePayload::width,
            ByteBufCodecs.VAR_INT, TvSizePayload::height,
            ByteBufCodecs.VAR_INT, TvSizePayload::anchorCol,
            ByteBufCodecs.VAR_INT, TvSizePayload::anchorRow,
            TvSizePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
