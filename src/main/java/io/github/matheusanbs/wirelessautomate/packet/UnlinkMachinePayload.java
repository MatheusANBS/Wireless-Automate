package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: Desvincular a máquina remota {@code index} pela tela. A posição vai junto para
 * o servidor conferir que o índice ainda é dela.
 */
public record UnlinkMachinePayload(int containerId, int index, BlockPos pos) implements CustomPacketPayload {
    public static final Type<UnlinkMachinePayload> TYPE = new Type<>(WirelessAutomate.id("unlink_machine"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnlinkMachinePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UnlinkMachinePayload::containerId,
            ByteBufCodecs.VAR_INT, UnlinkMachinePayload::index,
            BlockPos.STREAM_CODEC, UnlinkMachinePayload::pos,
            UnlinkMachinePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
