package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: Voltar ao perfil na máquina remota {@code index}, ou na presa ({@link #ATTACHED}): tira a
 * exceção dela. A posição vai junto: numa remota, o servidor acha a máquina por ela quando o índice deixou de ser
 * dela; na presa, confere que a posição atrás do roteador é a que a tela viu (o roteador não girou depois). O bloco
 * em si não é conferido.
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
