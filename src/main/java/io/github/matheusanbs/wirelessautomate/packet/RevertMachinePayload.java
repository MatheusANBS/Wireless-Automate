package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: Voltar ao perfil na máquina remota {@code index}, ou na presa ({@link #ATTACHED}): tira a
 * exceção dela. A posição vai junto para o servidor conferir que o índice ainda é dela (na presa, que o bloco
 * atrás do roteador é o que a tela viu).
 */
public record RevertMachinePayload(int containerId, int index, BlockPos pos) implements CustomPacketPayload {
    /** O {@code index} da máquina presa. */
    public static final int ATTACHED = -1;

    public static final Type<RevertMachinePayload> TYPE = new Type<>(WirelessAutomate.id("revert_machine"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RevertMachinePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RevertMachinePayload::containerId,
            ByteBufCodecs.VAR_INT, RevertMachinePayload::index,
            BlockPos.STREAM_CODEC, RevertMachinePayload::pos,
            RevertMachinePayload::new);

    /** Voltar ao perfil na presa, que está em {@code pos}. */
    public static RevertMachinePayload attached(int containerId, BlockPos pos) {
        return new RevertMachinePayload(containerId, ATTACHED, pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
