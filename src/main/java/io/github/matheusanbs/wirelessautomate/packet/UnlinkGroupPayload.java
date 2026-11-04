package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Cliente → servidor: Desvincular o grupo {@code group} inteiro pela tela (o ✕ da linha do grupo, depois de
 * confirmar). O bloco vai junto para o servidor conferir que o id ainda é desse grupo (um id reaproveitado é
 * recusado).
 */
public record UnlinkGroupPayload(int containerId, int group, ResourceLocation block) implements CustomPacketPayload {
    public static final Type<UnlinkGroupPayload> TYPE = new Type<>(WirelessAutomate.id("unlink_group"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnlinkGroupPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UnlinkGroupPayload::containerId,
            ByteBufCodecs.VAR_INT, UnlinkGroupPayload::group,
            ResourceLocation.STREAM_CODEC, UnlinkGroupPayload::block,
            UnlinkGroupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
