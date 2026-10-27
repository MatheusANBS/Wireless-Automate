package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: a resposta a cada {@link TvLayoutPayload} que chegou com o menu aberto, aceito ou recusado,
 * para o editor nunca ficar esperando um salvar que não vai gravar. Com {@link Result#SAVED} a revisão nova da Tela
 * vem pelo update tag do bloco, logo depois.
 */
public record TvLayoutAckPayload(int containerId, Result result, int dropped) implements CustomPacketPayload {
    /** O que o servidor fez com o layout. */
    public enum Result {
        /** Gravou: a revisão da Tela subiu. */
        SAVED,
        /** Valeu, mas o layout que ficou é igual ao que já estava (a revisão não sobe). */
        UNCHANGED,
        /** Só o dono ou um operador edita. */
        DENIED,
        /** Peças, ligações ou bytes acima dos tetos do servidor. */
        TOO_BIG,
        /** Salvando rápido demais: este não foi lido. */
        THROTTLED
    }

    public static final Type<TvLayoutAckPayload> TYPE = new Type<>(WirelessAutomate.id("tv_layout_ack"));
    private static final StreamCodec<ByteBuf, Result> RESULT = ByteBufCodecs
            .idMapper(i -> Result.values()[Math.floorMod(i, Result.values().length)], Result::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, TvLayoutAckPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TvLayoutAckPayload::containerId,
            RESULT, TvLayoutAckPayload::result,
            ByteBufCodecs.VAR_INT, TvLayoutAckPayload::dropped,
            TvLayoutAckPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
