package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente → servidor: o dial pedido na tela do Núcleo; o servidor põe no passo e na faixa, e recusa no automático. */
public record ResonatorDialPayload(int containerId, double mhz) implements CustomPacketPayload {
    public static final Type<ResonatorDialPayload> TYPE = new Type<>(WirelessAutomate.id("resonator_dial"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResonatorDialPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ResonatorDialPayload::containerId,
            ByteBufCodecs.DOUBLE, ResonatorDialPayload::mhz,
            ResonatorDialPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
