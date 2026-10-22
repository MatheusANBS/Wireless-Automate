package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: a escolha na lista do modo Abrir do Vinculador. O servidor recalcula os candidatos da
 * máquina e só abre se o roteador ainda é um deles.
 */
public record LinkerOpenPickPayload(BlockPos machine, BlockPos router) implements CustomPacketPayload {
    public static final Type<LinkerOpenPickPayload> TYPE = new Type<>(WirelessAutomate.id("linker_open_pick"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerOpenPickPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, LinkerOpenPickPayload::machine,
                    BlockPos.STREAM_CODEC, LinkerOpenPickPayload::router,
                    LinkerOpenPickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
