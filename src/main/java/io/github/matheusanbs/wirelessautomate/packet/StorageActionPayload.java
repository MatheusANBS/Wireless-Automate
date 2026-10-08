package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.ListKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.netty.handler.codec.DecoderException;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: um clique na tela em lista. {@code key} é o tipo clicado (vazio nas ações que
 * não dependem de tipo), no formato do armazenamento ({@link ListKind}). O servidor confere o menu
 * aberto, a distância e se o tipo existe.
 */
public record StorageActionPayload(int containerId, StorageKind kind, Action action, Optional<Object> key)
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
        /** Abre a tela do filtro de entrada. */
        OPEN_FILTER
    }

    public static final Type<StorageActionPayload> TYPE = new Type<>(WirelessAutomate.id("storage_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageActionPayload> STREAM_CODEC =
            StreamCodec.of(StorageActionPayload::write, StorageActionPayload::read);

    public StorageActionPayload(int containerId, StorageKind kind, Action action) {
        this(containerId, kind, action, Optional.empty());
    }

    private static void write(RegistryFriendlyByteBuf buf, StorageActionPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeEnum(payload.kind);
        buf.writeEnum(payload.action);
        buf.writeBoolean(payload.key.isPresent());
        payload.key.ifPresent(key -> ListKind.of(payload.kind).codec.encode(buf, key));
    }

    private static StorageActionPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        StorageKind kind = buf.readEnum(StorageKind.class);
        if (!kind.hasTypes()) {
            throw new DecoderException("Armazenamento sem lista: " + kind);
        }
        Action action = buf.readEnum(Action.class);
        Optional<Object> key = buf.readBoolean() ? Optional.of(ListKind.of(kind).codec.decode(buf)) : Optional.empty();
        return new StorageActionPayload(containerId, kind, action, key);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
