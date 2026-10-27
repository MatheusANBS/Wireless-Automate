package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor → cliente, só com o editor da Tela de Sinal aberto e só depois do pedido
 * ({@link TvStorageContentsRequestPayload}): os itens de um Baú ligado, do mais numeroso ao menos, até
 * {@link #MAX_ITEMS}, e {@code total} é quantos tipos casam com a busca (mais que a lista, se ela foi cortada). {@code available} é falso quando o Baú não está carregado ou não é um Baú do mod (a lista vem
 * vazia).
 */
public record TvStorageContentsPayload(int containerId, GlobalPos pos, boolean available, int total,
        List<Entry> items) implements CustomPacketPayload {
    public static final int MAX_ITEMS = 256;
    public static final Type<TvStorageContentsPayload> TYPE = new Type<>(WirelessAutomate.id("tv_storage_contents"));

    /** Um tipo de item do Baú e quanto há dele. */
    public record Entry(ResourceLocation item, long count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::item,
                ByteBufCodecs.VAR_LONG, Entry::count,
                Entry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TvStorageContentsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TvStorageContentsPayload::containerId,
                    GlobalPos.STREAM_CODEC, TvStorageContentsPayload::pos,
                    ByteBufCodecs.BOOL, TvStorageContentsPayload::available,
                    ByteBufCodecs.VAR_INT, TvStorageContentsPayload::total,
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ITEMS)), TvStorageContentsPayload::items,
                    TvStorageContentsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
