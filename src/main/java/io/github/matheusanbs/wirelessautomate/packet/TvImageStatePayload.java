package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: se as imagens enviadas estão ligadas ({@code tv.images.enabled}). Vai na entrada de quem chega
 * com elas desligadas e a todos quando a config recarregada as liga ou desliga ({@code TvImageState}), para as Telas à
 * vista trocarem a imagem pelo quadro neutro (ou voltarem) na hora, sem esperar o update tag de cada uma.
 */
public record TvImageStatePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<TvImageStatePayload> TYPE = new Type<>(WirelessAutomate.id("tv_image_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TvImageStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TvImageStatePayload::enabled, TvImageStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
