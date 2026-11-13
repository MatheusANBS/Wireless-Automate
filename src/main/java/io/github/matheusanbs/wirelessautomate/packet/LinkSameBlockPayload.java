package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: o Ctrl + clique do Vinculador no modo Vincular numa máquina em {@code pos} (o servidor não sabe
 * do Ctrl). O servidor refaz todas as conferências do clique e vincula as do mesmo bloco que se tocam com ela, no raio
 * ({@code range/SameBlockLinks}).
 */
public record LinkSameBlockPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<LinkSameBlockPayload> TYPE = new Type<>(WirelessAutomate.id("link_same_block"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkSameBlockPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, LinkSameBlockPayload::pos, LinkSameBlockPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
