package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: dá nome a um grupo ({@code GROUP}, {@code id} = id do grupo) ou rótulo a uma máquina
 * remota ({@code MACHINE}, {@code id} = índice) da tela aberta; vazio tira o nome. Numa máquina, a posição
 * vai junto para o servidor conferir que o índice ainda é dela. No máximo {@link RenameRouterPayload#MAX_LENGTH}.
 */
public record RenameTargetPayload(int containerId, EditTarget.Kind kind, int id, Optional<BlockPos> machinePos,
        String name) implements CustomPacketPayload {
    public static final Type<RenameTargetPayload> TYPE = new Type<>(WirelessAutomate.id("rename_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RenameTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RenameTargetPayload::containerId,
            ByteBufCodecs.idMapper(i -> EditTarget.Kind.values()[i], EditTarget.Kind::ordinal), RenameTargetPayload::kind,
            ByteBufCodecs.VAR_INT, RenameTargetPayload::id,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), RenameTargetPayload::machinePos,
            ByteBufCodecs.stringUtf8(64), RenameTargetPayload::name,
            RenameTargetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
