package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.net.ByteBufCodecs;
import io.github.matheusanbs.wirelessautomate.net.CustomPacketPayload;
import io.github.matheusanbs.wirelessautomate.net.GameCodecs;
import io.github.matheusanbs.wirelessautomate.net.NeoForgeStreamCodecs;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import java.util.Optional;
import java.util.UUID;

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
            ByteBufCodecs.optional(GameCodecs.UUID), TabletActionPayload::target,
            ByteBufCodecs.optional(GameCodecs.UUID), TabletActionPayload::other,
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
