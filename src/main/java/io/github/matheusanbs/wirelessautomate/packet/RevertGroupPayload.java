package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Cliente → servidor: Voltar ao perfil em todas as máquinas do grupo {@code group} (tira as exceções de uma vez).
 * O bloco vai junto para o servidor conferir que o id ainda é do grupo que a tela viu.
 */
public record RevertGroupPayload(int containerId, int group, ResourceLocation block) implements CustomPacketPayload {
    public static final Type<RevertGroupPayload> TYPE = new Type<>(WirelessAutomate.id("revert_group"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RevertGroupPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RevertGroupPayload::containerId,
            ByteBufCodecs.VAR_INT, RevertGroupPayload::group,
            ResourceLocation.STREAM_CODEC, RevertGroupPayload::block,
            RevertGroupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
