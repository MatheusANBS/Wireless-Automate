package io.github.matheusanbs.wirelessautomate.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Um pacote do mod. Imita o {@code net.minecraft.network.protocol.common.custom.CustomPacketPayload} do 1.21
 * para os payloads do {@code main} mudarem só os imports. No 1.20.1 quem registra e manda é o
 * {@code SimpleChannel} do Forge ({@code ModPayloads}); o {@link Type} só dá o id e o tipo.
 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B, T> codec(
            StreamMemberEncoder<B, T> encoder, StreamDecoder<B, T> decoder) {
        return StreamCodec.ofMember(encoder, decoder);
    }

    /** Id do pacote. */
    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }
}
