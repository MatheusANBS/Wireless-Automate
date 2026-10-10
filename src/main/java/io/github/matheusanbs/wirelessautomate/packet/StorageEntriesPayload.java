package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.ListKind;
import io.github.matheusanbs.wirelessautomate.menu.StorageListView;
import io.github.matheusanbs.wirelessautomate.net.CustomPacketPayload;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servidor → cliente: tipos do armazenamento da tela em lista aberta que mudaram (quantidade nova;
 * 0 = saiu) e o cabeçalho. A chave viaja pelo codec do tipo de armazenamento ({@link ListKind}):
 * item, fluido ou id de químico. Uma sincronização grande vai em vários pacotes de até
 * {@link #MAX_ENTRIES} tipos e cerca de {@link #MAX_BYTES} bytes (o 1.20.1 não divide pacotes e recusa os de
 * mais de 1 MiB; pilhas com NBT grande, como caixas cheias, enchem um pacote rápido); só o primeiro leva
 * {@code reset}.
 */
public record StorageEntriesPayload(int containerId, StorageKind kind, boolean reset, StorageListView.Header header,
        List<StorageListView.Entry<Object>> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 256;
    /** Teto de bytes das entradas de um pacote (metade do 1 MiB do vanilla, com folga para o cabeçalho). */
    public static final int MAX_BYTES = 512 * 1024;
    public static final Type<StorageEntriesPayload> TYPE = new Type<>(WirelessAutomate.id("storage_entries"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageEntriesPayload> STREAM_CODEC =
            StreamCodec.of(StorageEntriesPayload::write, StorageEntriesPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, StorageEntriesPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeEnum(payload.kind);
        buf.writeBoolean(payload.reset);
        StorageListView.Header.STREAM_CODEC.encode(buf, payload.header);
        ListKind<Object> kind = ListKind.of(payload.kind);
        buf.writeVarInt(payload.entries.size());
        for (StorageListView.Entry<Object> entry : payload.entries) {
            kind.codec.encode(buf, entry.key());
            buf.writeVarLong(entry.count());
        }
    }

    private static StorageEntriesPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        StorageKind storage = buf.readEnum(StorageKind.class);
        if (!storage.hasTypes()) {
            throw new DecoderException("Armazenamento sem lista: " + storage);
        }
        boolean reset = buf.readBoolean();
        StorageListView.Header header = StorageListView.Header.STREAM_CODEC.decode(buf);
        ListKind<Object> kind = ListKind.of(storage);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new DecoderException("Tipos demais num pacote: " + size);
        }
        List<StorageListView.Entry<Object>> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new StorageListView.Entry<>(kind.codec.decode(buf), buf.readVarLong()));
        }
        return new StorageEntriesPayload(containerId, storage, reset, header, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
