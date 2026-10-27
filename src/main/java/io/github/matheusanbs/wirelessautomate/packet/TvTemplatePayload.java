package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: aplica o modelo "Painel de rede" na Tela da tela aberta, com a rede e o tipo de recurso (a
 * chave do {@code ResourceType}); sem rede, limpa o layout.
 */
public record TvTemplatePayload(int containerId, Optional<UUID> network, String typeKey)
        implements CustomPacketPayload {
    public static final Type<TvTemplatePayload> TYPE = new Type<>(WirelessAutomate.id("tv_template"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TvTemplatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TvTemplatePayload::containerId,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), TvTemplatePayload::network,
            ByteBufCodecs.stringUtf8(32), TvTemplatePayload::typeKey,
            TvTemplatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
