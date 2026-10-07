package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/** Cliente → servidor: configura uma face (absoluta) da máquina na tela aberta. */
public record SetFacePayload(int containerId, ResourceType resource, Direction face, PortMode mode, int priority,
        RedstoneMode redstone) implements CustomPacketPayload {
    public static final Type<SetFacePayload> TYPE = new Type<>(WirelessAutomate.id("set_face"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetFacePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetFacePayload::containerId,
            NeoForgeStreamCodecs.enumCodec(ResourceType.class), SetFacePayload::resource,
            Direction.STREAM_CODEC, SetFacePayload::face,
            NeoForgeStreamCodecs.enumCodec(PortMode.class), SetFacePayload::mode,
            ByteBufCodecs.VAR_INT, SetFacePayload::priority,
            NeoForgeStreamCodecs.enumCodec(RedstoneMode.class), SetFacePayload::redstone,
            SetFacePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
