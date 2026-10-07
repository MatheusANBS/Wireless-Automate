package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: Shift + roda do mouse com o Configurador na mão principal troca o tipo (aba)
 * que ele cola (Todos → Itens → Fluidos → Energia → Químicos, este só com o Mekanism;
 * {@code direction} +1 ou −1). Irmão do {@link CycleLinkerTypePayload}.
 */
public record CycleConfiguratorTypePayload(int direction) implements CustomPacketPayload {
    public static final Type<CycleConfiguratorTypePayload> TYPE =
            new Type<>(WirelessAutomate.id("cycle_configurator_type"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CycleConfiguratorTypePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CycleConfiguratorTypePayload::direction,
                    CycleConfiguratorTypePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
