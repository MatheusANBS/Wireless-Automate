package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.ListKind;
import io.github.matheusanbs.wirelessautomate.net.CustomPacketPayload;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.netty.handler.codec.DecoderException;
import java.util.Optional;

/**
 * Cliente → servidor: um clique na tela em lista. {@code key} é o tipo clicado (vazio nas ações que
 * não dependem de tipo), no formato do armazenamento ({@link ListKind}); {@code slot} é o índice do
 * slot do inventário no menu, nas ações de rodinha sobre um slot ({@code -1} nas outras). O servidor
 * confere o menu aberto, a distância, se o tipo existe e se o slot é do menu.
 */
public record StorageActionPayload(int containerId, StorageKind kind, Action action, Optional<Object> key, int slot)
        implements CustomPacketPayload {
    public enum Action {
        /** Baú: uma pilha do tipo para o cursor (ou completa a do cursor). Tanques: enche o recipiente do cursor com o tipo. */
        TAKE_STACK,
        /** Baú, botão direito: meia pilha para o cursor. */
        TAKE_HALF,
        /** Baú, Shift + clique: uma pilha direto para o inventário. */
        TAKE_TO_INVENTORY,
        /** Clique com algo no cursor: guarda tudo o que couber (Tanques: esvazia o recipiente). */
        INSERT_CARRIED,
        /** Botão direito com algo no cursor: guarda um (Tanques: esvazia um recipiente). */
        INSERT_CARRIED_ONE,
        /** Baú, rodinha para baixo sobre um tipo: um item para o inventário. */
        TAKE_ONE_TO_INVENTORY,
        /** Baú, rodinha para cima sobre um tipo: guarda um item desse tipo que esteja no inventário. */
        INSERT_ONE_FROM_INVENTORY,
        /** Baú, rodinha para baixo sobre um slot do inventário: guarda um item dele. */
        INSERT_ONE_FROM_SLOT,
        /** Baú, rodinha para cima sobre um slot do inventário: um item do mesmo tipo do Baú para ele. */
        TAKE_ONE_TO_SLOT,
        /** Baú, Shift + duplo clique com item no cursor: o máximo do tipo que couber no inventário (o cursor fica). */
        TAKE_ALL_TO_INVENTORY,
        /** Abre a tela do filtro de entrada. */
        OPEN_FILTER
    }

    public static final Type<StorageActionPayload> TYPE = new Type<>(WirelessAutomate.id("storage_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageActionPayload> STREAM_CODEC =
            StreamCodec.of(StorageActionPayload::write, StorageActionPayload::read);

    public StorageActionPayload(int containerId, StorageKind kind, Action action) {
        this(containerId, kind, action, Optional.empty());
    }

    public StorageActionPayload(int containerId, StorageKind kind, Action action, Optional<Object> key) {
        this(containerId, kind, action, key, -1);
    }

    private static void write(RegistryFriendlyByteBuf buf, StorageActionPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeEnum(payload.kind);
        buf.writeEnum(payload.action);
        buf.writeBoolean(payload.key.isPresent());
        payload.key.ifPresent(key -> ListKind.of(payload.kind).codec.encode(buf, key));
        buf.writeVarInt(payload.slot + 1);
    }

    private static StorageActionPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        StorageKind kind = buf.readEnum(StorageKind.class);
        if (!kind.hasTypes()) {
            throw new DecoderException("Armazenamento sem lista: " + kind);
        }
        Action action = buf.readEnum(Action.class);
        Optional<Object> key = buf.readBoolean() ? Optional.of(ListKind.of(kind).codec.decode(buf)) : Optional.empty();
        int slot = buf.readVarInt() - 1;
        return new StorageActionPayload(containerId, kind, action, key, slot);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
