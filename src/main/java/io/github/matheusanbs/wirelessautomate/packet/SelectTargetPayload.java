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
 * Cliente → servidor: a tela do roteador passa a editar outro alvo (a máquina presa, o perfil de um
 * grupo ou uma máquina remota). Com o que a tela viu do alvo, para o servidor conferir: a posição da
 * máquina ({@link EditTarget.Kind#MACHINE}) ou o id do bloco do grupo ({@link EditTarget.Kind#GROUP}),
 * porque índices e ids de grupo mudam.
 *
 * @param id índice da máquina ou id do grupo (ignorado na presa)
 */
public record SelectTargetPayload(int containerId, EditTarget.Kind kind, int id, Optional<BlockPos> machinePos,
        Optional<ResourceLocation> block) implements CustomPacketPayload {
    public static final Type<SelectTargetPayload> TYPE = new Type<>(WirelessAutomate.id("select_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectTargetPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(EditTarget.Kind.class), SelectTargetPayload::kind,
            ByteBufCodecs.VAR_INT, SelectTargetPayload::id,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), SelectTargetPayload::machinePos,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), SelectTargetPayload::block,
            SelectTargetPayload::new);

    /** Voltar à máquina presa. */
    public static SelectTargetPayload attached(int containerId) {
        return new SelectTargetPayload(containerId, EditTarget.Kind.ATTACHED, 0, Optional.empty(), Optional.empty());
    }

    /** O perfil do grupo {@code id}, do bloco {@code block}. */
    public static SelectTargetPayload group(int containerId, int id, ResourceLocation block) {
        return new SelectTargetPayload(containerId, EditTarget.Kind.GROUP, id, Optional.empty(), Optional.of(block));
    }

    /** A máquina remota {@code index}, na posição {@code pos}. */
    public static SelectTargetPayload machine(int containerId, int index, BlockPos pos) {
        return new SelectTargetPayload(containerId, EditTarget.Kind.MACHINE, index, Optional.of(pos), Optional.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
