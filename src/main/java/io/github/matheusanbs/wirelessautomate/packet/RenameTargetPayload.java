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
 * Cliente → servidor: dá nome a um grupo ({@code GROUP}, {@code id} = id do grupo) ou rótulo a uma máquina
 * remota ({@code MACHINE}, {@code id} = índice) da tela aberta, ou nome à máquina presa ({@code ATTACHED}, sem
 * {@code id} nem posição, e sem exigir o Upgrade de Alcance); vazio tira o nome. Numa máquina, a posição
 * vai junto para o servidor achar a máquina se o índice mudou; num grupo, o bloco dele, para o servidor recusar um
 * grupo que já não é o visto. No máximo {@link RenameRouterPayload#MAX_LENGTH}.
 */
public record RenameTargetPayload(int containerId, EditTarget.Kind kind, int id, Optional<BlockPos> machinePos,
        Optional<ResourceLocation> block, String name) implements CustomPacketPayload {
    public static final Type<RenameTargetPayload> TYPE = new Type<>(WirelessAutomate.id("rename_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RenameTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RenameTargetPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(EditTarget.Kind.class), RenameTargetPayload::kind,
            ByteBufCodecs.VAR_INT, RenameTargetPayload::id,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), RenameTargetPayload::machinePos,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), RenameTargetPayload::block,
            ByteBufCodecs.stringUtf8(64), RenameTargetPayload::name,
            RenameTargetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
