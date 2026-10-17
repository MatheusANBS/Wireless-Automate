package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Servidor → cliente: um aviso curto do painel do nó, com o que o botão Desfazer faz. A tela que
 * hospeda o painel o mostra por alguns segundos ({@link PanelNotices}).
 *
 * <p>{@code containerId}, {@code resource} e {@code face} só valem para {@code Undo.CLEAR_FACE_NETWORK}:
 * o cliente manda o {@link SetNetworkPayload} sem rede para aquela face, no quadro do alvo de edição. Com
 * {@code Undo.RESTORE_IDLE_NETWORKS}, o cliente manda o {@link ClearIdleNetworksPayload} com {@code undo}.
 */
public record PanelNoticePayload(Component message, Undo undo, int containerId, ResourceType resource, Direction face)
        implements CustomPacketPayload {
    /** O que o Desfazer do aviso faz. */
    public enum Undo {
        /** Sem Desfazer. */
        NONE,
        /** Desfaz o último colar do Tablet. */
        UNDO_PASTE,
        /** Tira a rede só da face citada (a regra D1 pôs a face na rede ativa). */
        CLEAR_FACE_NETWORK,
        /** Devolve a rede às faces sem modo que o último "Tirar a rede das faces sem modo" limpou. */
        RESTORE_IDLE_NETWORKS
    }

    public static final Type<PanelNoticePayload> TYPE = new Type<>(WirelessAutomate.id("panel_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PanelNoticePayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, PanelNoticePayload::message,
            NeoForgeStreamCodecs.enumCodec(Undo.class), PanelNoticePayload::undo,
            ByteBufCodecs.VAR_INT, PanelNoticePayload::containerId,
            NeoForgeStreamCodecs.enumCodec(ResourceType.class), PanelNoticePayload::resource,
            Direction.STREAM_CODEC, PanelNoticePayload::face,
            PanelNoticePayload::new);

    /** Aviso sem Desfazer. */
    public static PanelNoticePayload plain(Component message) {
        return new PanelNoticePayload(message, Undo.NONE, 0, ResourceType.ITEM, Direction.UP);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
