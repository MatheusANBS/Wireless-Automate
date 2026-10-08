package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: um clique na lista do Baú. {@code key} é o tipo clicado (vazio nas ações que
 * não dependem de tipo). O servidor confere o menu aberto, a distância e se o tipo existe.
 */
public record StorageActionPayload(int containerId, Action action, ItemStack key) implements CustomPacketPayload {
    public enum Action {
        /** Clique: uma pilha do tipo para o cursor (ou completa a do cursor). */
        TAKE_STACK,
        /** Botão direito: meia pilha para o cursor. */
        TAKE_HALF,
        /** Shift + clique: uma pilha direto para o inventário. */
        TAKE_TO_INVENTORY,
        /** Clique com item no cursor: guarda tudo o que couber. */
        INSERT_CARRIED,
        /** Botão direito com item no cursor: guarda um. */
        INSERT_CARRIED_ONE,
        /** Abre a tela do filtro de entrada. */
        OPEN_FILTER
    }

    public static final Type<StorageActionPayload> TYPE = new Type<>(WirelessAutomate.id("storage_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageActionPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(Action.class), StorageActionPayload::action,
            ItemStack.OPTIONAL_STREAM_CODEC, StorageActionPayload::key,
            StorageActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
