package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: abre ou fecha um roteador na árvore da Lista do Tablet aberto, ou recolhe, expande, mostra
 * todas ou poucas máquinas de um grupo dele ({@code group}, o id do grupo no roteador; ignorado em abrir e fechar).
 * O servidor valida no {@code TabletTree} e refaz só a página desse jogador.
 */
public record TabletTreePayload(int containerId, Action action, NodeKey node, int group) implements CustomPacketPayload {
    public static final Type<TabletTreePayload> TYPE = new Type<>(WirelessAutomate.id("tablet_tree"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletTreePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletTreePayload::containerId,
            NeoForgeStreamCodecs.enumCodec(Action.class), TabletTreePayload::action,
            NodeKey.STREAM_CODEC.<RegistryFriendlyByteBuf>cast(), TabletTreePayload::node,
            ByteBufCodecs.VAR_INT, TabletTreePayload::group,
            TabletTreePayload::new);

    /** O que muda na árvore. */
    public enum Action {
        OPEN,
        CLOSE,
        COLLAPSE_GROUP,
        EXPAND_GROUP,
        SHOW_ALL,
        /** Volta o grupo a mostrar poucas máquinas. */
        SHOW_FEW
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
