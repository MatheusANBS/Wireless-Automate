package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvItemSearch;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor, com o editor da Tela de Sinal aberto: pede os itens de um Baú ligado à Tela, para a grade de
 * itens da aba Dado, filtrados por {@code query} (palavras e {@code @mod}, vazio = todos). Um pedido por clique no
 * armazenamento da lista e um por busca digitada (150 ms depois da última tecla), nunca por quadro nem por tick.
 */
public record TvStorageContentsRequestPayload(int containerId, GlobalPos pos, String query)
        implements CustomPacketPayload {
    public static final Type<TvStorageContentsRequestPayload> TYPE = new Type<>(
            WirelessAutomate.id("tv_storage_contents_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TvStorageContentsRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TvStorageContentsRequestPayload::containerId,
                    GlobalPos.STREAM_CODEC, TvStorageContentsRequestPayload::pos,
                    ByteBufCodecs.stringUtf8(TvItemSearch.MAX_QUERY), TvStorageContentsRequestPayload::query,
                    TvStorageContentsRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
