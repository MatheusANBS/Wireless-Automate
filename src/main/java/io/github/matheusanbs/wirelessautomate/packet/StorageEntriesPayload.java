package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.StorageChestView;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: tipos do Baú da tela aberta que mudaram (quantidade nova; 0 = saiu) e o
 * cabeçalho. Uma sincronização grande vai em vários pacotes de até {@link #MAX_ENTRIES} tipos; só o
 * primeiro leva {@code reset}.
 */
public record StorageEntriesPayload(int containerId, boolean reset, StorageChestView.Header header,
        List<StorageChestView.Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 256;
    public static final Type<StorageEntriesPayload> TYPE = new Type<>(WirelessAutomate.id("storage_entries"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageEntriesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageEntriesPayload::containerId,
            ByteBufCodecs.BOOL, StorageEntriesPayload::reset,
            StorageChestView.Header.STREAM_CODEC, StorageEntriesPayload::header,
            StorageChestView.Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), StorageEntriesPayload::entries,
            StorageEntriesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
