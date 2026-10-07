package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: "Mover para a rede…" da seleção do Tablet. {@code resource} vazio = todas as abas;
 * {@code network} vazio = sem rede. Até {@link TabletMenu#MAX_MOVE} nós.
 */
public record TabletMoveNodesPayload(int containerId, List<NodeKey> nodes, Optional<ResourceType> resource,
        Optional<UUID> network) implements CustomPacketPayload {
    public static final Type<TabletMoveNodesPayload> TYPE = new Type<>(WirelessAutomate.id("tablet_move_nodes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletMoveNodesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletMoveNodesPayload::containerId,
            NodeKey.STREAM_CODEC.<RegistryFriendlyByteBuf>cast().apply(ByteBufCodecs.list(TabletMenu.MAX_MOVE)),
            TabletMoveNodesPayload::nodes,
            ByteBufCodecs.optional(NeoForgeStreamCodecs.enumCodec(ResourceType.class)), TabletMoveNodesPayload::resource,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), TabletMoveNodesPayload::network,
            TabletMoveNodesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
