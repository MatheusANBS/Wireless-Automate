package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: sugestões para o campo de convidado ({@link Kind#PLAYER}) ou de time ({@link Kind#TEAM})
 * da rede {@code network}, para o texto digitado. O servidor só responde a quem gerencia a rede
 * ({@link NetworkSuggestionsPayload}).
 */
public record NetworkSuggestPayload(int containerId, Kind kind, String text, UUID network)
        implements CustomPacketPayload {
    /** Do que o campo procura. */
    public enum Kind {
        PLAYER,
        TEAM
    }

    /** O servidor corta o texto aqui: um nome de jogador tem até 16 caracteres e o de um time, mais. */
    public static final int MAX_TEXT = 32;
    public static final Type<NetworkSuggestPayload> TYPE = new Type<>(WirelessAutomate.id("network_suggest"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkSuggestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, NetworkSuggestPayload::containerId,
                    NeoForgeStreamCodecs.enumCodec(Kind.class), NetworkSuggestPayload::kind,
                    ByteBufCodecs.stringUtf8(MAX_TEXT), NetworkSuggestPayload::text,
                    UUIDUtil.STREAM_CODEC, NetworkSuggestPayload::network,
                    NetworkSuggestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
