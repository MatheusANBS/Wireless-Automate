package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.AccessSuggestions;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Servidor → cliente: as sugestões para o {@code text} que o campo tinha ({@link NetworkSuggestPayload}). O cliente
 * descarta a resposta se o campo já mudou de texto.
 */
public record NetworkSuggestionsPayload(NetworkSuggestPayload.Kind kind, String text, List<Suggestion> suggestions)
        implements CustomPacketPayload {
    /**
     * Uma linha da lista.
     *
     * @param detail jogador: 1 online, 0 já entrou; time: o número de membros
     * @param taken  já está na rede: a linha aparece apagada
     */
    public record Suggestion(UUID id, String name, int detail, boolean taken) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Suggestion> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Suggestion::id,
                ByteBufCodecs.stringUtf8(64), Suggestion::name,
                ByteBufCodecs.VAR_INT, Suggestion::detail,
                ByteBufCodecs.BOOL, Suggestion::taken,
                Suggestion::new);
    }

    public static final Type<NetworkSuggestionsPayload> TYPE = new Type<>(WirelessAutomate.id("network_suggestions"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkSuggestionsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    NeoForgeStreamCodecs.enumCodec(NetworkSuggestPayload.Kind.class), NetworkSuggestionsPayload::kind,
                    ByteBufCodecs.stringUtf8(NetworkSuggestPayload.MAX_TEXT), NetworkSuggestionsPayload::text,
                    Suggestion.STREAM_CODEC.apply(ByteBufCodecs.list(AccessSuggestions.MAX)),
                    NetworkSuggestionsPayload::suggestions,
                    NetworkSuggestionsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
