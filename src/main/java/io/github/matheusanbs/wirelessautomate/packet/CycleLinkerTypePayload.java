package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: Shift + roda do mouse com o Vinculador na mão principal troca as abas que ele
 * vincula pelos atalhos (Todos → um tipo por vez, só os carregados: Químicos com o Mekanism, Source com o Ars Nouveau; uma
 * combinação marcada na tela vai para Todos; {@code direction} +1 ou −1).
 */
public record CycleLinkerTypePayload(int direction) implements CustomPacketPayload {
    public static final Type<CycleLinkerTypePayload> TYPE = new Type<>(WirelessAutomate.id("cycle_linker_type"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CycleLinkerTypePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CycleLinkerTypePayload::direction,
            CycleLinkerTypePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
