package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: Atualizar uma máquina trocada ({@link EditTarget.Kind#MACHINE}, pelo índice e a
 * posição) ou todas as trocadas de um grupo ({@link EditTarget.Kind#GROUP}, pelo id e o bloco do grupo).
 * O que a tela viu vai junto para o servidor conferir, como no {@link SelectTargetPayload}.
 *
 * @param id índice da máquina ou id do grupo
 */
public record UpdateMachinePayload(int containerId, EditTarget.Kind kind, int id, Optional<BlockPos> pos,
        Optional<ResourceLocation> block) implements CustomPacketPayload {
    public static final Type<UpdateMachinePayload> TYPE = new Type<>(WirelessAutomate.id("update_machine"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateMachinePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UpdateMachinePayload::containerId,
            NeoForgeStreamCodecs.enumCodec(EditTarget.Kind.class), UpdateMachinePayload::kind,
            ByteBufCodecs.VAR_INT, UpdateMachinePayload::id,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), UpdateMachinePayload::pos,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), UpdateMachinePayload::block,
            UpdateMachinePayload::new);

    /** A máquina remota {@code index}, na posição {@code pos}. */
    public static UpdateMachinePayload machine(int containerId, int index, BlockPos pos) {
        return new UpdateMachinePayload(containerId, EditTarget.Kind.MACHINE, index, Optional.of(pos), Optional.empty());
    }

    /** As trocadas do grupo {@code id}, do bloco {@code block}. */
    public static UpdateMachinePayload group(int containerId, int id, ResourceLocation block) {
        return new UpdateMachinePayload(containerId, EditTarget.Kind.GROUP, id, Optional.empty(), Optional.of(block));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
