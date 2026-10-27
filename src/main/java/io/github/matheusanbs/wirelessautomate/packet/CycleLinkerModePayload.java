package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: Shift + roda do mouse com o Vinculador na mão principal troca o modo (Vincular ↔ Abrir;
 * {@code direction} +1 ou −1, os dois levam ao outro modo). Irmão do {@link CycleConfiguratorTypePayload}.
 */
public record CycleLinkerModePayload(int direction) implements CustomPacketPayload {
    public static final Type<CycleLinkerModePayload> TYPE = new Type<>(WirelessAutomate.id("cycle_linker_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CycleLinkerModePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CycleLinkerModePayload::direction,
                    CycleLinkerModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
