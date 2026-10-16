package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente → servidor: o modo pedido na tela do Núcleo (automático só com Sintonizador na estrutura). */
public record ResonatorModePayload(int containerId, boolean auto) implements CustomPacketPayload {
    public static final Type<ResonatorModePayload> TYPE = new Type<>(WirelessAutomate.id("resonator_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResonatorModePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ResonatorModePayload::containerId,
            ByteBufCodecs.BOOL, ResonatorModePayload::auto,
            ResonatorModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
