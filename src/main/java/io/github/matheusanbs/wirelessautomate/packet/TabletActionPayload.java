package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: uma ação do Tablet aberto sobre redes ou grupos ({@link TabletMenu.Action}).
 * O significado de alvo, outro, texto e valor depende da ação.
 */
public record TabletActionPayload(int containerId, TabletMenu.Action action, Optional<UUID> target,
        Optional<UUID> other, String text, int value) implements CustomPacketPayload {
    public static final int MAX_TEXT = 64;
    public static final Type<TabletActionPayload> TYPE = new Type<>(WirelessAutomate.id("tablet_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletActionPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(TabletMenu.Action.class), TabletActionPayload::action,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), TabletActionPayload::target,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), TabletActionPayload::other,
            ByteBufCodecs.stringUtf8(MAX_TEXT), TabletActionPayload::text,
            ByteBufCodecs.VAR_INT, TabletActionPayload::value,
            TabletActionPayload::new);

    /** Ação só com alvo. */
    public TabletActionPayload(int containerId, TabletMenu.Action action, UUID target) {
        this(containerId, action, Optional.of(target), Optional.empty(), "", 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
