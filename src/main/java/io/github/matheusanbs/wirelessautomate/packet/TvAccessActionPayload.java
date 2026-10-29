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
 * Cliente → servidor: uma ação na lista de acesso da Tela de Sinal aberta, ou o pedido das sugestões do campo. O
 * jogador ou o time vão pelo id da sugestão escolhida ({@code id}) ou, sem ele, pelo nome digitado ({@code text}), que
 * o servidor resolve. Só o dono ou um operador; a resposta é o {@link TvAccessPayload} (ou o
 * {@link NetworkSuggestionsPayload}, nas sugestões).
 */
public record TvAccessActionPayload(int containerId, Action action, Optional<UUID> id, String text)
        implements CustomPacketPayload {
    /** O que a ação faz. */
    public enum Action {
        ADD_PLAYER,
        REMOVE_PLAYER,
        ADD_TEAM,
        REMOVE_TEAM,
        SUGGEST_PLAYER,
        SUGGEST_TEAM
    }

    public static final Type<TvAccessActionPayload> TYPE = new Type<>(WirelessAutomate.id("tv_access_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TvAccessActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TvAccessActionPayload::containerId,
                    NeoForgeStreamCodecs.enumCodec(Action.class), TvAccessActionPayload::action,
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), TvAccessActionPayload::id,
                    ByteBufCodecs.stringUtf8(NetworkSuggestPayload.MAX_TEXT), TvAccessActionPayload::text,
                    TvAccessActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
