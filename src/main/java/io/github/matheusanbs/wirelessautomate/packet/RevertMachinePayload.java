package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: Voltar ao perfil na máquina remota {@code index} (tira a exceção dela). A
 * posição vai junto para o servidor conferir que o índice ainda é dela.
 */
public record RevertMachinePayload(int containerId, int index, BlockPos pos) implements CustomPacketPayload {
    public static final Type<RevertMachinePayload> TYPE = new Type<>(WirelessAutomate.id("revert_machine"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RevertMachinePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RevertMachinePayload::containerId,
            ByteBufCodecs.VAR_INT, RevertMachinePayload::index,
            BlockPos.STREAM_CODEC, RevertMachinePayload::pos,
            RevertMachinePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
