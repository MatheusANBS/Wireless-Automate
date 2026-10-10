package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.net.ByteBufCodecs;
import io.github.matheusanbs.wirelessautomate.net.CustomPacketPayload;
import io.github.matheusanbs.wirelessautomate.net.GameCodecs;
import io.github.matheusanbs.wirelessautomate.net.NeoForgeStreamCodecs;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import java.util.Optional;
import java.util.UUID;

/**
 * Cliente → servidor: uma ação na tela do Vinculador aberta ({@code containerId}).
 *
 * @param network a rede ({@link Op#SET_ACTIVE})
 * @param text    o nome da rede nova ({@link Op#CREATE_NETWORK})
 * @param value   a aba ({@link Op#TOGGLE_TAB}: o {@code ordinal} do tipo), o modo
 *                ({@link Op#SET_MODE}: o {@code ordinal}) ou o desvincular ({@link Op#SET_UNLINK}: 1 liga,
 *                0 desliga)
 */
public record LinkerActionPayload(int containerId, Op op, Optional<UUID> network, String text, int value)
        implements CustomPacketPayload {
    /** Maior nome de rede aceito (o mesmo do {@code /wa network create}). */
    public static final int MAX_NAME_LENGTH = 32;

    public enum Op {
        /** Escolhe a rede ativa (e sai do modo desvincular). */
        SET_ACTIVE,
        /** Cria uma rede e a torna ativa (e sai do modo desvincular). */
        CREATE_NETWORK,
        /** "Nenhuma (desvincular)" na escolha da rede. */
        SET_UNLINK,
        /** Marca ou desmarca uma aba. */
        TOGGLE_TAB,
        SET_MODE,
        CLEAR_AREA,
        LINK,
        /** "Todos" na tela: grava a máscara de abas de {@code value} (recusa uma sem tipo disponível). */
        SET_TABS
    }

    public static final Type<LinkerActionPayload> TYPE = new Type<>(WirelessAutomate.id("linker_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LinkerActionPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(Op.class), LinkerActionPayload::op,
            ByteBufCodecs.optional(GameCodecs.UUID), LinkerActionPayload::network,
            ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH * 4), LinkerActionPayload::text,
            ByteBufCodecs.VAR_INT, LinkerActionPayload::value,
            LinkerActionPayload::new);

    public static LinkerActionPayload of(int containerId, Op op) {
        return new LinkerActionPayload(containerId, op, Optional.empty(), "", 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
