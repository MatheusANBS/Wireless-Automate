package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: uma ação na tela do Vinculador aberta ({@code containerId}).
 *
 * @param network a rede ({@link Op#SET_ACTIVE})
 * @param text    o nome da rede nova ({@link Op#CREATE_NETWORK})
 * @param value   o tipo ({@link Op#SET_TYPE}: −1 = Todos, senão o {@code ordinal}) ou o modo
 *                ({@link Op#SET_MODE}: o {@code ordinal})
 */
public record LinkerActionPayload(int containerId, Op op, Optional<UUID> network, String text, int value)
        implements CustomPacketPayload {
    /** Maior nome de rede aceito (o mesmo do {@code /wa network create}). */
    public static final int MAX_NAME_LENGTH = 32;

    public enum Op {
        SET_ACTIVE,
        CREATE_NETWORK,
        SET_TYPE,
        SET_MODE,
        CLEAR_AREA,
        LINK
    }

    public static final Type<LinkerActionPayload> TYPE = new Type<>(WirelessAutomate.id("linker_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LinkerActionPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(Op.class), LinkerActionPayload::op,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), LinkerActionPayload::network,
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
